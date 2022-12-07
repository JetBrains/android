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

import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import java.util.function.BiConsumer

enum class DeviceEvent {
  DEVICE_DISCONNECT,
  APPLICATION_CONNECT,
  APPLICATION_DISCONNECT,
  DEBUGGER_CONNECT,
  DEBUGGER_DISCONNECT,
}

typealias DeviceEventListener = BiConsumer<IDevice, DeviceEvent>

class DeviceEventWatcher: IClientChangeListener, IDeviceChangeListener {
  private var applicationId = ""
  private val listeners = mutableListOf<DeviceEventListener>()

  // In order to detect that a client has disconnected, we must check two conditions:
  //  1) Does this device have any clients for our app?
  //  2) Did this device previously have any clients for our app?
  // If both are true, we dispatch an APPLICATION_DISCONNECT event. This set allows us to check condition #2.
  private val devicesWithConnectedClients = mutableSetOf<IDevice>()
  override fun deviceConnected(device: IDevice) {}

  override fun deviceDisconnected(device: IDevice) {
    notifyListeners(device, DeviceEvent.DEVICE_DISCONNECT)
  }

  override fun deviceChanged(device: IDevice, changeMask: Int) {
    if (changeMask and IDevice.CHANGE_CLIENT_LIST == 0) {
      return
    }

    if (device !in devicesWithConnectedClients) {
      return
    }

    if (!device.clients.any { it.clientData.packageName == applicationId && it.isValid }) {
      notifyListeners(device, DeviceEvent.APPLICATION_DISCONNECT)
      devicesWithConnectedClients.remove(device)
    }
  }

  override fun clientChanged(client: Client, changeMask: Int) {
    if (client.clientData.packageName != applicationId) {
      return
    }

    if (changeMask and Client.CHANGE_NAME != 0) {
      notifyListeners(client.device, DeviceEvent.APPLICATION_CONNECT)
      devicesWithConnectedClients.add(client.device)
    }

    if (changeMask and Client.CHANGE_DEBUGGER_STATUS != 0) {
      val event = if (client.isDebuggerAttached) DeviceEvent.DEBUGGER_CONNECT else DeviceEvent.DEBUGGER_DISCONNECT
      notifyListeners(client.device, event)
    }
  }

  fun setApplicationId(applicationId: String) {
    this.applicationId = applicationId
  }

  fun addListener(listener: DeviceEventListener) = listeners.add(listener)

  fun clearListeners() = listeners.clear()

  private fun notifyListeners(device: IDevice, event: DeviceEvent) = listeners.forEach { it.accept(device, event) }
}