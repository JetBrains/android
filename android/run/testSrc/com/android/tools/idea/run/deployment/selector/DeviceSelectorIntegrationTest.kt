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

import com.android.adblib.ConnectedDevice
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.ddmlib.IDevice
import com.android.sdklib.deviceprovisioner.testing.DeviceProvisionerRule
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.run.DeviceProvisionerAndroidDevice
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.datetime.Clock
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeviceSelectorIntegrationTest {
  @get:Rule val edtRule = EdtRule()
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  val project: Project
    get() = projectRule.project

  @get:Rule val deviceProvisionerRule = DeviceProvisionerRule()

  @Before
  fun setUp() {
    project.replaceService(
      DeploymentTargetDevicesService::class.java,
      DeploymentTargetDevicesService(
        deviceProvisionerRule.deviceProvisioner.scope,
        deviceProvisionerRule.deviceProvisioner.devices,
        deviceProvisionerRule.deviceProvisioner.templates,
        Clock.System,
        MutableStateFlow(
          object : DeviceProvisionerAndroidDevice.DdmlibDeviceLookup {
            override suspend fun findDdmlibDevice(connectedDevice: ConnectedDevice): IDevice {
              return mock<IDevice>()
            }
          }
        ),
        launchCompatibilityCheckerFlow(project),
      ),
      projectRule.project,
    )
  }

  /**
   * Use the DeviceAndSnapshotComboBoxTarget to activate a device, as if we had clicked Run, and
   * verify that the device gets activated.
   */
  @RunsInEdt
  @Test
  fun activation() {
    // Set up the device and wait until it's selected
    RunManager.getInstance(project).createTestConfig()

    val pixel6 = deviceProvisionerRule.deviceProvisionerPlugin.newDevice()
    deviceProvisionerRule.deviceProvisionerPlugin.addDevice(pixel6)

    val comboBox = DeviceAndSnapshotComboBoxAction()
    val event = actionEvent(dataContext(projectRule.project), place = ActionPlaces.MAIN_TOOLBAR)
    comboBox.update(event)
    runBlockingWithTimeout {
      while (event.presentation.text != "Google Pixel 6") {
        delay(100)
        comboBox.update(event)
      }
    }

    // Activate the device
    val deployTarget = DeviceAndSnapshotComboBoxTarget()

    // The device should not be launched by calling getAndroidDevices
    val androidDevices = deployTarget.getAndroidDevices(project)
    assertThrows(IllegalStateException::class.java) { androidDevices[0].launchedDevice }

    // Calling getDevices should launch the device
    val devices = deployTarget.getDevices(project)
    runBlockingWithTimeout { devices.get().forEach { it.await() } }

    assertThat(pixel6.state.isOnline()).isTrue()
  }
}
