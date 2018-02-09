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

import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.idea.common.property2.api.EnumValue
import com.android.tools.idea.common.property2.impl.model.ComboBoxPropertyEditorModel
import com.android.tools.idea.common.property2.impl.support.EditorFocusListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * A standard control for editing property values with a popup list.
 *
 * This control will act as a ComboBox or a DropDown depending on the model.
 */
class PropertyComboBox(model: ComboBoxPropertyEditorModel): CommonComboBox<EnumValue>(model) {
  private var ignoreUpdates = false

  init {
    registerKeyAction({ model.enterKeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter")
    registerKeyAction({ model.escape() }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "escape")

    val editor = editor.editorComponent as JTextField
    editor.registerKeyAction({ model.enter(currentValue) }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter")
    editor.registerKeyAction({ model.escape() }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape")
    editor.registerKeyAction({ model.f1KeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "help")
    editor.registerKeyAction({ model.shiftF1KeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK), "help2")

    addFocusListener(EditorFocusListener(model, { currentValue }))
    addPopupMenuListener(
        object : PopupMenuListener {
          override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) {
            handlePopupChange(false)
          }

          override fun popupMenuCanceled(event: PopupMenuEvent) {
          }

          override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) {
            handlePopupChange(true)
          }
        })
  }

  private fun handlePopupChange(becomeVisible: Boolean) {
    ignoreUpdates = true
    try {
      val model = model as ComboBoxPropertyEditorModel
      model.isPopupVisible = becomeVisible
    }
    finally {
      ignoreUpdates = false
    }
  }

  private val currentValue: String
    get() = if (isEditable) editor.item.toString() else model.selectedItem?.toString() ?: ""

  override fun updateFromModel() {
    if (ignoreUpdates) return

    super.updateFromModel()
    val model = model as ComboBoxPropertyEditorModel
    isVisible = model.visible
    if (model.focusRequest && !isFocusOwner) {
      requestFocusInWindow()
    }
    if (model.isPopupVisible != isPopupVisible) {
      // TODO: Handle this differently:
      isPopupVisible = model.isPopupVisible
    }
  }
}
