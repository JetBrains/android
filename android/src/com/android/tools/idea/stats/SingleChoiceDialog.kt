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
package com.android.tools.idea.stats

import com.android.tools.idea.Option
import com.android.tools.idea.Survey
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.AWTEvent
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.ButtonGroup
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton

private const val OK_BUTTON_TEXT = "Submit"
private val CENTER_PANEL_BORDER = JBUI.Borders.empty(0, 0, 10, 50)

class SingleChoiceDialog(private val survey: Survey, private val choiceLogger: ChoiceLogger)
  : DialogWrapper(null), ActionListener, ItemListener {
  val buttonGroup = ButtonGroup()
  private val buttons: MutableList<JRadioButton> = mutableListOf()

  val content: JComponent = Box.createVerticalBox().apply {
    border = CENTER_PANEL_BORDER
    add(JBLabel(survey.question))
    add(Box.createVerticalStrut(JBUI.scale(10)))

    survey.optionsList.forEach() {
      add(createButton(it))
    }
  }

  init {
    isAutoAdjustable = true
    setOKButtonText(OK_BUTTON_TEXT)
    setResizable(false)
    title = survey.title
    isOKActionEnabled = false
    isModal = false
    init()
  }

  override fun createCenterPanel(): JComponent = content

  override fun getPreferredFocusedComponent(): JComponent? = buttonGroup.elements.nextElement()

  override fun doOKAction() {
    choiceLogger.log(buttons.indexOfFirst { it.isSelected })
    super.doOKAction()
  }

  override fun doCancelAction(source: AWTEvent?) {
    choiceLogger.cancel()
    super.doCancelAction(source)
  }

  /**
   * Implementation of [ItemListener] to handle keyboard selection.
   */
  override fun itemStateChanged(e: ItemEvent) {
    handleSelection()
  }

  override fun actionPerformed(e: ActionEvent) {
    handleSelection()
  }

  private fun handleSelection() {
    isOKActionEnabled = buttons.any { it.isSelected }
  }

  private fun createButton(option: Option) = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    alignmentX = JPanel.LEFT_ALIGNMENT
    val button = JRadioButton().apply {
      addActionListener(this@SingleChoiceDialog)
      addItemListener(this@SingleChoiceDialog)
      buttonGroup.add(this)
    }
    add(button)
    buttons.add(button)
    add(JBLabel(option.label, option.icon, JBLabel.LEFT).apply {
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          delegateActionToButton(button, e)
        }
      })
    })
  }

  /**
   * Select the button when the user clicks the label
   */
  private fun delegateActionToButton(button: JRadioButton, e: MouseEvent) {
    button.requestFocus()
    button.isSelected = true
    button.actionListeners.forEach { actionListener ->
      actionListener.actionPerformed(ActionEvent(e.source, e.id, button.actionCommand))
    }
  }
}

private val Option.icon: Icon?
  get() {
    val path = iconPath ?: return null
    return IconLoader.getIcon(path, StudioIcons::class.java)
  }

class ShowSatisfactionDialogAction : DumbAwareAction("Show satisfaction dialog") {
  override fun actionPerformed(e: AnActionEvent) {
    if (ApplicationManager.getApplication().isInternal) {
      AndroidStudioUsageTracker.requestUserSentiment()
    }
    else {
      throw RuntimeException("${ShowSatisfactionDialogAction::class.simpleName} can only be called in internal builds")
    }
  }
}
