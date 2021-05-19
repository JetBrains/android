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
package com.android.tools.idea.testartifacts.instrumented.testsuite.model

import com.android.sdklib.AndroidVersion


/**
 * Encapsulates an Android device metadata to be displayed in Android test suite view.
 *
 * @param id a device identifier. This can be arbitrary string as long as it is unique to other devices.
 * @param deviceName a name of this device
 * @param deviceType a device type
 * @param version Android version of this device
 * @param additionalInfo an additional device info (such as RAM, processor name) as a key value pair
 */
data class AndroidDevice(val id: String,
                         val deviceName: String,
                         val deviceType: AndroidDeviceType,
                         val version: AndroidVersion,
                         val additionalInfo: MutableMap<String,String> = mutableMapOf())

enum class AndroidDeviceType {
  // A virtual Android device running on a local machine.
  LOCAL_EMULATOR,
  // A physical Android device connected to a local machine.
  LOCAL_PHYSICAL_DEVICE
}

/**
 * Returns a displayable name of a device.
 */
fun AndroidDevice.getName(): String {
  if (deviceName.isNotBlank()) {
    return deviceName
  }

  val manufacturer = additionalInfo.getOrDefault("Manufacturer", "")
  val model = additionalInfo.getOrDefault("Model", "")
  val displayName = "${manufacturer} ${model}".trim()
  if (displayName.isNotBlank()) {
    return displayName
  }

  return id
}