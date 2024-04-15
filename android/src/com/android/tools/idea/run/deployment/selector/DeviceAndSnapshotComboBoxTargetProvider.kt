/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.run.LaunchCompatibility
import com.android.tools.idea.run.TargetSelectionMode
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.run.editor.DeployTargetConfigurable
import com.android.tools.idea.run.editor.DeployTargetConfigurableContext
import com.android.tools.idea.run.editor.DeployTargetState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper

class DeviceAndSnapshotComboBoxTargetProvider
internal constructor(
  private val devicesSelectedService: (Project) -> DevicesSelectedService = Project::service,
  private val newSelectedDevicesErrorDialog:
    (Project, Iterable<DeploymentTargetDevice>) -> DialogWrapper =
    ::SelectedDevicesErrorDialog,
  private val newDeviceAndSnapshotComboBoxTarget: () -> DeployTarget = {
    DeviceAndSnapshotComboBoxTarget()
  },
) : com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider() {

  override fun getId(): String {
    return TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX.name
  }

  override fun getDisplayName(): String {
    return "Use the device/snapshot drop down"
  }

  override fun createState(): DeployTargetState {
    return DeployTargetState.DEFAULT_STATE
  }

  override fun createConfigurable(
    project: Project,
    parent: Disposable,
    context: DeployTargetConfigurableContext,
  ): DeployTargetConfigurable {
    return DeployTargetConfigurable.DEFAULT_CONFIGURABLE
  }

  override fun requiresRuntimePrompt(project: Project): Boolean {
    val devicesWithError = selectedDevicesWithError(project)
    if (devicesWithError.isEmpty()) {
      return false
    }
    val anyDeviceHasError =
      devicesWithError.any { it.launchCompatibility.state == LaunchCompatibility.State.ERROR }

    // Show dialog if any device has an error or if DO_NOT_SHOW_WARNING_ON_DEPLOYMENT is not true
    // (null or false).
    return anyDeviceHasError ||
      project.getUserData(SelectedDevicesErrorDialog.DO_NOT_SHOW_WARNING_ON_DEPLOYMENT) != true
  }

  private fun selectedDevicesWithError(project: Project): List<DeploymentTargetDevice> {
    return devicesSelectedService(project).getSelectedTargets().mapNotNull {
      it.device.takeIf { it.launchCompatibility.state != LaunchCompatibility.State.OK }
    }
  }

  override fun showPrompt(project: Project): DeployTarget? {
    val devicesWithError = selectedDevicesWithError(project)
    if (devicesWithError.isNotEmpty()) {
      if (!newSelectedDevicesErrorDialog(project, devicesWithError).showAndGet()) {
        return null
      }
    }
    return newDeviceAndSnapshotComboBoxTarget()
  }

  override fun getDeployTarget(project: Project): DeployTarget {
    return newDeviceAndSnapshotComboBoxTarget()
  }

  override fun getNumberOfSelectedDevices(project: Project): Int {
    return devicesSelectedService(project).getSelectedTargets().size
  }

  override fun canDeployToLocalDevice() = true

  companion object {
    @JvmStatic fun getInstance() = DeviceAndSnapshotComboBoxTargetProvider()
  }
}
