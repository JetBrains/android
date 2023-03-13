/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run.deployment.liveedit

import com.android.ddmlib.IDevice
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

typealias StatusUpdateFunction = (IDevice, LiveEditStatus) -> LiveEditStatus
typealias StatusChangeListener = Consumer<Map<IDevice, LiveEditStatus>>

// We track the state of LiveEdit on a per-project basis. Each AndroidLiveEditDeployMonitor features a LiveEditDevices object which
// track the state of a device for the project it monitor.
class LiveEditDevices {
  private val devices = ConcurrentHashMap<IDevice, LiveEditDevice>()
  private val listeners = mutableListOf<StatusChangeListener>()

  fun addDevice(device: IDevice, status: LiveEditStatus) {
    devices[device] = LiveEditDevice(status)
    listeners.forEach { it.accept(mapOf(Pair(device, status))) }
  }

  fun devices(): Set<IDevice> {
    return devices.keys
  }

  fun get(device: IDevice): LiveEditStatus? {
    return devices[device]!!.status
  }

  fun isUnrecoverable(): Boolean {
    return devices.values.any { it.status.unrecoverable() }
  }

  fun isDisabled(): Boolean {
    return devices.values.all { it.status == LiveEditStatus.Disabled }
  }

  fun clear() {
    devices.clear()
  }

  fun clear(device: IDevice) {
    devices.remove(device)
  }

  fun update(status: LiveEditStatus) {
    update(devices.keys) { _, _-> status }
  }

  fun update(transition: StatusUpdateFunction) {
    update(devices.keys, transition)
  }

  fun update(device: IDevice, status: LiveEditStatus) {
    update(setOf(device)) { _, _ -> status }
  }

  fun update(device: IDevice, transition: StatusUpdateFunction) {
    update(setOf(device), transition)
  }

  private fun update(candidates : Set<IDevice>, transition: StatusUpdateFunction) {
    val changes = mutableMapOf<IDevice, LiveEditStatus>()
    devices.keys.forEach{
      if (it !in candidates) {
        return@forEach
      }

      val device = devices[it]!!
      val oldStatus = device.status
      val newStatus = transition(it, oldStatus)
      if (newStatus != oldStatus) {
        device.status = newStatus
        changes[it] = newStatus
      }
    }

    if (changes.isNotEmpty()) {
      listeners.forEach { it.accept(changes) }
    }
  }

  fun addListener(listener: StatusChangeListener) {
    listeners.add(listener)
  }

  fun handleDeviceLifecycleEvents(device: IDevice, event: DeviceEvent) {
    if (!devices().contains(device)) {
      return
    }
    when (event) {
      DeviceEvent.DEVICE_DISCONNECT -> clear(device)
      DeviceEvent.APPLICATION_CONNECT ->  // If the device was previously in LOADING state, we are now ready to receive live edits.
        update(device) { _, status -> if (status === LiveEditStatus.Loading) LiveEditStatus.UpToDate else status }

      DeviceEvent.APPLICATION_DISCONNECT ->  // If the application disconnects while in the Loading status (if it's the current session that disconnected while loading, we
        // would've gotten an APPLICATION_CONNECT event first before the Client disconnected), that means it is the disconnect from the
        // previous session that has finally arrived in Studio through ADB. Ignore the event in this case.
        update(device) { _, status -> if (status !== LiveEditStatus.Loading) LiveEditStatus.Disabled else status }

      DeviceEvent.DEBUGGER_CONNECT -> update(device, LiveEditStatus.DebuggerAttached)
      DeviceEvent.DEBUGGER_DISCONNECT ->  // Don't return to up-to-date state if another state transition has taken place since.
        update( device) { _, status -> if (status === LiveEditStatus.DebuggerAttached) LiveEditStatus.UpToDate else status }
    }
  }
}