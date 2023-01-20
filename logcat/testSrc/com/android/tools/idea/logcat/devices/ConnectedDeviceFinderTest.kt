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

import com.android.adblib.AdbSession
import com.android.adblib.DeviceState.OFFLINE
import com.android.adblib.DeviceState.ONLINE
import com.android.adblib.SOCKET_CONNECT_TIMEOUT_MS
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.testing.FakeAdbDeviceProvisionerPlugin
import com.android.tools.idea.logcat.testing.TestDevice
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import org.junit.Rule
import org.junit.Test
import java.time.Duration

/**
 * Tests for [ConnectedDeviceFinder]
 */
class ConnectedDeviceFinderTest {
  private val closeables = CloseablesRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(closeables, disposableRule)

  private val fakeAdb = closeables.register(FakeAdbServerProvider().buildDefault().start())
  private val host = closeables.register(TestingAdbSessionHost())
  private val adbSession =
    closeables.register(AdbSession.create(
      host,
      fakeAdb.createChannelProvider(host),
      Duration.ofMillis(SOCKET_CONNECT_TIMEOUT_MS)
    ))
  private val plugin = FakeAdbDeviceProvisionerPlugin(adbSession.scope, fakeAdb)
  private val deviceProvisioner = DeviceProvisioner.create(adbSession, listOf(plugin))

  @Test
  fun findDevice_deviceIsOnline(): Unit = runBlockingWithTimeout {
    val device1 = TestDevice("device1", ONLINE, "11", 30, "manufacturer", "model")
    val device2 = TestDevice("device2", ONLINE, "11", 30, "manufacturer", "model")
    device1.addDevice(plugin)
    device2.addDevice(plugin)
    val deviceFinder = ConnectedDeviceFinder(deviceProvisioner)

    assertThat(deviceFinder.findDevice(device1.serialNumber)).isEqualTo(device1.device)
    assertThat(deviceFinder.findDevice(device2.serialNumber)).isEqualTo(device2.device)
  }

  @Test
  fun findDevice_deviceNotFound(): Unit = runBlockingWithTimeout {
    val device1 = TestDevice("device1", ONLINE, "11", 30, "manufacturer", "model")
    device1.addDevice(plugin)
    val deviceFinder = ConnectedDeviceFinder(deviceProvisioner)

    assertThat(deviceFinder.findDevice("device2")).isNull()
  }

  @Test
  fun findDevice_deviceIsOffline(): Unit = runBlockingWithTimeout {
    val device1 = TestDevice("device1", OFFLINE, "11", 30, "manufacturer", "model")
    device1.addDevice(plugin)
    val deviceFinder = ConnectedDeviceFinder(deviceProvisioner)

    assertThat(deviceFinder.findDevice(device1.serialNumber)).isNull()
  }
}