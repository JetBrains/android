/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.adtui.common.AdtPrimaryPanel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.DefaultFocusTraversalPolicy
import java.awt.GridLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JSeparator
import javax.swing.UIManager
import javax.swing.event.DocumentEvent

private const val CUSTOM_SET_NAME = "Create a Custom Category"
private const val HORIZONTAL_BORDER_PX = 12
private const val FIELD_VERTICAL_BORDER_PX = 3

class CustomConfigurationSetCreatePalette(val onCreated: (String) -> Unit)
  : AdtPrimaryPanel(BorderLayout()) {
  private var customSetName: String = "Custom"
  private val addButton: JButton

  private var defaultFocusComponent: JComponent? = null

  init {
    add(createHeader(), BorderLayout.NORTH)

    val optionPanel = AdtPrimaryPanel()
    optionPanel.preferredSize = JBDimension(280, 40)
    optionPanel.layout = GridLayout(1, 2)
    optionPanel.border = JBUI.Borders.empty(FIELD_VERTICAL_BORDER_PX, 0, 0, 0)
    add(optionPanel, BorderLayout.CENTER)

    optionPanel.add(JBLabel("Name").apply {
      border = JBUI.Borders.empty(FIELD_VERTICAL_BORDER_PX, HORIZONTAL_BORDER_PX, FIELD_VERTICAL_BORDER_PX, 0)
    })
    optionPanel.add(createNameOptionPanel().apply {
      border = JBUI.Borders.empty(FIELD_VERTICAL_BORDER_PX, 0, FIELD_VERTICAL_BORDER_PX, HORIZONTAL_BORDER_PX)
    })

    addButton = JButton()
    add(createAddButtonPanel(addButton), BorderLayout.SOUTH)

    isFocusCycleRoot = true
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = DefaultFocusTraversalPolicy()

    // initial state
    addButton.isEnabled = customSetName.isNotBlank()
  }

  private fun createHeader(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    val label = JBLabel(CUSTOM_SET_NAME)
    label.border = JBUI.Borders.empty(HORIZONTAL_BORDER_PX)
    panel.add(label, BorderLayout.CENTER)
    panel.add(JSeparator(), BorderLayout.SOUTH)
    return panel
  }

  private fun createNameOptionPanel(): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    // It is okay to have duplicated name
    val editTextField = JBTextField(customSetName)
    editTextField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        customSetName = e.document.getText(0, e.document.length) ?: ""
        addButton.isEnabled = customSetName.isNotBlank()
      }
    })

    editTextField.isFocusable = true
    panel.add(editTextField, BorderLayout.CENTER)

    defaultFocusComponent = editTextField
    return panel
  }

  private fun createAddButtonPanel(button: JButton): JComponent {
    val panel = AdtPrimaryPanel(BorderLayout())
    panel.border = JBUI.Borders.empty(FIELD_VERTICAL_BORDER_PX, 50, FIELD_VERTICAL_BORDER_PX * 3, 50)
    val action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        val setName = customSetName
        if (setName.isBlank()) {
          // Logically this should not happen. This is added in case the UI is not updated immediately.
          UIManager.getLookAndFeel().provideErrorFeedback(addButton)
          return
        }
        onCreated(setName)
      }
    }
    button.action = action

    addButton.text = "Add"

    addButton.isFocusable = true
    panel.add(addButton, BorderLayout.CENTER)

    return panel
  }

  override fun requestFocusInWindow(): Boolean = defaultFocusComponent?.requestFocusInWindow() ?: false
}
