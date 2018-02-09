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

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.api.PropertyEditorModel
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A standard class for implementing [com.android.tools.idea.common.property2.api.ActionButtonSupport] for an editor.
 *
 * The editor component is wrapped in panel with a possible icon to the right displaying of the editor.
 */
class ActionButtonBinding(val model: PropertyEditorModel, editor: JComponent): JPanel(BorderLayout()) {
  private val boundImage = JBLabel()

  init {
    add(editor, BorderLayout.CENTER)
    add(boundImage, BorderLayout.EAST)
    updateFromModel()

    model.addListener(ValueChangedListener { updateFromModel() })

    addMouseListener(object: MouseAdapter() {
      override fun mouseClicked(event: MouseEvent) {
        buttonPressed(event)
      }
    })
    boundImage.isFocusable = model.actionButtonFocusable
    boundImage.addFocusListener(object: FocusListener {
      override fun focusLost(event: FocusEvent) {
        updateFromModel()
      }

      override fun focusGained(event: FocusEvent) {
        updateFromModel()
      }
    })
  }

  private fun updateFromModel() {
    boundImage.icon = model.getActionIcon(boundImage.hasFocus())
    isVisible = model.visible
  }

  private fun buttonPressed(mouseEvent: MouseEvent) {
    val action = model.buttonAction ?: return
    if (action is ActionGroup) {
      val popupMenu = ActionManager.getInstance().createActionPopupMenu(ToolWindowContentUi.POPUP_PLACE, action)
      popupMenu.component.show(this, mouseEvent.x, mouseEvent.y)
    }
    else {
      val event = AnActionEvent.createFromAnAction(action, mouseEvent, ActionPlaces.UNKNOWN, DataManager.getInstance().getDataContext(this))
      action.actionPerformed(event)
    }
  }
}
