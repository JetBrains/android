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
package com.android.tools.idea.gradle.structure.configurables.variables

import com.intellij.openapi.project.Project
import com.intellij.ui.TextFieldWithAutoCompletion
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

/**
 * TextBox to be used whenever editing a field that requires knowledge of variables.
 * It enables autocompletion with variable names.
 */
class VariableAwareTextBox(project: Project) : TextFieldWithAutoCompletion<String>(project, object : TextFieldWithAutoCompletion.StringsCompletionProvider(null, null) {
  override fun getPrefix(text: String, offset: Int): String? {
    val origin: Int
    if (text.startsWith("\"")) {
      origin = text.lastIndexOf('$', offset - 1)
      if (origin == -1) {
        return null
      }
    }
    else {
      origin = text.lastIndexOf(' ', offset - 1)
    }
    return text.substring(origin + 1, offset)
  }
}, true, null) {

  private val textListeners = ArrayList<ActionListener>()

  fun addTextListener(listener: ActionListener) = textListeners.add(listener)

  override fun processKeyBinding(ks: KeyStroke?, e: KeyEvent?, condition: Int, pressed: Boolean): Boolean {
    if (e?.keyCode == KeyEvent.VK_ENTER) {
      val event = ActionEvent(this, ActionEvent.ACTION_PERFORMED, null)
      for (listener in textListeners) {
        listener.actionPerformed(event)
      }
      return true
    }
    return super.processKeyBinding(ks, e, condition, pressed)
  }
}