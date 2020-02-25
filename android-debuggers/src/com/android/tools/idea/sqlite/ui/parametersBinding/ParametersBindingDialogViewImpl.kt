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
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * @see ParametersBindingDialogView
 */
class ParametersBindingDialogViewImpl(
  project: Project,
  canBeParent: Boolean
) : DialogWrapper(project, canBeParent), ParametersBindingDialogView {

  val component = JPanel()
  private val namedParameterResolutionPanels = mutableListOf<NamedParameterResolutionPanel>()

  private val listeners = mutableListOf<ParametersBindingDialogView.Listener>()

  init {
    component.layout = BoxLayout(component, BoxLayout.Y_AXIS)

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
      component.add(namedParameterResolutionPanel.panel)
    }
  }

  override fun addListener(listener: ParametersBindingDialogView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: ParametersBindingDialogView.Listener) {
    listeners.remove(listener)
  }

  override fun createCenterPanel() = component

  public override fun doOKAction() {
    val parametersNameValueMap = namedParameterResolutionPanels.map { it.namedParameter to it.getVariableValue() }.toMap()
    listeners.forEach { it.bindingCompletedInvoked(parametersNameValueMap) }

    super.doOKAction()
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return namedParameterResolutionPanels.firstOrNull()?.namedParameterValueTextField
  }

  private inner class NamedParameterResolutionPanel(val namedParameter: String) {
    private val namedParameterLabel = JLabel(namedParameter)
    private val isNullCheckBox = JBCheckBox("Is null")
    val namedParameterValueTextField = JBTextField()
    val panel = JPanel(BorderLayout())

    init {
      panel.add(namedParameterLabel, BorderLayout.WEST)
      panel.add(namedParameterValueTextField, BorderLayout.CENTER)
      panel.add(isNullCheckBox, BorderLayout.EAST)

      namedParameterValueTextField.name = "value-text-field"
      isNullCheckBox.name = "null-check-box"
    }

    fun getVariableValue() : String? {
      return if (isNullCheckBox.isSelected) null else namedParameterValueTextField.text
    }
  }
}