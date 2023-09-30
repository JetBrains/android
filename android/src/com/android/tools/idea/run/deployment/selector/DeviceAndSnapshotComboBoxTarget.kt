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

import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.deployment.selector.Target.Companion.filterDevices
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.run.editor.DeployTargetState
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project

internal class DeviceAndSnapshotComboBoxTarget(
  private val deviceAndSnapshotComboBoxAction: () -> DeviceAndSnapshotComboBoxAction =
    DeviceAndSnapshotComboBoxAction.Companion::instance,
  private val getSelectedTargets: (Project, List<Device>) -> Set<Target> = { project, devices ->
    deviceAndSnapshotComboBoxAction().getSelectedTargets(project, devices)
  },
) : DeployTarget {
  override fun hasCustomRunProfileState(executor: Executor) = false

  override fun getRunProfileState(
    executor: Executor,
    environment: ExecutionEnvironment,
    state: DeployTargetState
  ): RunProfileState {
    throw UnsupportedOperationException()
  }

  override fun getDevices(project: Project): DeviceFutures {
    val devices = deviceAndSnapshotComboBoxAction().getDevices(project).orElse(emptyList())
    val selectedTargets = getSelectedTargets(project, devices)
    val selectedDevices = filterDevices(selectedTargets, devices)
    val deviceKeyToTarget = selectedTargets.associateBy { it.deviceKey }
    for (device in selectedDevices) {
      if (!device.isConnected) {
        deviceKeyToTarget[device.key]!!.boot(device, project)
      }
    }
    return DeviceFutures(selectedDevices.map { it.androidDevice })
  }
}
