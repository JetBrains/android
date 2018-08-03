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

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.impl.model.ColorFieldPropertyEditorModel
import com.intellij.ide.DataManager
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

const val ICON_BORDER = 2

class PropertyColorField(private val model: ColorFieldPropertyEditorModel, asTableCellEditor: Boolean): AdtSecondaryPanel(BorderLayout()) {
  private val iconField = JBLabel()
  private val textField = PropertyTextField(model, asTableCellEditor)

  init {
    border = DarculaTextBorder()
    iconField.border = JBUI.Borders.empty(0, ICON_BORDER, 0, 0)
    textField.border = JBUI.Borders.empty()
    textField.isOpaque = false
    add(iconField, BorderLayout.WEST)
    add(textField, BorderLayout.CENTER)
    model.addListener(ValueChangedListener { setFromModel() })
    iconField.addMouseListener(object: MouseAdapter() {
      override fun mousePressed(event: MouseEvent) {
        buttonPressed(event)
      }
    })
    setFromModel()
  }

  override fun hasFocus(): Boolean {
    return textField.hasFocus()
  }

  private fun setFromModel() {
    isVisible = model.visible
    iconField.icon = model.getDrawableIcon(hasFocus())
  }

  private fun buttonPressed(mouseEvent: MouseEvent) {
    var action = model.colorAction ?: return
    var event = AnActionEvent.createFromAnAction(action, mouseEvent, ActionPlaces.UNKNOWN, DataManager.getInstance().getDataContext(this))
      if (action is ActionGroup) {
        action = action.getChildren(event).firstOrNull() ?: return
        event = AnActionEvent.createFromAnAction(action, mouseEvent, ActionPlaces.UNKNOWN, DataManager.getInstance().getDataContext(this))
      }
      action.actionPerformed(event)
    }
}
