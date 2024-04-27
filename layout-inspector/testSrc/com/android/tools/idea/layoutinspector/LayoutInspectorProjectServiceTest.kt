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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.appinspection.ide.ui.RecentProcess
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.runningdevices.withEmbeddedLayoutInspector
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test

class LayoutInspectorProjectServiceTest {

  @get:Rule val projectRule = ProjectRule()

  @get:Rule val disposableRule = DisposableRule()

  @Test
  fun testCreateProcessesModel() {
    val modernProcess = MODERN_DEVICE.createProcess()
    val legacyProcess = LEGACY_DEVICE.createProcess()
    val olderLegacyProcess = OLDER_LEGACY_DEVICE.createProcess()

    val processDiscovery = TestProcessDiscovery()
    val model =
      createProcessesModel(
        projectRule.project,
        disposableRule.disposable,
        processDiscovery,
        MoreExecutors.directExecutor()
      ) {
        null
      }

    // Verify that devices older than M will be included in the processes model:
    processDiscovery.fireConnected(olderLegacyProcess)
    assertThat(model.processes).hasSize(1)
    // An M device as well:
    processDiscovery.fireConnected(legacyProcess)
    assertThat(model.processes).hasSize(2)
    // And newer devices as well:
    processDiscovery.fireConnected(modernProcess)
    assertThat(model.processes).hasSize(3)
  }

  @Test
  fun testIsPreferredProcess() {
    val recentProcess = RecentProcess("serial", "process_name")
    RecentProcess.set(projectRule.project, recentProcess)

    val deviceDescriptor = createDeviceDescriptor("serial")
    val processDescriptor = createProcessDescriptor("process_name", deviceDescriptor)

    val isPreferredProcess = isPreferredProcess(projectRule.project, processDescriptor) { null }
    assertThat(isPreferredProcess).isTrue()
  }

  @Test
  fun testIsNotPreferredProcessWhenDifferentFromForcedProcess() =
    withEmbeddedLayoutInspector(false) {
      val recentProcess = RecentProcess("serial", "process_name")
      RecentProcess.set(projectRule.project, recentProcess)

      val deviceDescriptor = createDeviceDescriptor("serial")
      val processDescriptor = createProcessDescriptor("process_name", deviceDescriptor)

      var deviceModel: DeviceModel? = null

      val processDiscovery = TestProcessDiscovery()
      val processModel =
        createProcessesModel(
          projectRule.project,
          disposableRule.disposable,
          processDiscovery,
          MoreExecutors.directExecutor()
        ) {
          deviceModel
        }
      deviceModel = DeviceModel(disposableRule.disposable, processModel)

      val isPreferredProcess1 =
        isPreferredProcess(projectRule.project, processDescriptor) { deviceModel }
      assertThat(isPreferredProcess1).isTrue()

      deviceModel.forcedDeviceSerialNumber = "different_serial"

      val isPreferredProcess2 =
        isPreferredProcess(projectRule.project, processDescriptor) { deviceModel }
      assertThat(isPreferredProcess2).isTrue()

      withEmbeddedLayoutInspector {
        // If embedded LI is on, we want to connect only to the forced device.
        val isPreferredProcess3 =
          isPreferredProcess(projectRule.project, processDescriptor) { deviceModel }
        assertThat(isPreferredProcess3).isFalse()

        deviceModel.forcedDeviceSerialNumber = "serial"

        val isPreferredProcess4 =
          isPreferredProcess(projectRule.project, processDescriptor) { deviceModel }
        assertThat(isPreferredProcess4).isTrue()
      }
    }
}

private fun createProcessDescriptor(
  processName: String,
  deviceDescriptor: DeviceDescriptor
): ProcessDescriptor {
  return object : ProcessDescriptor {
    override val device = deviceDescriptor
    override val abiCpuArch = "arch"
    override val name = processName
    override val packageName = "package"
    override val isRunning = true
    override val pid = 123
    override val streamId = 123L
  }
}

private fun createDeviceDescriptor(serialNumber: String): DeviceDescriptor {
  return object : DeviceDescriptor {
    override val manufacturer = "manufacturer"
    override val model = "model"
    override val serial = serialNumber
    override val isEmulator = true
    override val apiLevel = 30
    override val version = "version"
    override val codename = null
  }
}
