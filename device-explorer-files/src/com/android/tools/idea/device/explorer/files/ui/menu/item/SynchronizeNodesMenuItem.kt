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

class SynchronizeNodesMenuItem(listener: DeviceFileExplorerActionListener) : TreeMenuItem(listener) {
  override fun getText(nodes: List<DeviceFileEntryNode>): String = "Synchronize"

  override val icon: Icon
    get() = AllIcons.Actions.Refresh

  override val shortcutId: String
    get() = // Re-use existing shortcut, see platform/platform-resources/src/keymaps/$default.xml
      "Refresh"

  override fun isVisible(node: DeviceFileEntryNode): Boolean = true

  override fun run(nodes: List<DeviceFileEntryNode>) {
    listener.synchronizeNodes(nodes)
  }
}