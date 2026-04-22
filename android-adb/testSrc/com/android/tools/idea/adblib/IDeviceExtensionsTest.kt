/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.adblib

import com.android.adblib.connectedDevicesTracker
import com.android.adblib.device
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.ddmlib.AndroidDebugBridge
import com.android.fakeadbserver.DeviceState
import com.android.sdklib.AndroidApiLevel
import com.android.tools.adblib.testutils.FakeAdbServerAdbLibRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class IDeviceExtensionsTest {
  private val projectRule = ProjectRule()
  private val fakeAdbRule = FakeAdbServerAdbLibRule()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(fakeAdbRule)

  @Test
  fun toConnectedDevice_withNullProject_returnsDeviceFromAppSession() = runBlocking {
    // Prepare
    val serialNumber = "device-1"
    fakeAdbRule.connectDevice(serialNumber, "m1", "m2", "10", AndroidApiLevel(36), DeviceState.HostConnectionType.USB)
    val bridge = AndroidDebugBridge.getBridge() ?: error("No bridge")
    val appSession = AdbLibApplicationService.instance.session
    yieldUntil { bridge.devices.any { it.serialNumber == serialNumber } }
    val iDevice = bridge.devices.first { it.serialNumber == serialNumber }

    // Act
    val connectedDevice = iDevice.toConnectedDevice(null)

    // Assert
    assertThat(connectedDevice).isNotNull()
    assertThat(connectedDevice?.serialNumber).isEqualTo(serialNumber)
    assertThat(connectedDevice?.session).isSameAs(appSession)
  }

  @Test
  fun toConnectedDevice_withProject_returnsDeviceFromProjectSession() = runBlocking {
    // Prepare
    val serialNumber = "device-1"
    fakeAdbRule.connectDevice(serialNumber, "m1", "m2", "10", AndroidApiLevel(36), DeviceState.HostConnectionType.USB)
    val bridge = AndroidDebugBridge.getBridge() ?: error("No bridge")
    val project = projectRule.project
    yieldUntil { bridge.devices.any { it.serialNumber == serialNumber } }
    val iDevice = bridge.devices.first { it.serialNumber == serialNumber }

    // Act
    val connectedDevice = iDevice.toConnectedDevice(project)

    // Assert
    assertThat(connectedDevice).isNotNull()
    assertThat(connectedDevice?.serialNumber).isEqualTo(serialNumber)
    assertThat(connectedDevice?.session).isSameAs(AdbLibService.getSession(project))
  }

  @Test
  fun toConnectedDevice_withProject_returnsNull_ifDeviceNotFoundInBothSessions() = runBlocking {
    // Prepare
    val serialNumber = "device-1"
    fakeAdbRule.connectDevice(serialNumber, "m1", "m2", "10", AndroidApiLevel(36), DeviceState.HostConnectionType.USB)
    val bridge = AndroidDebugBridge.getBridge() ?: error("No bridge")
    val project = projectRule.project
    yieldUntil { bridge.devices.any { it.serialNumber == serialNumber } }
    val iDevice = bridge.devices.first { it.serialNumber == serialNumber }

    // Disconnect device from server so it's removed from AdbSessions
    fakeAdbRule.adbServer.disconnectDevice(serialNumber).get()
    yieldUntil { AdbLibApplicationService.instance.session.connectedDevicesTracker.device(serialNumber) == null }
    yieldUntil { AdbLibService.getSession(project).connectedDevicesTracker.device(serialNumber) == null }

    // Act
    val connectedDevice = iDevice.toConnectedDevice(project)

    // Assert
    assertThat(connectedDevice).isNull()
  }
}
