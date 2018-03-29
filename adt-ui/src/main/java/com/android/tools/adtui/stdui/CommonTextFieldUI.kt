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

import com.android.tools.adtui.model.stdui.CommonBorderModel
import com.android.tools.adtui.stdui.StandardDimensions.HORIZONTAL_PADDING
import com.android.tools.adtui.stdui.StandardDimensions.TEXT_FIELD_CORNER_RADIUS
import com.android.tools.adtui.stdui.StandardDimensions.VERTICAL_PADDING
import java.awt.Color
import java.awt.Graphics
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.LookAndFeel
import javax.swing.plaf.BorderUIResource
import javax.swing.plaf.ColorUIResource
import javax.swing.plaf.UIResource
import javax.swing.plaf.basic.BasicTextFieldUI

/**
 * TextFieldUI for a [CommonTextField].
 */
open class CommonTextFieldUI(private val editor: CommonTextField<*>) : BasicTextFieldUI() {

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
      editor.border = BorderUIResource(CommonBorder(
        TEXT_FIELD_CORNER_RADIUS, EditorCommonBorderModel(editor), VERTICAL_PADDING, HORIZONTAL_PADDING, 0, HORIZONTAL_PADDING))
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
    LookAndFeel.installProperty(editor, "opaque", true)
  }

  override fun paintSafely(g: Graphics) {
    paintBackground(g)
    if (editor.text.isEmpty() && editor.editorModel.placeHolderValue.isNotEmpty()) {
      printPlaceHolderText(g)
    }
    g.color = textColor

    super.paintSafely(g)
  }

  override fun paintBackground(g: Graphics) {
    val insets = editor.border.getBorderInsets(editor)
    g.color = editor.background
    g.fillRect(insets.left, insets.top,  editor.width - insets.left - insets.right, editor.height - insets.top - insets.bottom)
  }

  private val textColor: Color
    get() = if (editor.isEnabled) StandardColors.TEXT_COLOR else StandardColors.DISABLED_TEXT_COLOR

  private fun printPlaceHolderText(g: Graphics) {
    val insets = editor.insets
    val baseline = editor.getBaseline(editor.width, editor.height)
    g.color = StandardColors.PLACEHOLDER_TEXT_COLOR
    g.font = editor.font
    g.drawString(editor.editorModel.placeHolderValue, insets.left, baseline)
  }

  // Note: this violates the golden rule about not having component references in a model.
  // However in this case initDefaults() are being called in the constructor of JTextField
  // before the constructor of CommonTextField has been executed.
  private class EditorCommonBorderModel(private val editor: CommonTextField<*>): CommonBorderModel {
    override val hasError: Boolean
      get() = editor.editorModel.validate(editor.editorModel.text).isNotEmpty()

    override val hasPlaceHolder: Boolean
      get() = editor.editorModel.text.isEmpty() && editor.editorModel.placeHolderValue.isNotEmpty()
  }
}
