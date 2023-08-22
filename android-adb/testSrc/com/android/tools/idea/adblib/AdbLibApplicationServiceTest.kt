/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.ddmlib.testing.FakeAdbRule
import com.android.tools.idea.adb.FakeAdbServiceRule
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.google.common.truth.Truth
import com.intellij.openapi.components.service
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class AdbLibApplicationServiceTest {
  private val projectRule = ProjectRule()
  private val fakeAdbRule = FakeAdbRule()
  private val fakeAdbServiceRule = FakeAdbServiceRule({ projectRule.project }, fakeAdbRule)
  private val project2Rule = ProjectRule()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(project2Rule).around(fakeAdbRule).around(fakeAdbServiceRule)!!

  @Test
  fun hostServicesShouldWork() {
    // Prepare
    val session = AdbLibApplicationService.instance.session

    // Act
    val version = runBlocking {
      session.hostServices.version()
    }

    // Assert
    Truth.assertThat(version).isGreaterThan(1)
  }

  @Test
  fun getDeviceProvisionerForSessionIsNotNullWhenUsingApplicationService() {
    // Prepare
    val applicationAdbSession = AdbLibApplicationService.instance.session
    val deviceProvisionerService = projectRule.project.service<DeviceProvisionerService>()

    // Act
    val deviceProvisioner = AdbLibApplicationService.getDeviceProvisionerForSession(applicationAdbSession)

    // Assert
    Truth.assertThat(deviceProvisioner).isSameAs(deviceProvisionerService.deviceProvisioner)
  }

  @Test
  fun getDeviceProvisionerForSessionIsNullWhenUsingApplicationService() {
    // Prepare
    val applicationAdbSession = AdbLibApplicationService.instance.session

    // Act
    val deviceProvisioner = AdbLibApplicationService.getDeviceProvisionerForSession(applicationAdbSession)

    // Assert
    Truth.assertThat(deviceProvisioner).isNull()
  }

  @Test
  fun getDeviceProvisionerForSessionIsNotNullWhenUsingProjectService() {
    // Prepare
    val projectService = AdbLibService.getInstance(projectRule.project)

    // Act
    val deviceProvisioner = AdbLibApplicationService.getDeviceProvisionerForSession(projectService.session)

    // Assert
    Truth.assertThat(deviceProvisioner).isSameAs(projectRule.project.service<DeviceProvisionerService>().deviceProvisioner)
  }

  @Test
  fun getDeviceProvisionerForSessionIsReturningCorrectInstance() {
    // Prepare
    val projectAdbSession = AdbLibService.getInstance(project2Rule.project).session

    // Act
    val deviceProvisioner = AdbLibApplicationService.getDeviceProvisionerForSession(projectAdbSession)

    // Assert
    Truth.assertThat(deviceProvisioner).isSameAs(project2Rule.project.service<DeviceProvisionerService>().deviceProvisioner)
  }
}
