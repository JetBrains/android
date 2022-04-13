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
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A dialog box that allows adding and editing header rules.
 */
class HeaderRuleDialog(private val saveAction: (RuleData.TransformationRuleData) -> Unit) : DialogWrapper(false) {

  companion object {
    private const val DEFAULT_TEXT = "Text"
  }

  private val nameLabel: JBTextField = createTextField(DEFAULT_TEXT, TEXT_LABEL_WIDTH)
  private val valueLabel: JBTextField = createTextField(DEFAULT_TEXT, TEXT_LABEL_WIDTH)

  init {
    title = "New Header Rule"
    init()
  }

  override fun createNorthPanel() = JPanel(VerticalLayout(18)).apply {
    add(createKeyValuePair("Name") { nameLabel })
    add(createKeyValuePair("Value") { valueLabel })
  }

  override fun createCenterPanel(): JComponent? = null

  override fun doOKAction() {
    super.doOKAction()
    saveAction(RuleData.HeaderAddedRuleData(nameLabel.text, valueLabel.text))
  }
}
