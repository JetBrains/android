/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files.ui.menu.item

import com.android.tools.idea.device.explorer.files.DeviceFileEntryNode
import com.android.tools.idea.device.explorer.files.ui.DeviceFileExplorerActionListener
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import icons.StudioIcons
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.KeyStroke

class SaveAsMenuItem(
  listener: DeviceFileExplorerActionListener,
  private val context: MenuContext
) : TreeMenuItem(listener) {
  override fun getText(nodes: List<DeviceFileEntryNode>): String =
    if (nodes.size > 1) "Save To..." else "Save As..."

  override val icon: Icon
    get() = StudioIcons.LayoutEditor.Extras.PALETTE_DOWNLOAD

  override val shortcuts: Array<Shortcut?>
    get() = arrayOf(
      KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK), null),
      KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK), null))

  override val isVisible: Boolean
    get() =
      if (context == MenuContext.Toolbar) true else super.isVisible

  override fun run(nodes: List<DeviceFileEntryNode>) {
    listener.saveNodesAs(nodes)
  }
}