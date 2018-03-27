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
package com.android.tools.idea.common.property2.impl.ui

import com.android.tools.adtui.stdui.CommonTextField
import com.android.tools.idea.common.property2.impl.model.TextFieldPropertyEditorModel
import com.android.tools.idea.common.property2.impl.support.EditorFocusListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

/**
 * A standard control for editing a text property.
 */
class PropertyTextField(editorModel: TextFieldPropertyEditorModel) : CommonTextField<TextFieldPropertyEditorModel>(editorModel) {

  init {
    registerKeyAction({ editorModel.enter(text) }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter")
    registerKeyAction({ editorModel.escape() }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape")
    registerKeyAction({ editorModel.f1KeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "help")
    registerKeyAction({ editorModel.shiftF1KeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK), "help2")
    addFocusListener(EditorFocusListener(editorModel, { text }))
  }

  override fun updateFromModel() {
    super.updateFromModel()
    isVisible = editorModel.visible
    if (editorModel.focusRequest && !isFocusOwner) {
      requestFocusInWindow()
    }
  }

  override fun getToolTipText(): String? = editorModel.tooltip
}
