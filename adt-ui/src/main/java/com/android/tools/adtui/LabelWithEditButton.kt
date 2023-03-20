/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.adtui

import com.intellij.ui.layout.CellBuilder
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selected
import com.intellij.util.ui.StartupUiUtil
import java.awt.Color
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.border.Border
import javax.swing.text.Document

private const val EDIT_TEXT = "Edit"
private const val DONE_TEXT = "Done"

/**
 * A label with an "edit" button that turns it into a text field
 */
class LabelWithEditButton(defaultValue: String = "") : JPanel(), DocumentAccessor {
  val button = JToggleButton(EDIT_TEXT).apply {
    addActionListener {
      text = if (isSelected) DONE_TEXT else EDIT_TEXT
    }
  }
  val textField: JTextField = SometimesEditableTextField(defaultValue).apply {
    isEnabled = false
    font = StartupUiUtil.labelFont
  }

  val panel = panel {
    row {
      textField(growX).enableIf(button.selected).focusInWindowIf(button.selected)
      right {
        button(growX)
      }
    }
  }

  init {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    add(panel)
  }

  override fun setText(text: String) {
    textField.text = text
  }

  override fun getText(): String = textField.text

  override fun setFont(font: Font) {
    super.setFont(font)

    // Swing seems to abuse reflection. It tries to set the default look and feel before proper class initialization
    @Suppress("UNNECESSARY_SAFE_CALL")
    textField?.font = font
  }

  override fun getDocument(): Document = textField.document
}

private class SometimesEditableTextField(text: String): JTextField(text) {
  override fun getBorder(): Border? = if (isEnabled) super.getBorder() else BorderFactory.createEmptyBorder()
  override fun getBackground(): Color? = if (isEnabled || parent == null) super.getBackground() else parent.background
}

private fun CellBuilder<*>.focusInWindowIf(predicate: ComponentPredicate): CellBuilder<*> {
  if (predicate()) {
    component.requestFocusInWindow()
  }
  predicate.addListener { component.requestFocusInWindow() }
  return this
}

