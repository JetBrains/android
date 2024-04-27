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
package com.android.tools.idea.run.deployment.selector

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.run.DeviceHandleAndroidDevice
import com.android.tools.idea.run.DeviceProvisionerAndroidDevice
import com.android.tools.idea.run.LaunchCompatibility
import com.android.tools.idea.run.LaunchCompatibilityChecker
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class DeploymentTargetDevicesServiceTest {
  companion object {
    fun runTestWithFixture(test: suspend Fixture.() -> Unit) {
      runTest {
        val fixture = Fixture(this)
        fixture.test()
        fixture.scope.cancel()
      }
    }
  }

  /** This fixture does not rely on the base fixture since it doesn't need a Project. */
  class Fixture(testScope: TestScope) {
    val scope = testScope.createChildScope()
    val devicesFlow = MutableStateFlow(emptyList<DeviceHandle>())
    val templatesFlow = MutableStateFlow(emptyList<DeviceTemplate>())
    val clock = TestClock()
    val ddmlibDeviceLookupFlow =
      MutableStateFlow(mock<DeviceProvisionerAndroidDevice.DdmlibDeviceLookup>())
    val launchCompatibilityCheckerFlow = MutableSharedFlow<LaunchCompatibilityChecker>(replay = 1)

    internal val devicesService =
      DeploymentTargetDevicesService(
        scope,
        devicesFlow,
        templatesFlow,
        clock,
        ddmlibDeviceLookupFlow,
        launchCompatibilityCheckerFlow
      )

    suspend fun sendLaunchCompatibility() {
      launchCompatibilityCheckerFlow.emit(LaunchCompatibilityChecker { LaunchCompatibility.YES })
    }
  }

  @Test
  fun initialState() = runTestWithFixture {
    assertThat(devicesService.devices.first()).isEqualTo(LoadingState.Loading)

    sendLaunchCompatibility()

    assertThat(devicesService.loadedDevices.first()).isEqualTo(emptyList<DeploymentTargetDevice>())
  }

  @Test
  fun deviceHandle() = runTestWithFixture {
    val id = handleId("1")
    val deviceHandle = FakeDeviceHandle(scope, null, id)
    devicesFlow.value = listOf(deviceHandle)
    sendLaunchCompatibility()

    var devices = devicesService.loadedDevices.first { it.isNotEmpty() }
    val device = devices.first()
    assertThat(device.id).isEqualTo(id)
    assertThat(device.connectionTime).isNull()

    // Updating the device's stateFlow should cause an update to the Device
    deviceHandle.connectToMockDevice()

    devices = devicesService.loadedDevices.first { it.any { it.connectionTime != null } }
    assertThat(devices.first().connectionTime).isEqualTo(clock.time)

    // We should not keep the connection time after disconnecting
    deviceHandle.stateFlow.value = DeviceState.Disconnected(deviceHandle.state.properties)

    devices = devicesService.loadedDevices.first { it.any { it.connectionTime == null } }
    assertThat(devices.first().connectionTime).isNull()

    // After reconnecting, the new connection time should be present
    clock.time += 5.seconds
    deviceHandle.connectToMockDevice()

    devices = devicesService.loadedDevices.first { it.any { it.connectionTime != null } }
    assertThat(devices.first().connectionTime).isEqualTo(clock.time)

    // The device going away should update the device list
    devicesFlow.value = emptyList()

    devicesService.loadedDevices.first { it.isEmpty() }
  }

  @Test
  fun deviceTemplate() = runTestWithFixture {
    val templateId = templateId("1")
    val template = FakeDeviceTemplate(templateId)
    templatesFlow.value = listOf(template)
    sendLaunchCompatibility()

    var devices = devicesService.loadedDevices.first { it.isNotEmpty() }
    var device = devices.first()
    assertThat(device.id).isEqualTo(templateId)

    // Add a device from that template; the template should be hidden.
    val id = handleId("1")
    val handle = FakeDeviceHandle(scope, template, id)
    devicesFlow.value = listOf(handle)

    devices =
      devicesService.loadedDevices.first {
        it.any { it.androidDevice is DeviceHandleAndroidDevice }
      }
    assertThat(devices).hasSize(1)
    device = devices.first()
    assertThat(device.id).isEqualTo(id)
    assertThat(device.templateId).isEqualTo(templateId)
  }
}
