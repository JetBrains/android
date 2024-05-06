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
package com.android.tools.idea.run

import com.android.ddmlib.AdbInitOptions
import com.android.ddmlib.AndroidDebugBridge
import com.android.sdklib.deviceprovisioner.testing.DeviceProvisionerRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Uninterruptibles
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class DeviceProvisionerAndroidDeviceTest {
  @get:Rule val deviceProvisionerRule = DeviceProvisionerRule()

  lateinit var bridge: AndroidDebugBridge

  @Before
  fun initAdb() {
    AndroidDebugBridge.enableFakeAdbServerMode(deviceProvisionerRule.fakeAdb.fakeAdbServer.port)
    val options =
      AdbInitOptions.builder().setClientSupportEnabled(false).useJdwpProxyService(false).build()
    AndroidDebugBridge.init(options)
    bridge =
      AndroidDebugBridge.createBridge(10, TimeUnit.SECONDS) ?: error("Could not create ADB bridge")
    val startTime = System.currentTimeMillis()
    while (
      (!bridge.isConnected || !bridge.hasInitialDeviceList()) &&
        System.currentTimeMillis() - startTime < TimeUnit.SECONDS.toMillis(10)
    ) {
      Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS)
    }
  }

  @After
  fun terminateAdb() {
    AndroidDebugBridge.terminate()
    AndroidDebugBridge.disableFakeAdbServerMode()
  }

  @Test
  fun bootDefault() {
    val handle = deviceProvisionerRule.deviceProvisionerPlugin.newDevice("abcd")
    deviceProvisionerRule.deviceProvisionerPlugin.addDevice(handle)

    val device = DeviceHandleAndroidDevice(bridge.asDdmlibDeviceLookup(), handle, handle.state)
    assertThat(device.isRunning).isFalse()

    val iDevice = runBlocking { device.bootDefault().await() }

    assertThat(iDevice.serialNumber).isEqualTo("abcd")
    assertThat(device.isRunning).isTrue()
    assertThat(device.serial).isEqualTo("abcd")
    runBlocking { assertThat(device.launchedDevice.await()).isEqualTo(iDevice) }
  }
}
