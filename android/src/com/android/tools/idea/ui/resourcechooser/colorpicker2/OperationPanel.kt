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
package com.android.tools.idea.ui.resourcechooser.colorpicker2

import com.android.tools.adtui.stdui.CommonButton
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JPanel

private const val PANEL_BORDER_OFFSET = 10
private const val PANEL_HEIGHT = 40
private const val BUTTON_BORDER_SIZE = 5

/**
 * Used to create the [OperationPanel] which is used to place Cancel and OK buttons with the given callback functions.<br>
 * [IllegalArgumentException] will be raised if both [ok] and [cancel] are null.
 *
 * @param model The associated [ColorPickerModel].
 * @param ok Callback when clicking "OK" button. The "OK" button only appear when this parameter is not-null.
 * @param cancel Callback when clicking "Cancel" button.  The "OK" button only appear when this parameter is not-null.
 */
class OperationPanel(private val model: ColorPickerModel,
                     ok: ((Color) -> Unit)?,
                     cancel: ((Color) -> Unit)?)
  : JPanel(BorderLayout()) {

  init {
    if (ok == null && cancel == null) {
      throw IllegalStateException("${this::class.simpleName} must contains at least one operation")
    }
    border = JBUI.Borders.empty(PANEL_BORDER_OFFSET)
    preferredSize = JBUI.size(COLOR_PICKER_WIDTH, PANEL_HEIGHT)
    background = PICKER_BACKGROUND_COLOR

    if (cancel != null) {
      val cancelButton = MyButton("Cancel")
      cancelButton.border = JBUI.Borders.empty(BUTTON_BORDER_SIZE)
      cancelButton.addActionListener { cancel(model.color) }
      add(cancelButton, BorderLayout.WEST)
    }
    if (ok != null) {
      val okButton = MyButton("OK")
      okButton.border = JBUI.Borders.empty(BUTTON_BORDER_SIZE)
      okButton.addActionListener { ok(model.color) }
      add(okButton, BorderLayout.EAST)
    }
  }
}

/**
 * TODO: Remove this after [CommonButton.isFocusable] returns true.
 */
private class MyButton(text: String) : CommonButton(text) {
  override fun isFocusable(): Boolean = true
}
