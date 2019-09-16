/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.util.SystemInfo
import java.awt.Event.CTRL_MASK
import java.awt.Event.META_MASK
import java.awt.Event.SHIFT_MASK
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

private val ACTION_MASK = if (SystemInfo.isMac) META_MASK else CTRL_MASK

private val zoomInShortcuts = listOf(
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, ACTION_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, ACTION_MASK + SHIFT_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, ACTION_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, ACTION_MASK + SHIFT_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, ACTION_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, ACTION_MASK + SHIFT_MASK), null)
)

private val zoomOutShortcuts = listOf(
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, ACTION_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, ACTION_MASK + SHIFT_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_UNDERSCORE, ACTION_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_UNDERSCORE, ACTION_MASK + SHIFT_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, ACTION_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, ACTION_MASK + SHIFT_MASK), null)
)

private val zoomToFitShortcuts = listOf(
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_0, ACTION_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_0, ACTION_MASK + SHIFT_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT_PARENTHESIS, ACTION_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT_PARENTHESIS, ACTION_MASK + SHIFT_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, ACTION_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, ACTION_MASK + SHIFT_MASK), null)
)

private val zoomToActualShortcuts = listOf(
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, ACTION_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, ACTION_MASK + SHIFT_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_DIVIDE, ACTION_MASK), null),
  KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_DIVIDE, ACTION_MASK + SHIFT_MASK), null)
)

enum class ZoomShortcut(shortcuts: List<KeyboardShortcut>) {
  ZOOM_IN(zoomInShortcuts),
  ZOOM_OUT(zoomOutShortcuts),
  ZOOM_FIT(zoomToFitShortcuts),
  ZOOM_ACTUAL(zoomToActualShortcuts);

  private val shortcutSet: ShortcutSet

  init {
    shortcutSet = CustomShortcutSet(*shortcuts.toTypedArray())
  }

  fun registerForAction(shortcutAction: AnAction,
                        component: JComponent,
                        parentDisposable: Disposable
  ): AnAction {
    shortcutAction.registerCustomShortcutSet(shortcutSet, component, parentDisposable)
    return shortcutAction
  }
}