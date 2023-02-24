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
import java.util.stream.Collectors
import javax.swing.Icon

/**
 * A popup menu item that works for both single and multi-element selections.
 */
abstract class TreeMenuItem(val listener: DeviceFileExplorerActionListener) : PopupMenuItem {
  override val text: String
    get() {
      var nodes = listener.selectedNodes
      if (nodes == null) {
        nodes = emptyList()
      }
      return getText(nodes)
    }

  override val icon: Icon?
    get() = null

  override val isEnabled: Boolean
    get() {
      val nodes = listener.selectedNodes ?: return false
      return isEnabled(nodes)
    }

  override val isVisible: Boolean
    get() {
      val nodes = listener.selectedNodes ?: return false
      return isVisible(nodes)
    }

  override fun run() {
    var nodes = listener.selectedNodes ?: return
    nodes = nodes.stream().filter { node: DeviceFileEntryNode ->
      this.isEnabled(node)
    }.collect(Collectors.toList())
    if (nodes.isNotEmpty()) {
      run(nodes)
    }
  }

  abstract fun getText(nodes: List<DeviceFileEntryNode>): String

  open fun isEnabled(nodes: List<DeviceFileEntryNode>): Boolean {
    return nodes.stream().anyMatch { node: DeviceFileEntryNode -> this.isEnabled(node) }
  }

  open fun isVisible(nodes: List<DeviceFileEntryNode>): Boolean {
    return nodes.stream().anyMatch { node: DeviceFileEntryNode -> this.isVisible(node) }
  }

  open fun isVisible(node: DeviceFileEntryNode): Boolean {
    return true
  }

  open fun isEnabled(node: DeviceFileEntryNode): Boolean {
    return isVisible(node)
  }

  abstract fun run(nodes: List<DeviceFileEntryNode>)
}