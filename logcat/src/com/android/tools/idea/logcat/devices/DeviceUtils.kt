/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.adblib.serialNumber
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.LocalEmulatorProperties
import kotlin.io.path.pathString

/** Convert a [DeviceState] to a [Device] */
internal fun DeviceState.toDevice(): Device? {
  val serialNumber = connectedDevice?.serialNumber ?: return null
  val properties = this.properties

  val release = properties.androidRelease ?: "Unknown"
  val manufacturer = properties.manufacturer ?: "Unknown"
  val model = properties.model ?: "Unknown"
  val androidVersion = properties.androidVersion ?: AndroidVersion(HIGHEST_KNOWN_STABLE_API, 0)
  return when (properties) {
    is LocalEmulatorProperties -> {
      Device.createEmulator(
        serialNumber,
        true,
        release,
        androidVersion,
        properties.displayName,
        properties.avdPath.pathString,
        properties.deviceType,
      )
    }
    else ->
      Device.createPhysical(
        serialNumber,
        true,
        release,
        androidVersion,
        manufacturer,
        model,
        properties.deviceType,
      )
  }
}
