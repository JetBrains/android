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

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke

fun JComponent.registerKeyAction(action: () -> Unit, keyStroke: KeyStroke, name: String, condition: Int = JComponent.WHEN_FOCUSED) {
  val actionObject = object: AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      action()
    }
  }
  this.getInputMap(condition).put(keyStroke, name)
  this.actionMap.put(name, actionObject)
}

fun JComponent.registerKeyAction(action: AnAction, keyStroke: KeyStroke, name: String, condition: Int = JComponent.WHEN_FOCUSED) {
  this.getInputMap(condition).put(keyStroke, name)
  this.actionMap.put(name, object : AbstractAction() {
    override fun actionPerformed(event: ActionEvent) {
      val dataContext = DataManager.getInstance().getDataContext(this@registerKeyAction)
      val inputEvent = event.source as? InputEvent
      action.actionPerformed(AnActionEvent.createFromAnAction(action, inputEvent, ToolWindowContentUi.POPUP_PLACE, dataContext))
    }
  })
}
