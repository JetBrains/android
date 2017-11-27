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

import com.android.tools.adtui.model.stdui.CommonTextFieldModel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.stdui.StandardDimensions.HORIZONTAL_PADDING
import com.android.tools.adtui.stdui.StandardDimensions.VERTICAL_PADDING
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Insets
import javax.swing.JTextField
import javax.swing.text.PlainDocument

/**
 * Android Studio TextField with Standard Borders.
 *
 * TODO: Add Text Completion.
 */
open class CommonTextField(val model: CommonTextFieldModel) : JTextField() {

  init {
    isFocusable = true
    document = PlainDocument()
    foreground = StandardColors.TEXT_COLOR
    background = StandardColors.BACKGROUND_COLOR
    selectedTextColor = StandardColors.SELECTED_TEXT_COLOR
    selectionColor = StandardColors.SELECTED_BACKGROUND_COLOR
    disabledTextColor = StandardColors.DISABLED_TEXT_COLOR
    isOpaque = false
    text = model.value
    super.setEditable(model.editable)

    model.addListener(object: ValueChangedListener {
      override fun valueChanged() {
        text = model.value
        isEnabled = model.enabled
      }
    })
    @Suppress("LeakingThis")
    UIUtil.addUndoRedoActions(this)
  }

  override fun updateUI() {
    setUI(CommonTextFieldUI(this))
    revalidate()
  }

  override fun setText(text: String?) {
    super.setText(text)
    UIUtil.resetUndoRedoActions(this)
  }

  override fun getInsets(): Insets {
    val fromBorder = border?.getBorderInsets(this) ?: JBUI.insets(0)
    if (border !is StandardBorder) return fromBorder
    val insets = JBUI.insets(VERTICAL_PADDING, HORIZONTAL_PADDING, VERTICAL_PADDING, HORIZONTAL_PADDING)
    insets.left += fromBorder.left
    insets.right += fromBorder.right
    insets.top += fromBorder.top
    insets.bottom += fromBorder.bottom
    return insets
  }
}
