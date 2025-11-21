// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.common.surface

import com.android.tools.idea.ui.designer.EditorDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.keymap.KeymapUtil
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * Shortcuts for the [EditorDesignSurface].
 *
 * @param keyCode the [KeyEvent] code for the shortcut.
 * @param keyChar KeyChar for the shortcut. This is optional and used to register the the shortcut
 *   when the key code does not correspond to the key char (for example, the '+' in on the KeyCode
 *   of '='. This is necessary for the Intellij ActionButton which uses the key code to display the
 *   character in the tooltip and not the key char.
 *
 * TODO (b/149212539): Register these shortcuts to plugin xml file to support custom keymap. Then
 * remove this class.
 *
 * @see KeyStroke
 * @See KeyEvent
 */
enum class DesignSurfaceShortcut(val keyCode: Int, private val keyChar: Char? = null) {
  PAN(KeyEvent.VK_SPACE);

  private val shortcutSet: ShortcutSet by lazy { createShortcutSet() }

  private fun createShortcutSet(): ShortcutSet {
    val shortcuts = mutableListOf(KeyboardShortcut(KeyStroke.getKeyStroke(keyCode, 0), null))
    if (keyChar != null) {
      shortcuts += KeyboardShortcut(KeyStroke.getKeyStroke(keyChar), null)
    }
    return CustomShortcutSet(*shortcuts.toTypedArray())
  }

  /**
   * Register the shortcut on the provided action within the provided [component] and return the
   * action.
   */
  fun registerForAction(
    shortcutAction: AnAction,
    component: JComponent,
    parentDisposable: Disposable,
  ): AnAction {
    shortcutAction.registerCustomShortcutSet(shortcutSet, component, parentDisposable)
    return shortcutAction
  }

  /**
   * Register this shortcut on [shortcutAction] within the provided [component] and display the
   * shortcut hint in the description of [visibleAction]. This is useful if the action for which the
   * shortcut is registered is in a submenu.
   *
   * @return visibleAction.
   */
  fun registerForHiddenAction(
    visibleAction: AnAction,
    shortcutAction: AnAction,
    component: JComponent,
    parentDisposable: Disposable,
  ): AnAction {
    shortcutAction.registerCustomShortcutSet(shortcutSet, component, parentDisposable)
    val presentation = visibleAction.templatePresentation
    presentation.description =
      presentation.description + " (" + KeymapUtil.getShortcutsText(shortcutSet.shortcuts) + ")"
    return visibleAction
  }
}
