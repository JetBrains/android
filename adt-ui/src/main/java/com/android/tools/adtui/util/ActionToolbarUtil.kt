/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.adtui.util

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.util.ui.accessibility.ScreenReader
import java.awt.Component
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel

/** Use the extension methods below instead of this object. */
object ActionToolbarUtil {
  /** See [ActionToolbar.findActionButton]. */
  @JvmStatic
  fun findActionButton(toolbar: ActionToolbar, action: AnAction): ActionButton? = toolbar.findActionButton(action)

  /** See [ActionToolbar.makeNavigable]. */
  @JvmStatic
  fun makeToolbarNavigable(toolbar: ActionToolbar) {
    toolbar.makeNavigable()
  }
}

/**
 * Finds the toolbar button corresponding to [action].
 */
fun ActionToolbar.findActionButton(action: AnAction): ActionButton? =
  component.components.find { (it as? ActionButton)?.action == action } as ActionButton?

/**
 * Makes it possible to navigate the actions buttons from the keyboard.
 *
 * The action buttons are not focusable when `ScreenReader.isActive()` is false,
 * This method makes the buttons of the toolbar focusable unconditionally.
 */
fun ActionToolbar.makeNavigable() {
  if (!ScreenReader.isActive()) {
    for (child in component.components) {
      child.makeActionNavigable()
    }

    component.addContainerListener(object : ContainerAdapter() {
      override fun componentAdded(event: ContainerEvent) {
        event.child.makeActionNavigable()
      }
    })
  }
}

private fun Component.makeActionNavigable() {
  if (this is ActionButton || this is JCheckBox) {
    isFocusable = true
  }
  else if (this is JPanel && components.firstOrNull() is JButton) {
    // A ComboBoxAction creates a ComboBoxButton wrapped in a JPanel:
    components.firstOrNull()?.isFocusable = true
  }
}

