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

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceError
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.TemplateState
import com.android.sdklib.devices.Abi
import com.android.sdklib.internal.androidTarget.MockPlatformTarget
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.run.DeviceHandleAndroidDevice
import com.android.tools.idea.run.DeviceProvisionerAndroidDevice
import com.android.tools.idea.run.LaunchCompatibility
import com.android.tools.idea.run.LaunchCompatibilityChecker
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import icons.StudioIcons
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.jetbrains.kotlin.utils.addToStdlib.enumSetOf
import org.junit.Test
import java.util.EnumSet

class DeploymentTargetDevicesServiceTest : LightPlatformCodeInsightFixture4TestCase() {
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
  class Fixture(val testScope: TestScope) {
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
        launchCompatibilityCheckerFlow,
      )

    suspend fun sendLaunchCompatibility() {
      launchCompatibilityCheckerFlow.emit(
        LaunchCompatibilityChecker { device ->
          device.canRun(
            AndroidVersion(31),
            MockPlatformTarget(31, 0),
            { EnumSet.noneOf(IDevice.HardwareFeature::class.java) },
            setOf(Abi.ARM64_V8A),
          )
        }
      )
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
  fun deviceHandleActivationActionState() = runTestWithFixture {
    val id = handleId("1")
    val deviceHandle = FakeDeviceHandle(scope, null, id)
    deviceHandle.activationAction.presentation.update {
      it.copy(enabled = false, detail = "Error 12")
    }
    devicesFlow.value = listOf(deviceHandle)
    sendLaunchCompatibility()

    var device = devicesService.loadedDevices.first { it.isNotEmpty() }.first()
    assertThat(device.id).isEqualTo(id)
    assertThat(device.connectionTime).isNull()
    assertThat(device.launchCompatibility)
      .isEqualTo(LaunchCompatibility(LaunchCompatibility.State.ERROR, "Error 12"))

    deviceHandle.activationAction.presentation.update { it.copy(enabled = true, detail = null) }
    testScope.advanceUntilIdle()

    device = devicesService.loadedDevices.first { it.isNotEmpty() }.first()
    assertThat(device.launchCompatibility).isEqualTo(LaunchCompatibility.YES)
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

  private data class TestDeviceError(
    override val severity: DeviceError.Severity,
    override val message: String,
  ) : DeviceError

  @Test
  fun deviceTemplateState() = runTestWithFixture {
    val templateId = templateId("1")
    val template = FakeDeviceTemplate(templateId)
    template.stateFlow.value =
      TemplateState(error = TestDeviceError(DeviceError.Severity.ERROR, "Error"))
    templatesFlow.value = listOf(template)
    sendLaunchCompatibility()

    var devices = devicesService.loadedDevices.first { it.isNotEmpty() }
    var device = devices.first()
    assertThat(device.id).isEqualTo(templateId)
    assertThat(device.launchCompatibility.state).isEqualTo(LaunchCompatibility.State.ERROR)

    // Clear the error; launch compatibility should become OK
    template.stateFlow.value = TemplateState()
    testScope.advanceUntilIdle()

    device = devicesService.loadedDevices.first().first()
    assertThat(device.launchCompatibility.reason).isNull()
    assertThat(device.launchCompatibility.state).isEqualTo(LaunchCompatibility.State.OK)
  }

  @Test
  fun deviceTemplateActivationState() = runTestWithFixture {
    val templateId = templateId("1")
    val template = FakeDeviceTemplate(templateId)
    template.stateFlow.value = TemplateState()
    template.activationAction.presentation.update { it.copy(enabled = false, detail = "Error 42") }
    templatesFlow.value = listOf(template)
    sendLaunchCompatibility()

    var devices = devicesService.loadedDevices.first { it.isNotEmpty() }
    var device = devices.first()
    assertThat(device.id).isEqualTo(templateId)
    assertThat(device.launchCompatibility)
      .isEqualTo(LaunchCompatibility(LaunchCompatibility.State.ERROR, "Error 42"))

    // Clear the error; launch compatibility should become OK
    template.activationAction.presentation.update { it.copy(enabled = true, detail = null) }
    testScope.advanceUntilIdle()

    device = devicesService.loadedDevices.first().first()
    assertThat(device.launchCompatibility).isEqualTo(LaunchCompatibility.YES)

    // Now the user launches the device; it becomes not runnable momentarily
    template.activationAction.presentation.update {
      it.copy(enabled = false, detail = "Already activating")
    }
    testScope.advanceUntilIdle()

    device = devicesService.loadedDevices.first().first()
    assertThat(device.launchCompatibility)
      .isEqualTo(LaunchCompatibility(LaunchCompatibility.State.ERROR, "Already activating"))

    // Once the template state flow updates to reflect that it is activating, it becomes OK again
    template.stateFlow.update { it.copy(isActivating = true) }
    testScope.advanceUntilIdle()

    device = devicesService.loadedDevices.first().first()
    assertThat(device.launchCompatibility).isEqualTo(LaunchCompatibility.YES)
  }

  @Test
  fun deviceHandleLateUpdate() = runTestWithFixture {
    val deviceHandle1 = FakeDeviceHandle(scope, null, handleId("1"))
    val deviceHandle2 = FakeDeviceHandle(scope, null, handleId("2"))
    devicesFlow.value = listOf(deviceHandle1, deviceHandle2)
    sendLaunchCompatibility()

    devicesService.loadedDevices.first { it.isNotEmpty() }

    devicesFlow.value = listOf(deviceHandle2)

    devicesService.loadedDevices.first { it.size == 1 }

    // This should not cause an update
    deviceHandle1.stateFlow.update {
      DeviceState.Disconnected(
        DeviceProperties.buildForTest {
          model = "Updated"
          icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
        }
      )
    }

    // Remove deviceHandle2 to cause an update, which should not contain deviceHandle1
    devicesFlow.value = emptyList()

    testScope.advanceUntilIdle()

    withTimeout(1.seconds) { devicesService.loadedDevices.first { it.isEmpty() } }
  }
}
