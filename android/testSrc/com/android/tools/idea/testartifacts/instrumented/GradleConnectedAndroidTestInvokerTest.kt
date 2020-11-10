/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented

import com.android.ddmlib.IDevice
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito

/**
 * Unit tests for [GradleConnectedAndroidTestInvoker].
 */
@RunWith(JUnit4::class)
class GradleConnectedAndroidTestInvokerTest {

  @Test
  fun updateDevicesListAndRunSingleDeviceTest() {
    val gradleConnectedTestInvoker = GradleConnectedAndroidTestInvoker(1)
    val mockDevice = Mockito.mock(IDevice::class.java)

    val deviceRun = gradleConnectedTestInvoker.run(mockDevice)
    val devices = gradleConnectedTestInvoker.getDevices()

    assertThat(deviceRun).isTrue()
    assertThat(devices.size).isEqualTo(1)
    assertThat(devices).contains(mockDevice)
  }

  @Test
  fun updateDevicesListAndDoNotRunIfMultipleDevicesSelectedButNotAllRun() {
    val gradleConnectedTestInvoker = GradleConnectedAndroidTestInvoker(2)
    val mockDevice = Mockito.mock(IDevice::class.java)

    val deviceRun = gradleConnectedTestInvoker.run(mockDevice)
    val devices = gradleConnectedTestInvoker.getDevices()

    assertThat(deviceRun).isFalse()
    assertThat(devices.size).isEqualTo(1)
    assertThat(devices).contains(mockDevice)
  }

  @Test
  fun updateDevicesListAndRunIfMultipleDevicesSelectedAndAllRun() {
    val gradleConnectedTestInvoker = GradleConnectedAndroidTestInvoker(2)
    val mockDevice1 = Mockito.mock(IDevice::class.java)
    val mockDevice2 = Mockito.mock(IDevice::class.java)

    val firstRun = gradleConnectedTestInvoker.run(mockDevice1)
    val secondRun = gradleConnectedTestInvoker.run(mockDevice2)
    val devices = gradleConnectedTestInvoker.getDevices()

    assertThat(firstRun).isFalse()
    assertThat(secondRun).isTrue()
    assertThat(devices.size).isEqualTo(2)
    assertThat(devices).contains(mockDevice1)
    assertThat(devices).contains(mockDevice2)
  }
}