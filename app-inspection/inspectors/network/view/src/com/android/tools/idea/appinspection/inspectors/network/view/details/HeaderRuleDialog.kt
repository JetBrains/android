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
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.BooleanFunction
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.VisibleForTesting
import java.awt.event.ItemEvent
import java.awt.event.ItemEvent.ITEM_STATE_CHANGED
import javax.swing.JPanel
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

/**
 * A dialog box that allows adding and editing header rules.
 */
class HeaderRuleDialog(
  transformation: RuleData.TransformationRuleData?,
  private val saveAction: (RuleData.TransformationRuleData) -> Unit
) : DialogWrapper(false) {

  @VisibleForTesting
  val newAddedNameLabel: JBTextField = createTextField(null, "Access-Control-Allow-Origin").apply {
    (document as AbstractDocument).documentFilter = EmptyFieldDocumentFilter(::updateOkAction)
  }

  @VisibleForTesting
  val newAddedValueLabel: JBTextField = createTextField(null, "https://www.google.com")

  @VisibleForTesting
  val findNameTextField: JBTextField = createTextField(null, "Access-Control-Allow-Origin")

  @VisibleForTesting
  val findNameRegexCheckBox = JBCheckBox()

  @VisibleForTesting
  val findValueTextField: JBTextField = createTextField(null, "https://www.google.com")

  @VisibleForTesting
  val findValueRegexCheckBox = JBCheckBox()

  @VisibleForTesting
  val newReplacedNameTextField: JBTextField = createTextField(null, "Cache-Control")

  @VisibleForTesting
  val newReplacedValueTextField: JBTextField = createTextField(null, "max-age=604800")

  @VisibleForTesting
  val findNameCheckBox = createFieldEnabledCheckBox("Header name:", findNameTextField, findNameRegexCheckBox)

  @VisibleForTesting
  val findValueCheckBox = createFieldEnabledCheckBox("Header value:", findValueTextField, findValueRegexCheckBox)

  @VisibleForTesting
  val replaceNameCheckBox = createFieldEnabledCheckBox("Header name:", newReplacedNameTextField, null)

  @VisibleForTesting
  val replaceValueCheckBox = createFieldEnabledCheckBox("Header value:", newReplacedValueTextField, null)

  private fun createFieldEnabledCheckBox(name: String, textField: JBTextField, regexCheckBox: JBCheckBox?) =
    JBCheckBox(name).apply {
      val changeAction: (e: ItemEvent) -> Unit = {
        textField.isEnabled = isSelected
        if (!isSelected) {
          regexCheckBox?.isSelected = false
          textField.text = ""
        }
        regexCheckBox?.isEnabled = isSelected
        updateOkAction()
      }
      textField.putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, BooleanFunction<JBTextField>
      { !it.isEnabled })
      addItemListener(changeAction)
      changeAction(ItemEvent(this, 0, null, ITEM_STATE_CHANGED))
    }

  private fun updateOkAction() {
    @Suppress("SENSELESS_COMPARISON") // tabs will be null during initialization
    if (tabs == null) return
    okAction.isEnabled = (tabs.selectedComponent == newHeaderPanel && newAddedNameLabel.text.isNotBlank()) || // Blank value is fine
                         ((findNameCheckBox.isSelected || findValueCheckBox.isSelected)
                          && (replaceNameCheckBox.isSelected || replaceValueCheckBox.isSelected))
    setOKButtonTooltip(if (okAction.isEnabled) null else {
      if (tabs.selectedComponent == newHeaderPanel) {
        "Please provide a non-empty header name"
      } else {
        "Please select a header name or value to replace"
      }
    })
  }

  @VisibleForTesting
  val newHeaderPanel = JPanel(VerticalLayout(10)).apply {
    border = JBUI.Borders.empty(15, 0, 0, 0)
    add(createCategoryPanel(null,
                            JBLabel("New header name:") to newAddedNameLabel,
                            JBLabel("Value:") to newAddedValueLabel))
  }

  @VisibleForTesting
  val editHeaderPanel = JPanel(VerticalLayout(10)).apply {
    border = JBUI.Borders.empty(5, 0, 0, 0)
    add(createCategoryPanel("Find by",
                            findNameCheckBox to findNameTextField.withRegexCheckBoxAndInfoIcon(
                              findNameRegexCheckBox,
                              "Header name matching is case insensitive. Regex that selects for case will have the case selection ignored"
                            ),
                            findValueCheckBox to findValueTextField.withRegexCheckBoxAndInfoIcon(
                              findValueRegexCheckBox,
                              "Header value match is case sensitive"
                            )
    ))
    add(createCategoryPanel("Replace with",
                            replaceNameCheckBox to newReplacedNameTextField,
                            replaceValueCheckBox to newReplacedValueTextField
    ))
  }

  @VisibleForTesting
  val tabs = JBTabbedPane().apply {
    addTab("Add new header", newHeaderPanel)
    addTab("Edit existing header", editHeaderPanel)
    model.addChangeListener { updateOkAction() }
  }

  init {
    title = "Header Rule"
    applySavedHeader(transformation)
    init()
    updateOkAction()
  }

  private fun applySavedHeader(headerRule: RuleData.TransformationRuleData?) {
    if (headerRule == null) {
      tabs.selectedComponent = newHeaderPanel
      return
    }
    when (headerRule) {
      is RuleData.HeaderAddedRuleData -> {
        newAddedNameLabel.text = headerRule.name
        newAddedValueLabel.text = headerRule.value
        tabs.selectedComponent = newHeaderPanel
      }
      is RuleData.HeaderReplacedRuleData -> {
        if (headerRule.findName != null) {
          findNameTextField.text = headerRule.findName
          findNameCheckBox.isSelected = true
          findNameRegexCheckBox.isSelected = headerRule.isFindNameRegex
        }
        if (headerRule.findValue != null) {
          findValueTextField.text = headerRule.findValue
          findValueCheckBox.isSelected = true
          findValueRegexCheckBox.isSelected = headerRule.isFindValueRegex
        }
        if (headerRule.newName != null) {
          newReplacedNameTextField.text = headerRule.newName
          replaceNameCheckBox.isSelected = true
        }
        if (headerRule.newValue != null) {
          newReplacedValueTextField.text = headerRule.newValue
          replaceValueCheckBox.isSelected = true
        }
        tabs.selectedComponent = editHeaderPanel
      }
    }
  }

  override fun createCenterPanel() = tabs

  override fun doOKAction() {
    super.doOKAction()
    if (tabs.selectedComponent == newHeaderPanel) {
      saveAction(RuleData.HeaderAddedRuleData(newAddedNameLabel.text, newAddedValueLabel.text))
    }
    else {
      saveAction(RuleData.HeaderReplacedRuleData(
        if (findNameCheckBox.isSelected) findNameTextField.text else null,
        findNameRegexCheckBox.isSelected,
        if (findValueCheckBox.isSelected) findValueTextField.text else null,
        findValueRegexCheckBox.isSelected,
        if (replaceNameCheckBox.isSelected) newReplacedNameTextField.text else null,
        if (replaceValueCheckBox.isSelected) newReplacedValueTextField.text else null
      ))
    }
  }

  private fun JBTextField.withRegexCheckBoxAndInfoIcon(checkBox: JBCheckBox, infoIconText: String) =
    JPanel(TabularLayout("*,20px,Fit,5px,Fit")).apply {
      add(this@withRegexCheckBoxAndInfoIcon, TabularLayout.Constraint(0, 0))
      add(checkBox.withRegexLabel(), TabularLayout.Constraint(0, 2))
      add(JBLabel(AllIcons.General.Information).apply {
        isEnabled = false
        toolTipText = infoIconText
      }, TabularLayout.Constraint(0, 4))
  }
}

class EmptyFieldDocumentFilter(val updateOkAction: () -> Unit): DocumentFilter() {
  override fun remove(fb: FilterBypass, offset: Int, length: Int) {
    super.remove(fb, offset, length)
    if (isDocumentEmpty(fb)) updateOkAction()
  }

  override fun insertString(fb: FilterBypass, offset: Int, string: String, attr: AttributeSet?) {
    super.insertString(fb, offset, string, attr)
    if(!isDocumentEmpty(fb)) updateOkAction()
  }

  override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String, attrs: AttributeSet?) {
    super.replace(fb, offset, length, text, attrs)
    if (!isDocumentEmpty(fb)) updateOkAction()
  }

  private fun isDocumentEmpty(fb: FilterBypass) = fb.document.getText(0, fb.document.length).isEmpty()
}

