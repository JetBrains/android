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
package com.android.tools.idea.device.monitor.adbimpl

import com.android.ddmlib.IDevice
import com.android.tools.idea.device.monitor.processes.Device
import com.android.tools.idea.device.monitor.processes.DeviceState

class AdbDevice(val device: IDevice) : Device {

  override val name: String
    get() = device.name

  override val serialNumber: String
    get() = device.serialNumber

  override val state: DeviceState
    get() = when (device.state) {
      IDevice.DeviceState.ONLINE -> DeviceState.ONLINE
      IDevice.DeviceState.OFFLINE -> DeviceState.OFFLINE
      IDevice.DeviceState.UNAUTHORIZED -> DeviceState.UNAUTHORIZED
      IDevice.DeviceState.BOOTLOADER -> DeviceState.BOOTLOADER
      IDevice.DeviceState.RECOVERY -> DeviceState.RECOVERY
      IDevice.DeviceState.SIDELOAD -> DeviceState.SIDELOAD
      else -> DeviceState.DISCONNECTED
    }
}
