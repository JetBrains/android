/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.tools.adtui.common.borderLight
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.lang.Integer.max
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/** A [JBTextField] with a warning [JBLabel] next to it */
internal class TextFieldWithWarning(
  initialText: String?,
  hintText: String,
  name: String? = null,
  private val validate: (String) -> String?,
  apply: (String) -> Unit,
) : JPanel(TabularLayout("*,Fit")), StateValidator {
  private val warningLabel =
    JBLabel(StudioIcons.Common.WARNING).apply {
      this.name = "${name}WarningLabel"
      isVisible = false
      border = JBUI.Borders.emptyLeft(5)
    }

  private val textField =
    JBTextField(initialText).apply {
      this.name = "${name}TextField"
      emptyText.appendText(hintText)
      preferredSize =
        Dimension(
          max(preferredSize.width, emptyText.preferredSize.width + font.size),
          max(preferredSize.height, emptyText.preferredSize.height),
        )
      border = BorderFactory.createLineBorder(borderLight)
      addFocusListener(
        object : FocusAdapter() {
          override fun focusLost(e: FocusEvent) {
            if (validateState()) {
              apply(text.trim())
            }
          }
        }
      )
      document.addDocumentListener(
        object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            validateState()
          }
        }
      )
    }

  init {
    add(textField, TabularLayout.Constraint(0, 0))
    add(warningLabel, TabularLayout.Constraint(0, 1))
    validateState()
  }

  override fun setEnabled(enabled: Boolean) {
    textField.isEnabled = enabled
  }

  override fun isEnabled(): Boolean {
    return textField.isEnabled
  }

  override fun validateState(): Boolean {
    val warning = validate(textField.text.trim())
    val isValid = warning == null
    warningLabel.isVisible = !isValid
    warningLabel.toolTipText = warning
    return isValid
  }
}
