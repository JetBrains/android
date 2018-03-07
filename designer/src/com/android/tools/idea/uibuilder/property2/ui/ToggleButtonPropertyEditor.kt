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
package com.android.tools.idea.uibuilder.property2.ui

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.impl.support.EditorFocusListener
import com.android.tools.idea.common.property2.impl.ui.registerKeyAction
import com.android.tools.idea.uibuilder.property2.model.ToggleButtonPropertyEditorModel
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.ToggleActionButton
import java.awt.BorderLayout
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.KeyStroke.getKeyStroke

/**
 * Button with icon that is either pressed (on) or unchanged (off).
 */
class ToggleButtonPropertyEditor(val model: ToggleButtonPropertyEditorModel) : JPanel(BorderLayout()) {

  init {
    val action = ButtonAction(model)
    val presentation = action.templatePresentation.clone()
    val button = ActionButton(action, presentation, ActionPlaces.UNKNOWN, NAVBAR_MINIMUM_BUTTON_SIZE)
    add(button, BorderLayout.CENTER)
    button.isFocusable = true
    button.registerKeyAction({ model.enterKeyPressed() }, getKeyStroke(KeyEvent.VK_ENTER, 0), "enter")
    button.registerKeyAction({ model.f1KeyPressed() }, getKeyStroke(KeyEvent.VK_F1, 0), "help")
    button.registerKeyAction({ model.shiftF1KeyPressed() }, getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK), "help2")
    button.addFocusListener(EditorFocusListener(model, { "" }))

    model.addListener(ValueChangedListener {
      // This will update the selected state of the ActionButton:
      val context = DataManager.getInstance().getDataContext(button)
      val event = AnActionEvent(null, context, ActionPlaces.UNKNOWN, presentation, ActionManager.getInstance(), 0)
      ActionUtil.performDumbAwareUpdate(false, action, event, false)
      if (model.focusRequest && !isFocusOwner) {
        button.requestFocusInWindow()
      }
    })
  }

  private class ButtonAction(private val model: ToggleButtonPropertyEditorModel) : ToggleActionButton(model.description, model.icon) {
    override fun isSelected(event: AnActionEvent): Boolean {
      return model.selected
    }

    override fun setSelected(event: AnActionEvent, selected: Boolean) {
      model.selected = selected
    }
  }
}
