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
package com.android.tools.idea.uibuilder.property.ui

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.uibuilder.property.model.ToggleButtonPropertyEditorModel
import com.android.tools.property.panel.api.HelpSupport
import com.android.tools.property.panel.impl.support.EditorFocusListener
import com.android.tools.property.panel.impl.support.HelpSupportBinding
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAwareToggleAction
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** Button with icon that is either pressed (on) or unchanged (off). */
class ToggleButtonPropertyEditor(val model: ToggleButtonPropertyEditorModel) :
  JPanel(BorderLayout()), UiDataProvider {

  init {
    val action = ButtonAction(model)
    val presentation = action.templatePresentation.clone()
    val button =
      ActionButton(action, presentation, ActionPlaces.UNKNOWN, NAVBAR_MINIMUM_BUTTON_SIZE)
    add(button, BorderLayout.CENTER)
    button.isFocusable = true
    isFocusable = false
    HelpSupportBinding.registerHelpKeyActions(
      this,
      { model.property },
      JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
    )
    button.addFocusListener(EditorFocusListener(this, model))

    model.addListener(
      ValueChangedListener {
        // This will update the selected state of the ActionButton:
        val context = DataManager.getInstance().getDataContext(button)
        val event =
          AnActionEvent(
            null,
            context,
            ActionPlaces.UNKNOWN,
            presentation,
            ActionManager.getInstance(),
            0,
          )
        ActionUtil.performDumbAwareUpdate(action, event, false)
        if (model.focusRequest && !isFocusOwner) {
          button.requestFocusInWindow()
        }
      }
    )
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[HelpSupport.PROPERTY_ITEM] = model.property
  }

  private class ButtonAction(private val model: ToggleButtonPropertyEditorModel) :
    DumbAwareToggleAction(model.description, null, model.icon) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(event: AnActionEvent): Boolean {
      return model.selected
    }

    override fun setSelected(event: AnActionEvent, selected: Boolean) {
      model.selected = selected
    }
  }
}
