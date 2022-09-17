/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.adtui.TabularLayout
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import org.jetbrains.annotations.VisibleForTesting
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.event.DocumentEvent

const val REPLACE_ENTIRE_BODY_TEXT = "Replace entire body"

/**
 * A dialog box that allows adding and editing body rules.
 */
class BodyRuleDialog(
  transformation: RuleData.TransformationRuleData?,
  private val saveAction: (RuleData.TransformationRuleData) -> Unit
) : DialogWrapper(false) {

  @VisibleForTesting
  val findTextArea = JBTextArea(15, 25)

  @VisibleForTesting
  val replaceTextArea = JBTextArea(15, 25)

  @VisibleForTesting
  val regexCheckBox = JBCheckBox()

  @VisibleForTesting
  val replaceEntireBodyCheckBox = JBCheckBox(REPLACE_ENTIRE_BODY_TEXT).apply {
    val changeAction: (e: ItemEvent) -> Unit = {
      findTextArea.isEnabled = !isSelected
      findTextArea.isOpaque = !isSelected
      if (isSelected) {
        regexCheckBox.isSelected = false
        findTextArea.text = ""
      }
      regexCheckBox.isEnabled = !isSelected
      updateIsOKActionEnabled(this)
    }
    addItemListener(changeAction)
    changeAction(ItemEvent(this, 0, null, ItemEvent.ITEM_STATE_CHANGED))
  }

  init {
    title = "Body Rule"
    transformation?.let { applySavedBody(it) }
    updateIsOKActionEnabled(replaceEntireBodyCheckBox)
    init()
  }

  private fun applySavedBody(bodyRule: RuleData.TransformationRuleData) {
    when (bodyRule) {
      is RuleData.BodyModifiedRuleData -> {
        findTextArea.text = bodyRule.targetText
        replaceTextArea.text = bodyRule.newText
        regexCheckBox.isSelected = bodyRule.isRegex
        replaceEntireBodyCheckBox.isSelected = false
      }
      is RuleData.BodyReplacedRuleData -> {
        findTextArea.text = ""
        replaceTextArea.text = bodyRule.body
        regexCheckBox.isSelected = false
        replaceEntireBodyCheckBox.isSelected = true
      }
    }
  }

  override fun createCenterPanel() = JPanel(TabularLayout("*,5px,Fit,5px,*", "20px,*,Fit")).apply {
    findTextArea.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        updateIsOKActionEnabled(replaceEntireBodyCheckBox)
      }
    })

    add(createTitledPanel("Find by", findTextArea), TabularLayout.Constraint(1, 0))
    add(JSeparator(), TabularLayout.Constraint(1, 2))
    add(createTitledPanel("Replace with", replaceTextArea), TabularLayout.Constraint(1, 4))
    add(JPanel(BorderLayout()).apply {
      add(replaceEntireBodyCheckBox, BorderLayout.WEST)
      add(regexCheckBox.withRegexLabel(), BorderLayout.EAST)
    }, TabularLayout.Constraint(2, 0))
    minimumSize = Dimension(800, preferredSize.height)
  }

  override fun doOKAction() {
    super.doOKAction()
    if (replaceEntireBodyCheckBox.isSelected) {
      saveAction(RuleData.BodyReplacedRuleData(replaceTextArea.text))
    }
    else {
      saveAction(RuleData.BodyModifiedRuleData(findTextArea.text, regexCheckBox.isSelected, replaceTextArea.text))
    }
  }

  private fun createTitledPanel(titleName: String, body: JComponent): JPanel {
    val panel = JPanel(TabularLayout("*", "Fit,6px,*"))
    val headingPanel = TitledSeparator(titleName)
    headingPanel.minimumSize = Dimension(0, 34)
    panel.add(headingPanel, TabularLayout.Constraint(0, 0))
    val scroll = JBScrollPane(body).apply {
      // Set JBScrollPane transparent to render an inactive JBTextArea with correct background color.
      isOpaque = false
      viewport.isOpaque = false
    }
    panel.add(scroll, TabularLayout.Constraint(2, 0))
    return panel
  }

  private fun updateIsOKActionEnabled(replaceEntireBodyCheckBox: JBCheckBox) {
    isOKActionEnabled = replaceEntireBodyCheckBox.isSelected || findTextArea.text.isNotBlank()
  }
}
