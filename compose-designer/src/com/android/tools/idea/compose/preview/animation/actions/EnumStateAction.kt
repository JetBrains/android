/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation.actions

import com.android.tools.idea.compose.preview.message
import com.intellij.ide.ui.laf.darcula.ui.ComboBoxButtonUI
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import javax.swing.JComponent
import javax.swing.plaf.ComponentUI

/** A comboBox action to select one state from the list of predefined states. */
class EnumStateAction(private val callback: () -> Unit = {}) :
  ComboBoxAction(), CustomComponentAction {

  /** Available states to select from. */
  var states: Set<Any> = emptySet()

  val stateHashCode: Int
    get() = currentState.hashCode()

  var currentState: Any? = null

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.text =
      currentState?.toString() ?: message("animation.inspector.states.combobox.placeholder.message")
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
    super<ComboBoxAction>.createCustomComponent(presentation, place).apply {
      this.components.forEach { it.isFocusable = true }
    }

  override fun createComboBoxButton(presentation: Presentation): ComboBoxButton {
    return object : ComboBoxButton(presentation) {
      private var componentUI: ComponentUI? = null

      override fun updateUI() {
        super.updateUI()
        if (componentUI == null) componentUI = ComboBoxButtonUI.createUI(this)
        setUI(componentUI)
      }
    }
  }

  override fun createPopupActionGroup(button: JComponent?) =
    DefaultActionGroup(states.map { StateAction(it) })

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private inner class StateAction(val state: Any) : AnAction(state.toString()) {
    override fun actionPerformed(e: AnActionEvent) {
      currentState = state
      callback()
    }
  }
}
