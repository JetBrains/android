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

// Associates devices with their LiveEdit status, and implements state transition logic. Status values may be updated directly or by
// providing a state transition function, which computes a new status based on the current status. Status changes can be subscribed to, and
// subscribers will be notified when a device's status changes.
class DeviceStatusManager {
  private val deviceStatuses = ConcurrentHashMap<IDevice, LiveEditStatus>()
  private val listeners = mutableListOf<StatusChangeListener>()

  fun addDevice(device: IDevice, status: LiveEditStatus) {
    deviceStatuses[device] = status
    listeners.forEach { it.accept(mapOf(Pair(device, status))) }
  }

  fun devices(): Set<IDevice> {
    return deviceStatuses.keys
  }

  fun get(device: IDevice): LiveEditStatus? {
    return deviceStatuses[device]
  }

  fun isUnrecoverable(): Boolean {
    return deviceStatuses.values.any { it.unrecoverable() }
  }

  fun isDisabled(): Boolean {
    return deviceStatuses.values.all { it == LiveEditStatus.Disabled || it == LiveEditStatus.NoMultiDeploy }
  }

  fun clear() {
    deviceStatuses.clear()
  }

  fun clear(device: IDevice) {
    deviceStatuses.remove(device)
  }

  fun update(status: LiveEditStatus) {
    update(deviceStatuses.keys) { _, _ -> status }
  }

  fun update(transition: StatusUpdateFunction) {
    update(deviceStatuses.keys, transition)
  }

  fun update(device: IDevice, status: LiveEditStatus) {
    update(setOf(device)) { _, _ -> status }
  }

  fun update(device: IDevice, transition: StatusUpdateFunction) {
    update(setOf(device), transition)
  }

  private fun update(devices: Set<IDevice>, transition: StatusUpdateFunction) {
    val changes = mutableMapOf<IDevice, LiveEditStatus>()
    deviceStatuses.replaceAll { device, oldStatus ->
      if (device !in devices) {
        return@replaceAll oldStatus
      }

      val newStatus = transition(device, oldStatus)
      if (newStatus != oldStatus) {
        changes[device] = newStatus
      }

      return@replaceAll newStatus
    }

    if (changes.isNotEmpty()) {
      listeners.forEach { it.accept(changes) }
    }
  }

  fun addListener(listener: StatusChangeListener) {
    listeners.add(listener)
  }
}