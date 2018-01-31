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
package com.android.tools.adtui.stdui

import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.plaf.ColorUIResource
import javax.swing.plaf.UIResource
import javax.swing.plaf.basic.BasicTextFieldUI

/**
 * TextFieldUI for a [CommonTextField].
 */
open class CommonTextFieldUI(private val editor: CommonTextField) : BasicTextFieldUI() {

  init {
    editor.addFocusListener(
        object : FocusAdapter() {
          override fun focusGained(event: FocusEvent) {
            editor.repaint()
          }

          override fun focusLost(event: FocusEvent) {
            editor.repaint()
          }
        }
    )
  }

  override fun installDefaults() {
    super.installDefaults()

    if (editor.border == null || editor.border is UIResource) {
      editor.border = StandardBorder(StandardDimensions.TEXT_FIELD_CORNER_RADIUS)
    }
    if (editor.foreground == null || editor.foreground is UIResource) {
      editor.foreground = ColorUIResource(StandardColors.TEXT_COLOR)
    }
    if (editor.background == null || editor.background is UIResource) {
      editor.background = ColorUIResource(StandardColors.BACKGROUND_COLOR)
    }
    if (editor.selectedTextColor == null || editor.selectedTextColor is UIResource) {
      editor.selectedTextColor = ColorUIResource(StandardColors.SELECTED_TEXT_COLOR)
    }
    if (editor.selectionColor == null || editor.selectionColor is UIResource) {
      editor.selectionColor = ColorUIResource(StandardColors.SELECTED_BACKGROUND_COLOR)
    }
    if (editor.disabledTextColor == null || editor.disabledTextColor is UIResource) {
      editor.disabledTextColor = ColorUIResource(StandardColors.DISABLED_TEXT_COLOR)
    }
  }

  override fun paintSafely(g: Graphics) {
    val g2 = g as Graphics2D

    val hasErrors = !editor.model.validationError(editor.text).isEmpty()
    val hasFocus = editor.isFocusOwner
    val hasVisiblePlaceHolder = editor.text.isEmpty() && editor.model.placeHolderValue.isNotEmpty()
    (editor.border as? StandardBorder)?.paintBorder(editor, g, hasErrors, hasFocus, hasVisiblePlaceHolder)
    if (hasVisiblePlaceHolder) {
      printPlaceHolderText(g2)
    }
    else {
      g2.setColorAndAlpha(textColor)
    }
    super.paintSafely(g2)
  }

  private val textColor: Color
    get() = if (editor.isEnabled) StandardColors.TEXT_COLOR else StandardColors.DISABLED_TEXT_COLOR

  private fun printPlaceHolderText(g2: Graphics2D) {
    val insets = editor.insets
    val baseline = editor.getBaseline(editor.width, editor.height)
    g2.setColorAndAlpha(StandardColors.PLACEHOLDER_TEXT_COLOR)
    g2.font = editor.font
    g2.drawString(editor.model.placeHolderValue, insets.left, baseline)
  }
}
