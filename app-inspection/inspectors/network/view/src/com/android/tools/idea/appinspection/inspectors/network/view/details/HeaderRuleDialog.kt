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

import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import org.jetbrains.annotations.VisibleForTesting
import java.awt.CardLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A dialog box that allows adding and editing header rules.
 */
class HeaderRuleDialog(private val saveAction: (RuleData.TransformationRuleData) -> Unit) : DialogWrapper(false) {

  companion object {
    private const val DEFAULT_TEXT = "Text"
    private const val REGEX_TEXT = "Regex"
  }

  @VisibleForTesting
  val newAddedNameLabel: JBTextField = createTextField(DEFAULT_TEXT, TEXT_LABEL_WIDTH)

  @VisibleForTesting
  val newAddedValueLabel: JBTextField = createTextField(DEFAULT_TEXT, TEXT_LABEL_WIDTH)

  @VisibleForTesting
  val findNameLabel: JBTextField = createTextField(DEFAULT_TEXT, TEXT_LABEL_WIDTH)

  @VisibleForTesting
  val findNameRegexCheckBox = JBCheckBox(REGEX_TEXT)

  @VisibleForTesting
  val findValueLabel: JBTextField = createTextField(DEFAULT_TEXT, TEXT_LABEL_WIDTH)

  @VisibleForTesting
  val findValueRegexCheckBox = JBCheckBox(REGEX_TEXT)

  @VisibleForTesting
  val newReplacedNameLabel: JBTextField = createTextField(DEFAULT_TEXT, TEXT_LABEL_WIDTH)

  @VisibleForTesting
  val newReplacedValueLabel: JBTextField = createTextField(DEFAULT_TEXT, TEXT_LABEL_WIDTH)

  @VisibleForTesting
  val addRadioButton = JBRadioButton("Add new header")

  @VisibleForTesting
  val replaceRadioButton = JBRadioButton("Edit existing header")

  init {
    title = "New Header Rule"
    init()
  }

  override fun createNorthPanel() = JPanel(VerticalLayout(20)).apply {
    val addPanel = JPanel(VerticalLayout(10)).apply {
      add(createKeyValuePair("Name", newAddedNameLabel))
      add(createKeyValuePair("Value", newAddedValueLabel))
    }

    val replacePanel = JPanel(VerticalLayout(10)).apply {
      add(createCategoryPanel("Find", listOf(
        createKeyValuePair("Name", findNameLabel.withRegexCheckBox(findNameRegexCheckBox)),
        createKeyValuePair("Value", findValueLabel.withRegexCheckBox(findValueRegexCheckBox))
      )))
      add(createCategoryPanel("Replace with", listOf(
        createKeyValuePair("Name", newReplacedNameLabel),
        createKeyValuePair("Value", newReplacedValueLabel)
      )))
    }

    val cardLayout = CardLayout()
    val cardView = JPanel(cardLayout)
    val addKey = "add"
    val replaceKey = "replace"
    cardView.add(addPanel, addKey)
    cardView.add(replacePanel, replaceKey)

    addRadioButton.addActionListener {
      if (addRadioButton.isSelected) {
        cardLayout.show(cardView, addKey)
      }
    }
    replaceRadioButton.addActionListener {
      if (replaceRadioButton.isSelected) {
        cardLayout.show(cardView, replaceKey)
      }
    }
    ButtonGroup().apply {
      add(addRadioButton)
      add(replaceRadioButton)
    }
    add(createKeyValuePair(
      "Rule action",
      JPanel(HorizontalLayout(20)).apply {
        add(addRadioButton)
        add(replaceRadioButton)
      }
    ))
    add(cardView)
    addRadioButton.isSelected = true
  }

  override fun createCenterPanel(): JComponent? = null

  override fun doOKAction() {
    super.doOKAction()
    if (addRadioButton.isSelected) {
      saveAction(RuleData.HeaderAddedRuleData(newAddedNameLabel.text, newAddedValueLabel.text))
    }
    else {
      saveAction(RuleData.HeaderReplacedRuleData(
        findNameLabel.text,
        findNameRegexCheckBox.isSelected,
        findValueLabel.text,
        findValueRegexCheckBox.isSelected,
        newReplacedNameLabel.text,
        newReplacedValueLabel.text
      ))
    }
  }

  private fun JBTextField.withRegexCheckBox(checkBox: JBCheckBox) = JPanel(HorizontalLayout(20)).apply {
    add(this@withRegexCheckBox)
    add(checkBox)
  }
}
