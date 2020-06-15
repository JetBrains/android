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
package com.android.tools.idea.connection.assistant.actions

import com.android.ddmlib.AdbDevice
import com.android.ddmlib.IDevice
import com.android.tools.usb.UsbDevice

/**
 * Contains all representations of a device, as it is recognized by the operating system's usb system, ddms, and adb.
 */
data class DeviceCrossReference(val usbDevice: List<UsbDevice>, val ddmsDevices: List<IDevice>, val adbDevice: List<AdbDevice>) {
  constructor(device: UsbDevice) : this(listOf(device), emptyList(), emptyList())
  constructor(device: AdbDevice) : this(emptyList(), emptyList(), listOf(device))
}

/**
 * Given information about devices from three different sources (the operating system's USB system, the ADB command line,
 * and ddmslib), this method attempts to figure out which of these are referring to the same device and groups the data
 * up into a list of [DeviceCrossReference] objects, where each instance of [DeviceCrossReference] contains all the information
 * about a single device.
 */
fun crossReference(usbDevices: Collection<UsbDevice>,
                   ddmsDevices: Collection<IDevice>,
                   adbDevices: Collection<AdbDevice>): List<DeviceCrossReference> {
  val snToDdms = ddmsDevices.groupBy { it.serialNumber }
  val snToAdb = adbDevices.groupBy { it.serial }
  val snToUsb = usbDevices.groupBy { it.serialNumber }

  val serialNumbers = (snToAdb.keys + snToDdms.keys + snToUsb.keys).filterNotNull().toSet()

  return (
    serialNumbers.map { DeviceCrossReference(snToUsb[it].orEmpty(), snToDdms[it].orEmpty(), snToAdb[it].orEmpty()) }
    + snToAdb[null].orEmpty().map { DeviceCrossReference(it) }
    + snToUsb[null].orEmpty().map { DeviceCrossReference(it) })
}
