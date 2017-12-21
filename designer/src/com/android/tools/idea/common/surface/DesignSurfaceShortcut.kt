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
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.keymap.KeymapUtil
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * Shortcuts for the [EditorDesignSurface].
 *
 * @param keyCode the [KeyEvent] code for the shortcut.
 * @param keyChar KeyChar for the shortcut.
 * This is optional and used to register the
 * the shortcut when the key code does not correspond to the key char (for example,
 * the '+' in on the KeyCode of '='. This is necessary for the Intellij ActionButton which uses
 * the key code to display the character in the tooltip and not the key char.
 *
 * @see KeyStroke
 * @See KeyEvent
 */
enum class DesignSurfaceShortcut(val keyCode: Int, private val keyChar: Char? = null) {
  ZOOM_IN(KeyEvent.VK_PLUS, '+'),
  ZOOM_OUT(KeyEvent.VK_MINUS, '-'),
  ZOOM_FIT(KeyEvent.VK_0, '0'),
  ZOOM_ACTUAL(KeyEvent.VK_1, '1'),

  TOGGLE_ISSUE_PANEL(KeyEvent.VK_E),
  SWITCH_ORIENTATION(KeyEvent.VK_O),
  NEXT_DEVICE(KeyEvent.VK_D),
  REFRESH_LAYOUT(KeyEvent.VK_R),
  DESIGN_MODE(KeyEvent.VK_B),

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
   * Register the shortcut on the provided action within the provided [component] and return the action.
   */
  fun registerForAction(shortcutAction: AnAction, component: JComponent): AnAction {
    shortcutAction.registerCustomShortcutSet(shortcutSet, component)
    return shortcutAction
  }

  /**
   * Register this shortcut on [shortcutAction] within the provided [component]
   * and display the shortcut hint in the description of [visibleAction]
   *
   * @return visibleAction.
   */
  fun registerForAction(visibleAction: AnAction, shortcutAction: AnAction, component: JComponent): AnAction {
    shortcutAction.registerCustomShortcutSet(shortcutSet, component)
    val presentation = visibleAction.templatePresentation
    presentation.description = presentation.description +
        " (" + KeymapUtil.getShortcutsText(shortcutSet.shortcuts) + ")"
    return visibleAction
  }
}