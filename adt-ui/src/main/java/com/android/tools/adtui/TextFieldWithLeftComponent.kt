/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.UIManager

private const val ICON_SIDES_MARGIN = 4

/**
 * Wraps the given [JComponent] and [JTextField] in a panel that has the appearance of a regular TextField, with the [leftComponent]
 * placed left to the [textField].
 *
 * The panel paints as focused whenever [textField] would be focused.
 *
 * Since the panel will have the TextField appearance, [textField] and [leftComponent] are stripped from their border and background.
 */
class TextFieldWithLeftComponent(private val leftComponent: JComponent, val textField: JTextField) : JPanel(BorderLayout()) {
  private val focusListener: FocusListener = object : FocusListener {
    override fun focusLost(e: FocusEvent?) {
      repaint()
    }

    override fun focusGained(e: FocusEvent?) {
      repaint()
    }
  }

  init {
    border = DarculaTextBorder()
    leftComponent.border = JBUI.Borders.empty(0, ICON_SIDES_MARGIN)
    leftComponent.isOpaque = false
    textField.border = JBUI.Borders.empty()
    textField.isOpaque = false
    textField.addFocusListener(focusListener)
    super.add(leftComponent, BorderLayout.WEST)
    super.add(textField, BorderLayout.CENTER)
  }

  override fun requestFocus() {
    leftComponent.requestFocusInWindow() || textField.requestFocusInWindow()
  }

  override fun hasFocus(): Boolean {
    return textField.hasFocus()
  }

  override fun paintComponent(g: Graphics?) {
    super.paintComponent(g)
    g?.apply {
      val borderInsets = insets
      val insetsW = borderInsets.left + borderInsets.right
      val insetsH = borderInsets.top + borderInsets.bottom
      color = UIManager.getColor("TextField.background")
      fillRect(borderInsets.left, borderInsets.top, width - insetsW, height - insetsH)
    }
  }
}