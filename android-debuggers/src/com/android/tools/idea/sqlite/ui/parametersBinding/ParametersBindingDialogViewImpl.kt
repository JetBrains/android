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
package com.android.tools.idea.sqlite.ui.parametersBinding

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * @see ParametersBindingDialogView
 */
// TODO(b/143340815) this UI doesn't handle well SQLite statements that contain positional templates instead of named templates.
class ParametersBindingDialogViewImpl(
  project: Project,
  canBeParent: Boolean
) : DialogWrapper(project, canBeParent), ParametersBindingDialogView {

  private val panel = JPanel()
  private val namedParameterResolutionPanels = mutableListOf<NamedParameterResolutionPanel>()

  private val listeners = mutableListOf<ParametersBindingDialogView.Listener>()

  init {
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

    isModal = false
    title = "Parameters Resolution"
    setOKButtonText("Run query")
    setCancelButtonText("Close")

    init()
  }

  override fun showNamedParameters(parametersNames: Set<String>) {
    parametersNames.forEach {
      val namedParameterResolutionPanel = NamedParameterResolutionPanel(it)
      namedParameterResolutionPanels.add(namedParameterResolutionPanel)
      panel.add(namedParameterResolutionPanel.panel)
    }
  }

  override fun addListener(listener: ParametersBindingDialogView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: ParametersBindingDialogView.Listener) {
    listeners.remove(listener)
  }

  override fun createCenterPanel() = panel

  override fun doOKAction() {
    val parametersNameValueMap = namedParameterResolutionPanels.map { it.namedParameter to it.getVariableValue() }.toMap()
    listeners.forEach { it.bindingCompletedInvoked(parametersNameValueMap) }

    super.doOKAction()
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return namedParameterResolutionPanels.firstOrNull()?.namedParameterValueTextField
  }

  private inner class NamedParameterResolutionPanel(val namedParameter: String) {
    private val namedParameterLabel = JLabel(namedParameter)
    val namedParameterValueTextField = JTextField()
    val panel = JPanel(BorderLayout())

    init {
      panel.add(namedParameterLabel, BorderLayout.WEST)
      panel.add(namedParameterValueTextField, BorderLayout.CENTER)
    }

    fun getVariableValue() : String {
      return namedParameterValueTextField.text
    }
  }
}