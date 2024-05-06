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
package com.android.tools.idea.deviceprovisioner

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.fakeadbserver.DeviceState
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState.Connected
import com.android.sdklib.deviceprovisioner.EmptyIcon
import com.android.sdklib.deviceprovisioner.testing.DeviceProvisionerRule
import com.android.sdklib.deviceprovisioner.testing.FakeAdbDeviceProvisionerPlugin
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.SimpleColoredText
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import org.junit.Rule
import org.junit.Test

class DeviceHandleRendererTest {

  @JvmField @Rule val deviceProvisionerRule = DeviceProvisionerRule()

  private fun DeviceProperties.Builder.baseProperties() {
    manufacturer = "Google"
    model = "Pixel 6"
    androidRelease = "12.0"
    androidVersion = AndroidVersion(31)
    icon = EmptyIcon.DEFAULT
  }
  val properties1 =
    DeviceProperties.buildForTest {
      baseProperties()
      disambiguator = "SN1"
    }
  val properties2 =
    DeviceProperties.buildForTest {
      baseProperties()
      disambiguator = "SN2"
    }
  val device1 by lazy {
    deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice(properties = properties1)
  }
  val device2 by lazy {
    deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice(properties = properties2)
  }

  @Test
  fun disconnected() = runBlockingWithTimeout {
    val text = SimpleColoredText()
    DeviceHandleRenderer.renderDevice(text, device1)

    assertThat(text.toString()).isEqualTo("Google Pixel 6 [Disconnected] Android 12.0 (\"S\")")
  }

  @Test
  fun unauthorized() = runBlockingWithTimeout {
    activate(device1)
    device1.fakeAdbDevice?.deviceStatus = DeviceState.DeviceStatus.UNAUTHORIZED
    checkNotNull(device1.state.connectedDevice)
      .deviceInfoFlow
      .takeWhile { it.deviceState != com.android.adblib.DeviceState.UNAUTHORIZED }
      .collect()

    val text = SimpleColoredText()
    DeviceHandleRenderer.renderDevice(text, device1)

    assertThat(text.toString()).isEqualTo("Google Pixel 6 [Unauthorized] Android 12.0 (\"S\")")
  }

  @Test
  fun connected() = runBlockingWithTimeout {
    activate(device1)

    val text = SimpleColoredText()
    DeviceHandleRenderer.renderDevice(text, device1)

    assertThat(text.toString()).isEqualTo("Google Pixel 6 Android 12.0 (\"S\")")
  }

  @Test
  fun duplicate() = runBlockingWithTimeout {
    val allDevices = listOf(device1, device2)
    allDevices.forEach { activate(it) }

    val text = SimpleColoredText()
    DeviceHandleRenderer.renderDevice(text, device1, allDevices)

    assertThat(text.toString()).isEqualTo("Google Pixel 6 [SN1] Android 12.0 (\"S\")")
  }

  private suspend fun activate(deviceHandle: FakeAdbDeviceProvisionerPlugin.FakeDeviceHandle) {
    deviceHandle.activationAction.activate()
    deviceHandle.stateFlow.takeWhile { it !is Connected }.collect()
  }
}
