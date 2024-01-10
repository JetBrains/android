/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.sdklib.deviceprovisioner.DeviceId
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Sets
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import javax.swing.Action
import javax.swing.GroupLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class SelectMultipleDevicesDialog(
  project: Project,
  devicesSelectedServiceSupplier: (Project) -> DevicesSelectedService = Project::service,
) : DialogWrapper(project) {

  private val devicesSelectedService = devicesSelectedServiceSupplier(project)
  private val model =
    SelectMultipleDevicesDialogTableModel(devicesSelectedService.devicesAndTargets.allDevices)
  internal val table =
    SelectMultipleDevicesDialogTable().apply {
      setModel(this@SelectMultipleDevicesDialog.model)
      selectedTargets = devicesSelectedService.getTargetsSelectedWithDialog()
    }

  init {
    init()
    title = "Select Multiple Devices"
  }

  override fun createCenterPanel(): JComponent {
    val panel = JPanel()
    val layout = GroupLayout(panel)
    val label = JLabel("Available devices")
    val scrollPane = JBScrollPane(table)
    scrollPane.setPreferredSize(JBUI.size(556, 270))
    val horizontalGroup = layout.createParallelGroup().addComponent(label).addComponent(scrollPane)
    val verticalGroup = layout.createSequentialGroup().addComponent(label).addComponent(scrollPane)
    layout.autoCreateGaps = true
    layout.setHorizontalGroup(horizontalGroup)
    layout.setVerticalGroup(verticalGroup)
    panel.setLayout(layout)
    return panel
  }

  override fun doOKAction() {
    super.doOKAction()
    devicesSelectedService.setTargetsSelectedWithDialog(table.selectedTargets)
  }

  @VisibleForTesting
  override fun getOKAction(): Action {
    return super.getOKAction()
  }

  override fun postponeValidation() = false

  override fun getDimensionServiceKey() =
    "com.android.tools.idea.run.deployment.SelectMultipleDevicesDialog"

  override fun getPreferredFocusedComponent() = table

  @VisibleForTesting
  public override fun doValidate(): ValidationInfo? {
    val targets = table.selectedTargets
    val ids = Sets.newHashSetWithExpectedSize<DeviceId>(targets.size)
    val duplicateIds = targets.any { !ids.add(it.deviceId) }
    if (duplicateIds) {
      val message =
        "Some of the selected targets are for the same device. Each target should be for a different device."
      return ValidationInfo(message, null)
    }
    if (
      targets.size > 1 &&
        (targets.any {
          it.device.androidDevice.appPreferredAbi != null && it.device.androidDevice.abis.size > 1
        } || targets.distinctBy { it.device.androidDevice.appPreferredAbi }.size != 1)
    ) {
      val message =
        "Some of the targets have a preferred ABI set. However, the preferred ABI may be ignored when deploying to multiple devices."
      return ValidationInfo(message, null)
    }
    return null
  }
}
