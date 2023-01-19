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
package com.android.tools.idea.logcat.testing

import com.android.adblib.DeviceState
import com.android.adblib.DeviceState.ONLINE
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.LocalEmulatorProperties
import com.android.tools.idea.logcat.devices.Device

internal class TestDevice(
  val serialNumber: String,
  val state: DeviceState,
  private val release: String,
  private val sdk: Int,
  private val manufacturer: String = "",
  private val model: String = "",
  private val avdName: String = "",
) {

  val device = when {
    serialNumber.isEmulatorSerial() -> Device.createEmulator(serialNumber, state == ONLINE, release, sdk, avdName)
    else -> Device.createPhysical(serialNumber, state == ONLINE, release, sdk, manufacturer, model)
  }
  val deviceProperties = when {
    serialNumber.isEmulatorSerial() -> LocalEmulatorProperties.build {
      manufacturer = this@TestDevice.manufacturer
      model = this@TestDevice.model
      androidRelease = release
      androidVersion = AndroidVersion(sdk)
      avdName = this@TestDevice.avdName
      displayName = avdName
    }

    else -> DeviceProperties.build {
      manufacturer = this@TestDevice.manufacturer
      model = this@TestDevice.model
      androidRelease = release
      androidVersion = AndroidVersion(sdk)
    }
  }


  // Return a new TestDevice with a different serial number
  fun withSerialNumber(serialNumber: String): TestDevice =
    TestDevice(serialNumber, state, release, sdk, manufacturer, model, avdName)

  // Return a new TestDevice with a different state
  fun withState(state: DeviceState): TestDevice =
    TestDevice(device.serialNumber, state, release, sdk, manufacturer, model, avdName)

}

// Emulator can have a blank serial when it's offline
private fun String.isEmulatorSerial() = startsWith("emulator") || isBlank()
