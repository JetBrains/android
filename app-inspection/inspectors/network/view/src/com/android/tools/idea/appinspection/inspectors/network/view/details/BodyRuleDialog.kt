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
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.VerticalLayout
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator

/**
 * A dialog box that allows adding and editing body rules.
 */
class BodyRuleDialog(
  transformation: RuleData.TransformationRuleData?,
  private val saveAction: (RuleData.TransformationRuleData) -> Unit
) : DialogWrapper(false) {

  @VisibleForTesting
  val findTextArea = JBTextArea()

  @VisibleForTesting
  val replaceTextArea = JBTextArea()

  @VisibleForTesting
  val regexCheckBox = JBCheckBox("Regex")

  init {
    title = "New Header Rule"
    transformation?.let { applySavedBody(it) }
    init()
  }

  private fun applySavedBody(bodyRule: RuleData.TransformationRuleData) {
    when (bodyRule) {
      is RuleData.BodyModifiedRuleData -> {
        findTextArea.text = bodyRule.targetText
        replaceTextArea.text = bodyRule.newText
        regexCheckBox.isSelected = bodyRule.isRegex
      }
      is RuleData.BodyReplacedRuleData -> {
        findTextArea.text = ""
        replaceTextArea.text = bodyRule.body
        regexCheckBox.isSelected = false
      }
    }
  }

  override fun createNorthPanel() = JPanel(VerticalLayout(18)).apply {
    add(JPanel(TabularLayout("300px,20px,Fit,20px,300px", "350px")).apply {
      add(findTextArea, TabularLayout.Constraint(0, 0))
      add(JSeparator(), TabularLayout.Constraint(0, 2))
      add(replaceTextArea, TabularLayout.Constraint(0, 4))
    })
    add(regexCheckBox)
  }

  override fun createCenterPanel(): JComponent? = null

  override fun doOKAction() {
    super.doOKAction()
    val findText = findTextArea.text
    if (findText.isBlank()) {
      saveAction(RuleData.BodyReplacedRuleData(replaceTextArea.text))
    }
    else {
      saveAction(RuleData.BodyModifiedRuleData(findText, regexCheckBox.isSelected, replaceTextArea.text))
    }
  }
}
