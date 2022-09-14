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

import com.android.tools.idea.serverflags.protos.Option
import com.android.tools.idea.serverflags.protos.Survey
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
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
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton

private const val SUBMIT_BUTTON_TEXT = "Submit"
private const val NEXT_BUTTON_TEXT = "Next"
private val CENTER_PANEL_BORDER = JBUI.Borders.empty(0, 0, 10, 50)

class SingleChoiceDialog(private val survey: Survey, private val choiceLogger: ChoiceLogger, hasFollowup: Boolean)
  : DialogWrapper(null), ActionListener, ItemListener {
  private val buttonGroup = ButtonGroup()
  private val buttons: MutableList<JRadioButton> = mutableListOf()
  val ordering: MutableList<Int> = mutableListOf()

  val content: JComponent = Box.createVerticalBox().apply {
    border = CENTER_PANEL_BORDER
    add(JBLabel(survey.question))
    add(Box.createVerticalStrut(JBUI.scale(10)))

    for (i in 0 until survey.optionsCount) {
      ordering.add(i)
    }

    if (survey.hasRandomOrder() && survey.randomOrder) {
      ordering.shuffle()
    }

    for (i in ordering) {
      add(createButton(survey.optionsList[ordering[i]]))
    }
  }

  init {
    isAutoAdjustable = true
    setOKButtonText(
      if (hasFollowup) {
        NEXT_BUTTON_TEXT
      }
      else {
        SUBMIT_BUTTON_TEXT
      }
    )
    setResizable(false)
    title = survey.title
    isOKActionEnabled = false
    isModal = false

    init()
  }

  override fun createCenterPanel(): JComponent = content

  override fun getPreferredFocusedComponent(): JComponent? = buttonGroup.elements.nextElement()

  override fun doOKAction() {
    choiceLogger.log(survey.name, ordering[buttons.indexOfFirst { it.isSelected }])
    super.doOKAction()
  }

  override fun doCancelAction(source: AWTEvent?) {
    choiceLogger.cancel(survey.name)
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
