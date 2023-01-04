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
package com.android.tools.idea.device

import com.android.adblib.DevicePropertyNames
import com.android.sdklib.SdkVersionInfo

/**
 * Characteristics of a mirrored Android device.
 */
class DeviceConfiguration(private val deviceProperties: Map<String, String>) {

  val apiLevel: Int
    get() = deviceProperties[DevicePropertyNames.RO_BUILD_VERSION_SDK]?.toInt() ?: SdkVersionInfo.HIGHEST_KNOWN_STABLE_API

  val avdName: String?
    get() = deviceProperties[DevicePropertyNames.RO_BOOT_QEMU_AVD_NAME] ?: deviceProperties[DevicePropertyNames.RO_KERNEL_QEMU_AVD_NAME]

  val deviceModel: String?
    get() = deviceProperties[DevicePropertyNames.RO_PRODUCT_MODEL]

  val isWatch: Boolean
    get() = deviceProperties[DevicePropertyNames.RO_BUILD_CHARACTERISTICS]?.contains("watch") ?: false

  val isAutomotive: Boolean
    get() = deviceProperties[DevicePropertyNames.RO_BUILD_CHARACTERISTICS]?.contains("automotive") ?: false

  val hasOrientationSensors: Boolean = true // TODO Obtain sensor info from the device.
}