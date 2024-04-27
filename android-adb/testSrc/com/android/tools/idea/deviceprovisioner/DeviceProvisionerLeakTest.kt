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
package com.android.tools.idea.deviceprovisioner

import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.fakeadbserver.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.tools.idea.concurrency.createChildScope
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

class DeviceProvisionerLeakTest {

  private val project1 = ProjectRule()
  private val project2 = ProjectRule()

  private val fakeAdbProvider = FakeAdbServerProviderRule()

  @get:Rule val rules = RuleChain(project1, project2, fakeAdbProvider)

  private fun createDeviceProvisioner(project: Project): DeviceProvisioner {
    val coroutineScope =
      fakeAdbProvider.adbSession.scope.createChildScope(
        isSupervisor = true,
        parentDisposable = project
      )

    return DeviceProvisioner.create(
      coroutineScope,
      fakeAdbProvider.adbSession,
      emptyList(),
      StudioDefaultDeviceIcons
    )
  }

  /**
   * Verifies that a DeviceProvisioner is not held in memory by a ConnectedDevice if the
   * ConnectedDevice outlives the DeviceProvisioner.
   *
   * (This is tested in this module to get access to LeakHunter, but could otherwise be tested in
   * DeviceProvisionerTest.)
   */
  @Test
  fun provisionerNotRetainedByConnectedDevice() {
    val dp1 = createDeviceProvisioner(project1.project)
    val dp2 = createDeviceProvisioner(project2.project)

    val deviceState =
      fakeAdbProvider.fakeAdb.fakeAdbServer
        .connectDevice("1", "Google", "Pixel", "13", "33", DeviceState.HostConnectionType.USB)
        .get()
    deviceState.deviceStatus = DeviceState.DeviceStatus.ONLINE

    val handle1 = runBlocking { dp1.devices.first { it.isNotEmpty() }.first() }
    val handle2 = runBlocking { dp2.devices.first { it.isNotEmpty() }.first() }

    val job2 = handle2.scope.launch { awaitCancellation() }

    ApplicationManager.getApplication().invokeAndWait {
      ProjectManagerEx.getInstanceEx().forceCloseProject(project2.project, save = false)
    }

    runBlocking { withTimeout(5000) { job2.join() } }

    LeakHunter.checkLeak(fakeAdbProvider.adbSession, DeviceProvisioner::class.java) {
      !it.scope.isActive
    }

    assertThat(handle1.state.connectedDevice).isSameAs(handle2.state.connectedDevice)
    LeakHunter.checkLeak(handle1.state.connectedDevice!!, DeviceProvisioner::class.java) {
      !it.scope.isActive
    }
  }
}
