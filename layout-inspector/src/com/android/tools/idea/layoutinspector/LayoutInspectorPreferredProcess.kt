/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.ddmlib.IDevice
import com.android.tools.idea.transport.TransportServiceProxy
import com.android.tools.profiler.proto.Common

fun isDeviceMatch(device: Common.Device, iDevice: IDevice): Boolean {
  return device.manufacturer == TransportServiceProxy.getDeviceManufacturer(iDevice) &&
         device.model == TransportServiceProxy.getDeviceModel(iDevice) &&
         device.serial == iDevice.serialNumber
}

/**
 * Information about an Android process that was recently started from Studio.
 *
 * @param manufacturer the manufacturer of the device or the emulator.
 * @param model the model of the device (or avd name if this is an emulator device).
 * @param serialNumber the serial number of the device or emulator number.
 * @param packageName the package name of the app module.
 */
data class LayoutInspectorPreferredProcess (
  val manufacturer: String,
  val model: String,
  val serialNumber: String,
  val packageName: String?,
  val api: Int
) {
  constructor(device: IDevice, packageName: String?) : this(
    TransportServiceProxy.getDeviceManufacturer(device),
    TransportServiceProxy.getDeviceModel(device),
    device.serialNumber,
    packageName,
    device.version.featureLevel)

  /**
   * Returns true if a device from the transport layer matches the device profile stored.
   */
  fun isDeviceMatch(device: Common.Device): Boolean {
    return device.manufacturer == manufacturer &&
           device.model == model &&
           device.serial == serialNumber
  }
}
