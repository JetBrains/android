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
import com.intellij.icons.AllIcons
import javax.swing.Icon

class NewDirectoryMenuItem(
  listener: DeviceFileExplorerActionListener,
  private val context: MenuContext
) : SingleSelectionTreeMenuItem(listener) {
  override fun getText(nodes: List<DeviceFileEntryNode>): String =
    if (context == MenuContext.Toolbar) "New Directory" else "Directory"

  override val icon: Icon
    get() =
      if (context == MenuContext.Toolbar) AllIcons.Actions.NewFolder else AllIcons.Nodes.Folder

  override val isVisible: Boolean
    get() =
      if (context == MenuContext.Toolbar) true else super.isVisible

  override fun isVisible(node: DeviceFileEntryNode): Boolean =
    node.entry.isDirectory || node.isSymbolicLinkToDirectory

  override fun run(node: DeviceFileEntryNode) {
    listener.newDirectory(node)
  }
}