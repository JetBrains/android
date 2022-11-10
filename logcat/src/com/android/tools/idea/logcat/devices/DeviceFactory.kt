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
package com.android.tools.idea.logcat.devices

import com.android.adblib.AdbSession
import com.android.adblib.DevicePropertyNames.RO_BOOT_QEMU_AVD_NAME
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_RELEASE
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_SDK
import com.android.adblib.DevicePropertyNames.RO_KERNEL_QEMU_AVD_NAME
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MANUFACTURER
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MODEL
import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsText
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.logcat.util.LOGGER
import com.intellij.openapi.project.Project
import java.time.Duration

private val ADB_TIMEOUT = Duration.ofMillis(1000)

/** Reads from a running device and creates a [Device] */
internal class DeviceFactory(project: Project) {
  private val adbSession: AdbSession = AdbLibService.getSession(project)

  suspend fun createDevice(serialNumber: String): Device {
    if (serialNumber.startsWith("emulator-")) {
      val properties = getProperties(
        serialNumber,
        RO_BUILD_VERSION_RELEASE,
        RO_BUILD_VERSION_SDK,
        RO_BOOT_QEMU_AVD_NAME,
        RO_KERNEL_QEMU_AVD_NAME)
      return Device.createEmulator(
        serialNumber,
        isOnline = true,
        properties.getValue(RO_BUILD_VERSION_RELEASE),
        properties.getValue(RO_BUILD_VERSION_SDK).toIntOrNull() ?: 0,
        getAvdName(serialNumber, properties))
    }
    else {
      val properties = getProperties(
        serialNumber,
        RO_BUILD_VERSION_RELEASE,
        RO_BUILD_VERSION_SDK,
        RO_PRODUCT_MANUFACTURER,
        RO_PRODUCT_MODEL)
      return Device.createPhysical(
        serialNumber,
        isOnline = true,
        properties.getValue(RO_BUILD_VERSION_RELEASE),
        properties.getValue(RO_BUILD_VERSION_SDK).toIntOrNull() ?: 0,
        properties.getValue(RO_PRODUCT_MANUFACTURER),
        properties.getValue(RO_PRODUCT_MODEL))
    }
  }

  @Suppress("SameParameterValue") // The inspection is wrong. It only considers the first arg in the vararg
  private suspend fun getProperties(serialNumber: String, vararg properties: String): Map<String, String> {
    val selector = DeviceSelector.fromSerialNumber(serialNumber)
    val command = properties.joinToString(" ; ") { "getprop $it" }
    //TODO: Check for `stderr` and `exitCode` to report errors
    //TODO: Maybe use `AdbDeviceServices.deviceProperties(selector).allReadonly()` to take advantage of caching
    val lines = adbSession.deviceServices.shellAsText(selector, command, commandTimeout = ADB_TIMEOUT).stdout.split("\n")
    return properties.withIndex().associate { it.value to lines[it.index].trimEnd('\r') }
  }

  private fun getAvdName(serialNumber: String, properties: Map<String, String>): String =
    properties.getValue(RO_BOOT_QEMU_AVD_NAME).ifBlank { properties.getValue(RO_KERNEL_QEMU_AVD_NAME) }.ifBlank {
      LOGGER.warn("Emulator has no avd_name property")
      serialNumber
    }
}