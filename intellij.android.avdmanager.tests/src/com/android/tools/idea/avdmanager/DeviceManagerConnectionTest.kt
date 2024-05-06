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
package com.android.tools.idea.avdmanager

import com.android.sdklib.TempSdkManager
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.devices.DeviceManager.DeviceFilter
import com.android.testutils.NoErrorsOrWarningsLogger
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class DeviceManagerConnectionTest {
  @get:Rule val sdkManager = TempSdkManager("sdk_DeviceManagerConnectionTest")

  @Test
  fun createDevices() {
    val deviceManager =
      DeviceManager.createInstance(sdkManager.sdkHandler, NoErrorsOrWarningsLogger())
    val deviceManagerConnection = DeviceManagerConnection(deviceManager)
    val device =
      Device.Builder(deviceManagerConnection.devices.first())
        .apply { setName("TestDevice") }
        .build()

    deviceManagerConnection.createDevices(listOf(device))
    assertThat(deviceManagerConnection.getDevices(listOf(DeviceFilter.USER)).map { it.displayName })
      .containsExactly("TestDevice")

    deviceManagerConnection.createDevices(listOf(device))
    assertThat(deviceManagerConnection.getDevices(listOf(DeviceFilter.USER)).map { it.displayName })
      .containsExactly("TestDevice", "TestDevice_2")

    deviceManagerConnection.createDevices(listOf(device))
    assertThat(deviceManagerConnection.getDevices(listOf(DeviceFilter.USER)).map { it.displayName })
      .containsExactly("TestDevice", "TestDevice_2", "TestDevice_3")
  }
}
