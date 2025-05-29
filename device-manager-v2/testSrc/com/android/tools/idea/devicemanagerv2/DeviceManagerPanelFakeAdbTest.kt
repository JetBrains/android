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
package com.android.tools.idea.devicemanagerv2

import com.android.fakeadbserver.DeviceState
import com.android.sdklib.deviceprovisioner.testing.DeviceProvisionerRule
import com.android.testutils.retryUntilPassing
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** Tests for the DeviceManagerPanel that use FakeAdb. */
@RunsInEdt
class DeviceManagerPanelFakeAdbTest {

  private val deviceProvisionerRule = DeviceProvisionerRule()
  private val androidExecutorsRule = AndroidExecutorsRule()
  private val projectRule = ProjectRule()

  @get:Rule
  val ruleChain = RuleChain(EdtRule(), projectRule, androidExecutorsRule, deviceProvisionerRule)

  @Test
  fun viewDetails() {
    val device1 = deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice()
    val device2 = deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice()
    val template = FakeDeviceTemplate("device1")

    val deviceManager =
      DeviceManagerPanel(projectRule.project, deviceProvisionerRule.deviceProvisioner)

    deviceProvisionerRule.deviceProvisionerPlugin.addDevice(device1)
    deviceProvisionerRule.deviceProvisionerPlugin.addDevice(device2)

    // Select device 1
    deviceManager.invokeViewDetails(DeviceRowData.create(device1, emptyList()))

    assertThat(deviceManager.deviceDetailsPanelRow?.handle).isEqualTo(device1)

    // Select device 2
    deviceManager.invokeViewDetails(DeviceRowData.create(device2, emptyList()))

    assertThat(deviceManager.deviceDetailsPanelRow?.handle).isEqualTo(device2)

    // Select template
    deviceManager.invokeViewDetails(DeviceRowData.create(template))

    assertThat(deviceManager.deviceDetailsPanelRow?.template).isEqualTo(template)

    // Close panel
    val scope = deviceManager.deviceDetailsPanel?.scope
    deviceManager.deviceDetailsPanel?.closeButton?.doClick()

    assertThat(deviceManager.deviceDetailsPanel).isNull()
    assertThat(scope!!.isActive).isFalse()
  }

  @Test
  fun goOffline() {
    val device = deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice()
    runBlocking { device.activationAction.activate() }

    val deviceManager =
      DeviceManagerPanel(projectRule.project, deviceProvisionerRule.deviceProvisioner)

    deviceProvisionerRule.deviceProvisionerPlugin.addDevice(device)

    retryUntilPassing(5.seconds) {
      assertThat(deviceManager.deviceTable.values).isNotEmpty()
      assertThat(deviceManager.deviceTable.values[0].status).isEqualTo(DeviceRowData.Status.ONLINE)
    }

    device.fakeAdbDevice?.deviceStatus = DeviceState.DeviceStatus.OFFLINE

    retryUntilPassing(5.seconds) {
      assertThat(deviceManager.deviceTable.values[0].status).isEqualTo(DeviceRowData.Status.OFFLINE)
    }
  }

  private fun DeviceManagerPanel.invokeViewDetails(row: DeviceRowData) {
    ViewDetailsAction().actionPerformed(actionEvent(dataContext(this, deviceRowData = row)))
  }
}
