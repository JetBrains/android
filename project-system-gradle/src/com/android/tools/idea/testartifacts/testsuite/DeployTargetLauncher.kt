/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.testsuite

import com.android.tools.idea.run.TargetSelectionMode
import com.android.tools.idea.run.editor.DeployTargetContext
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project

/**
 * Launches the device or devices currently selected in the device selector dropdown, and returns
 * the serial numbers of the launched devices.
 *
 * Will return an empty list if no devices are launched, i.e. there are no devices configured.
 */
fun launchDevices(project: Project, context: DeployTargetContext = DeployTargetContext()): List<String> {
  context.targetSelectionMode = TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX

  val currentTargetProvider = context.currentDeployTargetProvider
  val deployTarget = if (currentTargetProvider.requiresRuntimePrompt(project)) {
    @Suppress("UnstableApiUsage")
    invokeAndWaitIfNeeded { currentTargetProvider.showPrompt(project) }
  }
  else {
    currentTargetProvider.getDeployTarget(project)
  }

  val devices = deployTarget?.launchDevices(project)?.get() ?: emptyList()
  if (devices.isEmpty()) {
    return emptyList()
  }

  return devices.map { device ->
    device.get().serialNumber
  }
}