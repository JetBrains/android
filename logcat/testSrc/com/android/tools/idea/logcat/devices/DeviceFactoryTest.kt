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

import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbSession
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Tests for [DeviceFactory]
 *
 * This test focuses on the cases where a timeout occurs. The normal flow is already exercised in [DeviceComboBoxDeviceTrackerTest]
 */
class DeviceFactoryTest {
  private val deviceServices = FakeAdbSession().deviceServices

  @Test
  fun createDevice_withTimeouts(): Unit = runBlocking {
    deviceServices.shellNumTimeouts = 3
    val deviceFactory = DeviceFactory(deviceServices)

    val device = deviceFactory.createDevice("device")

    assertThat(device).isEqualTo(Device.createPhysical("device", true, "Unknown", 0, "Unknown", "Unknown"))
  }

  @Test
  fun createDevice_withTimeoutTheSuccess(): Unit = runBlocking {
    deviceServices.shellNumTimeouts = 1
    deviceServices.configureShellV2Command(
      DeviceSelector.fromSerialNumber("device"),
      "getprop ro.build.version.release ; getprop ro.build.version.sdk ; getprop ro.product.manufacturer ; getprop ro.product.model",
      "Release\n12\nManufacturer\nModel")
    val deviceFactory = DeviceFactory(deviceServices)

    val device = deviceFactory.createDevice("device")

    assertThat(device).isEqualTo(Device.createPhysical("device", true, "Release", 12, "Manufacturer", "Model"))
  }
}
