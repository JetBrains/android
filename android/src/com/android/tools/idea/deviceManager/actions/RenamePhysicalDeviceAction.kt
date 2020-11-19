/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.panel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.JComponent


// TODO(qumeric): create 2 packages for actions. Physical and Virtual.

/**
 * Open the Device File Explorer tool window with a selected device
 */
class RenamePhysicalDeviceAction(deviceProvider: PhysicalDeviceProvider) : PhysicalDeviceUiAction(
  deviceProvider,
  "Rename Physical Device...",
  "Change the name of the selected device",
  AllIcons.Actions.Edit
) {
  override fun actionPerformed(e: ActionEvent?) {
    val namedDevice = deviceProvider.device!!
    val dialog = SampleDialogWrapper(namedDevice.name)
    if (dialog.showAndGet()) {
      namedDevice.name = dialog.newName
      // refreshDevices
    }
  }

  override fun isEnabled(): Boolean = true

  private fun createRenameDeviceDialog() {

  }
}


class SampleDialogWrapper(private val oldName: String) : DialogWrapper(true) {
  // TODO(qumeric): pass this data in a better way
  internal var newName: String = oldName

  override fun createCenterPanel(): JComponent? {
    val dialogPanel = panel {
      row {
        textField(::newName)
      }
    }
    val label = JBLabel("testing")
    label.preferredSize = Dimension(100, 100)
    dialogPanel.add(label, BorderLayout.CENTER)
    return dialogPanel
  }

  init {
    init()
    title = "Test DialogWrapper"
  }
}