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
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

private const val SUBMIT_BUTTON_TEXT = "Submit"
private const val NEXT_BUTTON_TEXT = "Next"

/**
 * Dialog presenting a survey for users with a list of options and requesting multiple answers
 */
class MultipleChoiceDialog(private val survey: Survey, private val choiceLogger: ChoiceLogger, hasFollowup: Boolean)
  : DialogWrapper(null), ActionListener, ItemListener {
  private val checkBoxes = mutableListOf<JCheckBox>()

  val content: JComponent = Box.createVerticalBox().apply {
    border = JBUI.Borders.empty(0, 0, 10, 50)
    add(JBLabel(survey.question))
    add(Box.createVerticalStrut(JBUI.scale(10)))

    survey.optionsList.forEach {
      add(createButton(it))
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

  override fun doOKAction() {
    choiceLogger.log(survey.name, IntRange(0, checkBoxes.count() - 1).filter { checkBoxes[it].isSelected })
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
    val limitReached = checkBoxes.count { it.isSelected } == survey.answerCount

    checkBoxes.forEach {
      it.isEnabled = it.isSelected || !limitReached
    }

    isOKActionEnabled = limitReached
  }

  private fun createButton(option: Option) = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    alignmentX = JPanel.LEFT_ALIGNMENT
    val checkBox = JCheckBox().apply {
      addActionListener(this@MultipleChoiceDialog)
      addItemListener(this@MultipleChoiceDialog)
    }
    add(checkBox)
    checkBoxes.add(checkBox)
    add(JBLabel(option.label, option.icon, JBLabel.LEFT).apply {
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          delegateActionToCheckBox(checkBox, e)
        }
      })
    })
  }

  /**
   * Select the button when the user clicks the label
   */
  private fun delegateActionToCheckBox(checkBox: JCheckBox, e: MouseEvent) {
    checkBox.requestFocus()
    checkBox.isSelected = true
    checkBox.actionListeners.forEach { actionListener ->
      actionListener.actionPerformed(ActionEvent(e.source, e.id, checkBox.actionCommand))
    }
  }
}


