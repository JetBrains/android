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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

/**
 * An action for each device in the drop down without a snapshot sublist. When a user selects a
 * device, SelectDeviceAction will set a target for the device in DeviceAndSnapshotComboBoxAction.
 */
class SelectDeviceAction
internal constructor(
  internal val device: DeploymentTargetDevice,
) : AnAction() {
  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val project = requireNotNull(event.project)
    presentation.setIcon(device.icon)
    presentation.setText(
      device.disambiguatedName(
        project.service<DevicesSelectedService>().devicesAndTargets.allDevices
      ),
      false
    )
  }

  override fun actionPerformed(event: AnActionEvent) {
    event.project!!
      .service<DevicesSelectedService>()
      .setTargetSelectedWithComboBox(device.defaultTarget)
  }
}
