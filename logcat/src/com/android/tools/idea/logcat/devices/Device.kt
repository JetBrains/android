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

import com.android.sdklib.AndroidApiLevel
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceType
import com.google.common.annotations.VisibleForTesting

/** A representation of a device used by [DeviceComboBox]. */
@Suppress("DataClassPrivateConstructor") // Exposed via copy which we use in tests
data class Device @VisibleForTesting constructor(
  val deviceId: String,
  val name: String,
  val serialNumber: String,
  val isOnline: Boolean,
  val release: String,
  val apiLevel: AndroidApiLevel,
  val featureLevel: Int,
  val model: String,
  val type: DeviceType?,
) {
  val sdk: Int
    get() = apiLevel.majorVersion

  val isEmulator: Boolean = serialNumber.startsWith("emulator-")

  companion object {
    fun createPhysical(
      serialNumber: String,
      isOnline: Boolean,
      release: String,
      androidVersion: AndroidVersion,
      manufacturer: String,
      model: String,
      type: DeviceType? = null,
    ): Device {
      val deviceName = if (model.startsWith(manufacturer)) model else "$manufacturer $model"
      return Device(
        deviceId = serialNumber,
        name = deviceName,
        serialNumber,
        isOnline,
        release.normalizeVersion(),
        androidVersion.androidApiLevel,
        androidVersion.featureLevel,
        model,
        type,
      )
    }

    fun createEmulator(
      serialNumber: String,
      isOnline: Boolean,
      release: String,
      androidVersion: AndroidVersion,
      avdName: String,
      avdPath: String,
      type: DeviceType? = null,
    ): Device {
      return Device(
        deviceId = avdPath,
        name = avdName,
        serialNumber,
        isOnline,
        release.normalizeVersion(),
        androidVersion.androidApiLevel,
        androidVersion.featureLevel,
        model = avdName.substringBefore(" API "),
        type,
      )
    }
  }
}

private val VERSION_TRAILING_ZEROS_REGEX = "(\\.0)+$".toRegex()

private fun String.normalizeVersion(): String {
  return VERSION_TRAILING_ZEROS_REGEX.replace(this, "")
}
