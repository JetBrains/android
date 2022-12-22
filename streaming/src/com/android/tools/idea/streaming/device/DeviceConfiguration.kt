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
package com.android.tools.idea.streaming.device

import com.android.adblib.DevicePropertyNames.RO_BOOT_QEMU_AVD_NAME
import com.android.adblib.DevicePropertyNames.RO_BUILD_CHARACTERISTICS
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_CODENAME
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_SDK
import com.android.adblib.DevicePropertyNames.RO_KERNEL_QEMU_AVD_NAME
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MANUFACTURER
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MODEL
import com.android.sdklib.SdkVersionInfo
import com.intellij.openapi.util.text.StringUtil

/**
 * Characteristics of a mirrored Android device.
 */
class DeviceConfiguration(deviceProperties: Map<String, String>) {

  val apiLevel: Int = deviceProperties[RO_BUILD_VERSION_SDK]?.toInt() ?: SdkVersionInfo.HIGHEST_KNOWN_STABLE_API

  val featureLevel: Int = if (deviceProperties[RO_BUILD_VERSION_CODENAME] == null) apiLevel else apiLevel + 1

  val avdName: String? = deviceProperties[RO_BOOT_QEMU_AVD_NAME] ?: deviceProperties[RO_KERNEL_QEMU_AVD_NAME]

  val deviceModel: String? = deviceProperties[RO_PRODUCT_MODEL]

  val isWatch: Boolean = deviceProperties[RO_BUILD_CHARACTERISTICS]?.contains("watch") ?: false

  val isAutomotive: Boolean = deviceProperties[RO_BUILD_CHARACTERISTICS]?.contains("automotive") ?: false

  val deviceName: String?

  init {
    var name = avdName?.replace('_', ' ')
    if (name == null) {
      name = deviceModel
      if (name != null && !name.startsWith("Pixel")) {
        val manufacturer = deviceProperties[RO_PRODUCT_MANUFACTURER]
        if (!manufacturer.isNullOrBlank() && manufacturer != "unknown") {
          name = "${StringUtil.capitalize(manufacturer)} $name"
        }
      }
      name += " API $apiLevel"
    }
    deviceName = name
  }

  val hasOrientationSensors: Boolean = true // TODO Obtain sensor info from the device.
}