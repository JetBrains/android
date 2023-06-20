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
package com.android.tools.idea.npw.importing

import com.google.common.base.Objects
import com.google.common.base.Strings
import com.intellij.openapi.ui.MessageType
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class PrimaryModuleImportSettings : ModuleImportSettings {
  val moduleNameLabel = JBLabel("Module name")
  val moduleNameField = JTextField(10)
  val primaryModuleState = JBLabel()

  override fun isModuleSelected(): Boolean = true

  override fun setModuleSelected(selected: Boolean) {
    // Do nothing - primary module
  }

  override fun getModuleName(): String = moduleNameField.text

  override fun setModuleName(moduleName: String) {
    if (!Objects.equal(moduleName, moduleNameField.text)) {
      moduleNameField.text = moduleName
    }
  }

  override fun setModuleSourcePath(relativePath: String) {
    // Nothing
  }

  override fun setCanToggleModuleSelection(b: Boolean) {
    // Nothing
  }

  override fun setCanRenameModule(canRenameModule: Boolean) {
    moduleNameField.isEnabled = canRenameModule
  }

  override fun setValidationStatus(statusSeverity: MessageType?, statusDescription: String?) {
    primaryModuleState.icon = statusSeverity?.defaultIcon
    primaryModuleState.text = """<html>${Strings.nullToEmpty(statusDescription)}</html>""" // <html> allows text to wrap
  }

  override fun setVisible(visible: Boolean) {
    primaryModuleState.isVisible = visible
    moduleNameField.isVisible = visible
    moduleNameLabel.isVisible = visible
  }

  override fun addActionListener(actionListener: ActionListener) {
    moduleNameField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        actionListener.actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, "changed"))
      }
    })
  }
}
