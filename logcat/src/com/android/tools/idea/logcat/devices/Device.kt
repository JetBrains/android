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

/**
 * A representation of a device used by [DeviceComboBox].
 */
@Suppress("DataClassPrivateConstructor") // Exposed via copy which we use in tests
internal data class Device private constructor(
  val deviceId: String,
  val name: String,
  val serialNumber: String,
  val isOnline: Boolean,
  val release: String,
  val sdk: Int,
  val model: String,
) {

  val isEmulator: Boolean = serialNumber.startsWith("emulator-")

  companion object {
    fun createPhysical(
      serialNumber: String,
      isOnline: Boolean,
      release: String,
      sdk: Int,
      manufacturer: String,
      model: String,
    ): Device {
      val deviceName = if (model.startsWith(manufacturer)) model else "$manufacturer $model"
      return Device(deviceId = serialNumber, name = deviceName, serialNumber, isOnline, release.normalizeVersion(), sdk, model)
    }

    fun createEmulator(
      serialNumber: String,
      isOnline: Boolean,
      release: String,
      sdk: Int,
      avdName: String,
    ): Device {
      return Device(
        deviceId = avdName, name = avdName.replace('_', ' '), serialNumber, isOnline, release.normalizeVersion(), sdk, model = "")
    }
  }
}

private val VERSION_TRAILING_ZEROS_REGEX = "(\\.0)+$".toRegex()

private fun String.normalizeVersion(): String {
  return VERSION_TRAILING_ZEROS_REGEX.replace(this, "")
}
