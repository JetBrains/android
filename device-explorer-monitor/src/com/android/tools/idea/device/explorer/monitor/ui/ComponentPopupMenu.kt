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
package com.android.tools.idea.device.explorer.monitor.ui

import com.android.tools.idea.device.explorer.monitor.ui.menu.item.PopupMenuItem
import javax.swing.JComponent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.PopupHandler
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.util.text.StringUtil

/**
 * Utility class for building and installing a popup menu for a given [JComponent].
 */
class ComponentPopupMenu(private val myComponent: JComponent) {
  private val myGroup = DefaultActionGroup()

  fun install() {
    PopupHandler.installPopupMenu(myComponent, myGroup, "Device Monitor Toolbar")
  }

  fun addSeparator() {
    myGroup.addSeparator()
  }

  fun addItem(popupMenuItem: PopupMenuItem) {
    val action = popupMenuItem.action
    val shortcutId = popupMenuItem.shortcutId
    if (!StringUtil.isEmpty(shortcutId)) {
      val active = KeymapManager.getInstance().activeKeymap
      val shortcuts = active.getShortcuts(shortcutId)
      action.registerCustomShortcutSet(CustomShortcutSet(*shortcuts), myComponent)
    }
    val shortcuts = popupMenuItem.shortcuts
    if (shortcuts != null && shortcuts.isNotEmpty()) {
      action.registerCustomShortcutSet(CustomShortcutSet(*shortcuts), myComponent)
    }
    myGroup.add(action)
  }
}