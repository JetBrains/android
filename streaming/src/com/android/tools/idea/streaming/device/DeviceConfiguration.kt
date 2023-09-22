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

import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceType

/**
 * Characteristics of a mirrored Android device.
 */
class DeviceConfiguration(val deviceProperties: DeviceProperties, useTitleAsName: Boolean = false) {
  val apiLevel: Int
    get() = deviceProperties.androidVersion?.apiLevel ?: SdkVersionInfo.HIGHEST_KNOWN_STABLE_API

  val featureLevel: Int
    get() = deviceProperties.androidVersion?.featureLevel ?: SdkVersionInfo.HIGHEST_KNOWN_STABLE_API

  val deviceModel: String?
    get() = deviceProperties.model

  val isWatch: Boolean
    get() = deviceProperties.deviceType == DeviceType.WEAR

  val isAutomotive: Boolean
    get() = deviceProperties.deviceType == DeviceType.AUTOMOTIVE

  val deviceName: String = deviceProperties.composeDeviceName(useTitleAsName)

  val hasOrientationSensors: Boolean = true // TODO Obtain sensor info from the device.
}

internal fun DeviceProperties?.composeDeviceName(useTitleAsName: Boolean = false): String {
  if (this == null) {
    return "Unknown"
  }
  if (useTitleAsName) {
    return title
  }

  val name = StringBuilder()
  val model = model
  if (!model.isNullOrBlank()) {
    if (!model.startsWith("Pixel")) {
      val manufacturer = manufacturer
      if (!manufacturer.isNullOrBlank() && manufacturer != "unknown") {
        name.append(manufacturer).append(' ')
      }
    }
    name.append(model)
  }
  else {
    name.append("unknown")
  }
  val apiLevel = androidVersion?.apiLevel
  if (apiLevel != null) {
    name.append(" API ").append(apiLevel)
  }
  return name.toString()
}
