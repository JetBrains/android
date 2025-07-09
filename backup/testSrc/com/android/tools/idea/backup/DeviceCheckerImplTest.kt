/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.backup

import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.fakeadbserver.DeviceState
import com.android.sdklib.AndroidApiLevel
import com.android.sdklib.deviceprovisioner.DefaultProvisionerPlugin
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceIcons
import com.android.tools.idea.testing.WaitForIndexRule
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.util.io.await
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class DeviceCheckerImplTest {
  private val projectRule = ProjectRule()
  private val fakeAdbProvider = FakeAdbServerProviderRule()

  @get:Rule val rule = RuleChain(projectRule, WaitForIndexRule(projectRule), fakeAdbProvider)

  private val adbSession by lazy { fakeAdbProvider.adbSession }

  private val deviceProvisioner by lazy { createDeviceProvisioner() }

  @Test
  fun deviceNotFound(): Unit = runBlocking {
    val deviceCheckerImpl = DeviceCheckerImpl(deviceProvisioner)

    val result = deviceCheckerImpl.checkDevice("serial")

    assertThat(result).isEqualTo("Device not found")
  }

  @Test
  fun api30(): Unit = runBlocking {
    val deviceState = connectDevice("serial", apiLevel = 30)
    deviceState.deviceStatus = DeviceState.DeviceStatus.ONLINE
    val deviceCheckerImpl = DeviceCheckerImpl(deviceProvisioner)

    val result = deviceCheckerImpl.checkDevice("serial")

    assertThat(result).isEqualTo("Device API level 30 is not supported")
  }

  @Test
  fun notHandheld(): Unit = runBlocking {
    val deviceState = connectDevice("serial", characteristics = "tv")
    deviceState.deviceStatus = DeviceState.DeviceStatus.ONLINE
    val deviceCheckerImpl = DeviceCheckerImpl(deviceProvisioner)

    val result = deviceCheckerImpl.checkDevice("serial")

    assertThat(result).isEqualTo("Device of type TV is not supported")
  }

  private suspend fun connectDevice(
    serial: String,
    apiLevel: Int = 36,
    characteristics: String = "",
  ): DeviceState {
    return fakeAdbProvider.fakeAdb.fakeAdbServer
      .connectDevice(
        deviceId = serial,
        manufacturer = "Google",
        deviceModel = "Pixel",
        release = "13",
        sdk = AndroidApiLevel(apiLevel),
        cpuAbi = "x86",
        properties = mapOf("ro.build.characteristics" to characteristics),
        hostConnectionType = DeviceState.HostConnectionType.USB,
      )
      .await()
  }

  private fun createDeviceProvisioner(): DeviceProvisioner {
    val coroutineScope =
      adbSession.scope.createChildScope(
        isSupervisor = true,
        parentDisposable = projectRule.disposable,
      )

    return DeviceProvisioner.create(
      coroutineScope,
      adbSession,
      listOf(DefaultProvisionerPlugin(coroutineScope, StudioDefaultDeviceIcons)),
    )
  }
}
