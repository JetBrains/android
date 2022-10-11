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
package com.android.tools.idea.device.explorer.monitor.ui.menu.item

import com.android.tools.idea.device.monitor.ui.DeviceMonitorActionsListener
import com.android.tools.idea.device.monitor.ProcessTreeNode
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import javax.swing.Icon

/**
 * A popup menu item that works for both single and multi-element selections.
 */
abstract class TreeMenuItem(protected val listener: DeviceMonitorActionsListener) : PopupMenuItem {
  override val text: String
    get() {
      var nodes = listener.selectedNodes
      if (nodes == null) {
        nodes = emptyList()
      }
      return getText(nodes)
    }

  override val icon: Icon?
    get() {
      return null
    }

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

  override val action: AnAction = object : AnAction(icon) {
    override fun update(e: AnActionEvent) {
      val presentation = e.presentation
      presentation.text = text
      presentation.isEnabled = isEnabled
      presentation.isVisible = isVisible
    }

    override fun actionPerformed(e: AnActionEvent) {
      run()
    }
  }

  override fun run() {
    var nodes = listener.selectedNodes ?: return
    nodes = nodes.filter { node: ProcessTreeNode -> this.isEnabled(node) }.toList()
    if (nodes.isNotEmpty()) {
      run(nodes)
    }
  }

  open fun isEnabled(nodes: List<ProcessTreeNode>): Boolean {
    return nodes.stream().anyMatch { node: ProcessTreeNode -> this.isEnabled(node) }
  }

  open fun isVisible(nodes: List<ProcessTreeNode>): Boolean {
    return nodes.stream().anyMatch { node: ProcessTreeNode -> this.isVisible(node) }
  }

  open fun isVisible(node: ProcessTreeNode): Boolean {
    return true
  }

  open fun isEnabled(node: ProcessTreeNode): Boolean {
    return true
  }

  abstract fun run(nodes: List<ProcessTreeNode>)

  abstract fun getText(nodes: List<ProcessTreeNode>): String
}