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

import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke

fun JComponent.registerKeyAction(action: () -> Unit, keyStroke: KeyStroke, name: String) {
  this.registerKeyAction(action, keyStroke, name, JComponent.WHEN_FOCUSED)
}

fun JComponent.registerKeyAction(action: () -> Unit, keyStroke: KeyStroke, name: String, condition: Int) {
  val actionObject = object: AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      action()
    }
  }
  this.getInputMap(condition).put(keyStroke, name)
  this.actionMap.put(name, actionObject)
}
