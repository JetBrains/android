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

import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.idea.sqlite.controllers.SqliteParameter
import com.android.tools.idea.sqlite.controllers.SqliteParameterValue
import com.android.tools.idea.sqlite.model.SqliteValue
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.Function
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke

/** @see ParametersBindingDialogView */
class ParametersBindingDialogViewImpl(
  sqliteStatementText: String,
  project: Project,
  canBeParent: Boolean
) : DialogWrapper(project, canBeParent), ParametersBindingDialogView {

  val component = JPanel(BorderLayout())
  private val parameterResolutionPanelsContainer = JPanel()
  private val parameterResolutionPanels = mutableListOf<ParameterResolutionPanel>()

  private val listeners = mutableListOf<ParametersBindingDialogView.Listener>()

  private val statementLabel = JBLabel("Statement")
  private val statementTextField =
    ExpandableTextField(
      Function { value: String -> listOf(value) },
      ParametersListUtil.DEFAULT_LINE_JOINER
    )

  init {
    parameterResolutionPanelsContainer.layout =
      BoxLayout(parameterResolutionPanelsContainer, BoxLayout.Y_AXIS)
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.add(createStatementPanel(sqliteStatementText))
    panel.add(parameterResolutionPanelsContainer)
    component.add(panel, BorderLayout.NORTH)

    isModal = false
    title = "Query parameters"
    setOKButtonText("Run")
    setCancelButtonText("Cancel")

    init()
  }

  override fun showNamedParameters(parameters: Set<SqliteParameter>) {
    parameters.forEach {
      val namedParameterResolutionPanel =
        if (it.isCollection) {
          MultiValueParameterResolutionPanel(it)
        } else {
          SingleValueParameterResolutionPanel(it)
        }

      parameterResolutionPanels.add(namedParameterResolutionPanel)
      parameterResolutionPanelsContainer.add(namedParameterResolutionPanel.component)
    }

    val labelSizes =
      parameterResolutionPanels.map { it.parameterNameLabel.preferredSize.width } +
        statementLabel.preferredSize.width
    val maxLabelWidth = labelSizes.maxOrNull() ?: 0
    val finalMaxLabelWidth = boundMaxLabelWidth(maxLabelWidth)
    statementLabel.setFixedWidth(finalMaxLabelWidth)
    parameterResolutionPanels.forEach { it.parameterNameLabel.setFixedWidth(finalMaxLabelWidth) }
  }

  override fun addListener(listener: ParametersBindingDialogView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: ParametersBindingDialogView.Listener) {
    listeners.remove(listener)
  }

  override fun createCenterPanel() = component

  public override fun doOKAction() {
    val parametersNameValueMap =
      parameterResolutionPanels.map { it.sqliteParameter to it.getValue() }.toMap()
    listeners.forEach { it.bindingCompletedInvoked(parametersNameValueMap) }

    super.doOKAction()
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return parameterResolutionPanels.firstOrNull()?.preferredFocusedComponent
  }

  private fun createStatementPanel(sqliteStatementText: String): JPanel {
    statementTextField.isEditable = false
    statementTextField.text = sqliteStatementText
    statementTextField.caretPosition = 0

    val statementPanel = JPanel()
    statementPanel.layout = BoxLayout(statementPanel, BoxLayout.X_AXIS)
    statementPanel.add(statementLabel)
    statementPanel.add(statementTextField)
    return statementPanel
  }

  private fun boundMaxLabelWidth(maxLabelWidth: Int): Int {
    val minWidth = JBUI.scale(50)
    val maxWidth = JBUI.scale(150)

    return when {
      maxLabelWidth in minWidth..maxWidth -> maxLabelWidth
      maxLabelWidth < minWidth -> minWidth
      else -> maxWidth
    }
  }

  /** Interface defining a component used to assign values to a [SqliteParameter]. */
  private interface ParameterResolutionPanel {
    val sqliteParameter: SqliteParameter
    val component: JComponent
    val preferredFocusedComponent: JComponent
    fun getValue(): SqliteParameterValue

    val parameterNameLabel: JLabel

    fun getSqliteValue(inputComponent: InputComponent): SqliteValue {
      return if (inputComponent.isNull) {
        SqliteValue.NullValue
      } else {
        SqliteValue.StringValue(inputComponent.valueTextField.text)
      }
    }
  }

  /**
   * UI component used to assign a list value to a [SqliteParameter]. The list assigned to the
   * parameter must have at least one value, therefore the first input field is not optional. It is
   * possible to add additional input fields by clicking the "add" button, located in the first
   * input field. Each additional field has a button to remove itself.
   */
  private class MultiValueParameterResolutionPanel(override val sqliteParameter: SqliteParameter) :
    ParameterResolutionPanel {
    private val additionalInputComponentsPanel = JPanel()
    private val additionalInputComponents = mutableListOf<InputComponent>()

    // At least one value must be in the list, therefore mainInputComponent is not optional.
    private val mainInputComponent =
      InputComponent(
        sqliteParameter.name,
        InputComponent.Action.Add(true, this::createRemovableInputComponent)
      )

    override val component = JPanel()
    override val preferredFocusedComponent: JTextField = mainInputComponent.valueTextField
    override val parameterNameLabel = mainInputComponent.nameLabel

    init {
      component.layout = BoxLayout(component, BoxLayout.Y_AXIS)
      additionalInputComponentsPanel.layout =
        BoxLayout(additionalInputComponentsPanel, BoxLayout.Y_AXIS)

      component.add(mainInputComponent.component)
      component.add(additionalInputComponentsPanel)
    }

    override fun getValue(): SqliteParameterValue {
      return SqliteParameterValue.CollectionValue(
        listOf(getSqliteValue(mainInputComponent)) +
          additionalInputComponents.map { getSqliteValue(it) }
      )
    }

    /** Adds an InputComponent to the view, that is capable of removing itself. */
    private fun createRemovableInputComponent(parent: InputComponent) {
      val removableInputComponent =
        InputComponent(
          "",
          InputComponent.Action.Remove {
            additionalInputComponents.remove(it)
            additionalInputComponentsPanel.remove(it.component)
            additionalInputComponentsPanel.revalidate()
          }
        )
      removableInputComponent.nameLabel.setFixedWidth(parent.nameLabel.preferredSize.width)

      additionalInputComponents.add(removableInputComponent)
      additionalInputComponentsPanel.add(removableInputComponent.component)
      additionalInputComponentsPanel.revalidate()
    }
  }

  /** UI component used to assign a single value to a [SqliteParameter] */
  private class SingleValueParameterResolutionPanel(override val sqliteParameter: SqliteParameter) :
    ParameterResolutionPanel {
    private val inputComponent =
      InputComponent(sqliteParameter.name, InputComponent.Action.Add(false) {})

    override val component = JPanel(BorderLayout())
    override val preferredFocusedComponent: JTextField = inputComponent.valueTextField
    override val parameterNameLabel = inputComponent.nameLabel

    init {
      component.add(inputComponent.component, BorderLayout.CENTER)
    }

    override fun getValue(): SqliteParameterValue =
      SqliteParameterValue.SingleValue(getSqliteValue(inputComponent))
  }

  private class InputComponent(labelText: String, action: Action) {
    internal val component = JPanel()
    internal val nameLabel = JBLabel(labelText)
    internal val valueTextField = JBTextField(20)

    internal var isNull = false
      private set

    init {
      component.layout = BoxLayout(component, BoxLayout.X_AXIS)
      component.add(nameLabel)
      component.add(valueTextField)

      val setToNullButton = createSetToNullButton()
      component.add(setToNullButton)

      val actionButton = createActionButton(action)
      component.add(actionButton)
    }

    private fun createActionButton(action: Action): Component {
      val actionKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.CTRL_DOWN_MASK)
      val actionShortcutText =
        KeymapUtil.getFirstKeyboardShortcutText(CustomShortcutSet(actionKeyStroke))
      valueTextField.inputMap.put(actionKeyStroke, "myCustomActionButton")
      valueTextField.actionMap.put(
        "myCustomActionButton",
        object : AbstractAction() {
          override fun actionPerformed(e: ActionEvent) {
            action.action(this@InputComponent)
          }
        }
      )

      val actionButton = CommonButton(action.icon)
      actionButton.disabledIcon = IconLoader.getDisabledIcon(action.icon)
      actionButton.toolTipText = "${action.description} ($actionShortcutText)"
      actionButton.addActionListener { action.action(this) }
      actionButton.isEnabled = action.enabled
      return actionButton
    }

    private fun createSetToNullButton(): Component {
      val setToNullKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK)
      val setToNullShortcutText =
        KeymapUtil.getFirstKeyboardShortcutText(CustomShortcutSet(setToNullKeyStroke))

      valueTextField.inputMap.put(setToNullKeyStroke, "setToNull")
      valueTextField.actionMap.put(
        "setToNull",
        object : AbstractAction() {
          override fun actionPerformed(e: ActionEvent) {
            setTextFieldToNull()
          }
        }
      )

      val setToNullButton = CommonButton(AllIcons.RunConfigurations.ShowIgnored)
      setToNullButton.disabledIcon =
        IconLoader.getDisabledIcon(AllIcons.RunConfigurations.ShowIgnored)
      setToNullButton.toolTipText = "Set value to null ($setToNullShortcutText)"
      setToNullButton.addActionListener { setTextFieldToNull() }

      return setToNullButton
    }

    private fun setTextFieldToNull() {
      isNull = !isNull
      valueTextField.text = if (isNull) "<null>" else ""
      valueTextField.isEnabled = !isNull
    }

    internal sealed class Action {
      internal abstract val description: String
      internal abstract val icon: Icon
      internal abstract val enabled: Boolean
      internal abstract val action: (InputComponent) -> Unit

      internal data class Add(
        override val enabled: Boolean,
        override val action: (InputComponent) -> Unit
      ) : Action() {
        override val description = "Add value"
        override val icon = AllIcons.General.Add
      }

      internal data class Remove(override val action: (InputComponent) -> Unit) : Action() {
        override val description = "Remove value"
        override val icon = AllIcons.General.Remove
        override val enabled = true
      }
    }
  }
}

private fun JLabel.setFixedWidth(width: Int) {
  preferredSize = JBUI.size(width, preferredSize.height).size
  maximumSize = preferredSize
  minimumSize = preferredSize
}
