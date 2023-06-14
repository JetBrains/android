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

import com.android.sdklib.deviceprovisioner.testing.DeviceProvisionerRule
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.isActive
import org.junit.Rule
import org.junit.Test

/** Tests for the DeviceManagerPanel that use FakeAdb. */
class DeviceManagerPanelFakeAdbTest {
  private val deviceProvisionerRule = DeviceProvisionerRule()
  private val androidExecutorsRule = AndroidExecutorsRule()
  private val projectRule = ProjectRule()

  @get:Rule val ruleChain = RuleChain(projectRule, androidExecutorsRule, deviceProvisionerRule)

  @Test
  fun viewDetails() {
    val device1 = deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice()
    val device2 = deviceProvisionerRule.deviceProvisionerPlugin.addNewDevice()

    val deviceManager =
      DeviceManagerPanel(projectRule.project, deviceProvisionerRule.deviceProvisioner)

    deviceProvisionerRule.deviceProvisionerPlugin.addDevice(device1)
    deviceProvisionerRule.deviceProvisionerPlugin.addDevice(device2)

    // Select device 1
    ViewDetailsAction().actionPerformed(actionEvent(dataContext(deviceManager, device1)))

    assertThat(deviceManager.deviceDetailsPanel?.handle).isEqualTo(device1)

    // Select device 2
    ViewDetailsAction().actionPerformed(actionEvent(dataContext(deviceManager, device2)))

    assertThat(deviceManager.deviceDetailsPanel?.handle).isEqualTo(device2)

    // Close panel
    val scope = deviceManager.deviceDetailsPanel?.scope
    deviceManager.deviceDetailsPanel?.closeButton?.doClick()

    assertThat(deviceManager.deviceDetailsPanel).isNull()
    assertThat(scope!!.isActive).isFalse()
  }
}
