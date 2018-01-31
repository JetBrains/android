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
import com.intellij.util.ui.UIUtil
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
    isOpaque = false
    setFromModel()

    model.addListener(object: ValueChangedListener {
      override fun valueChanged() {
        updateFromModel()
      }
    })
    @Suppress("LeakingThis")
    UIUtil.addUndoRedoActions(this)
  }

  protected open fun updateFromModel() {
    setFromModel()
  }

  private fun setFromModel() {
    text = model.value
    isEnabled = model.enabled
    isEditable = model.editable
  }

  override fun updateUI() {
    setUI(CommonTextFieldUI(this))
    revalidate()
  }

  override fun setText(text: String?) {
    super.setText(text)
    UIUtil.resetUndoRedoActions(this)
  }
}
