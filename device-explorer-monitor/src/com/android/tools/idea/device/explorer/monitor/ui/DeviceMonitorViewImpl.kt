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

import com.android.tools.idea.device.explorer.monitor.DeviceMonitorModel
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorModelListener
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorViewListener
import com.android.tools.idea.device.explorer.monitor.ProcessTreeNode
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.DebugMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.ForceStopMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.KillMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.MenuContext
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.RefreshMenuItem
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.TreePath

class DeviceMonitorViewImpl(model: DeviceMonitorModel): DeviceMonitorView, DeviceMonitorActionsListener {
  private val panel = DeviceMonitorPanel()
  private val listeners = mutableListOf<DeviceMonitorViewListener>()
  private val modelListener = ModelListener()

  init {
    model.addListener(modelListener)
  }

  override val panelComponent: JComponent
    get() = panel.component

  override fun setup() {
    createTreePopupMenu()
    createToolbar()
  }

  override fun addListener(listener: DeviceMonitorViewListener) {
    listeners.add(listener)
  }
  override fun removeListener(listener: DeviceMonitorViewListener) {
    listeners.remove(listener)
  }

  override val selectedNodes: List<ProcessTreeNode>?
    get() {
      val paths = panel.tree.selectionPaths ?: return null
      val nodes = paths.mapNotNull { path -> ProcessTreeNode.fromNode(path.lastPathComponent) }.toList()
      return nodes.ifEmpty { null }
    }

  override fun refreshNodes(treeNodes: List<ProcessTreeNode>) {
    listeners.forEach(Consumer { it.refreshInvoked() })
  }

  override fun killNodes(treeNodes: List<ProcessTreeNode>) {
    listeners.forEach(Consumer { it.killNodesInvoked(treeNodes) })
  }

  override fun forceStopNodes(treeNodes: List<ProcessTreeNode>) {
    listeners.forEach(Consumer { it.forceStopNodesInvoked(treeNodes) })
  }

  override fun debugNodes(treeNodes: List<ProcessTreeNode>) {
    listeners.forEach(Consumer { it.debugNodes(treeNodes) })
  }

  private fun createTreePopupMenu() {
    ComponentPopupMenu(panel.tree).apply {
      addItem(KillMenuItem(this@DeviceMonitorViewImpl, MenuContext.Popup))
      addItem(ForceStopMenuItem(this@DeviceMonitorViewImpl, MenuContext.Popup))
      install()
    }
  }

  private fun createToolbar() {
    createToolbarSubSection(DefaultActionGroup().apply {
      add(KillMenuItem(this@DeviceMonitorViewImpl, MenuContext.Toolbar).action)
      add(ForceStopMenuItem(this@DeviceMonitorViewImpl, MenuContext.Toolbar).action)
      add(DebugMenuItem(this@DeviceMonitorViewImpl, MenuContext.Toolbar).action)
      add(RefreshMenuItem(this@DeviceMonitorViewImpl).action) }
    )
  }

  private fun createToolbarSubSection(group: DefaultActionGroup) {
    val actionManager = ActionManager.getInstance()
    val actionToolbar = actionManager.createActionToolbar("Device Monitor Toolbar", group, true).apply {
      targetComponent = panel.tree
    }
    panel.toolbar.add(actionToolbar.component, BorderLayout.WEST)
  }

  inner class ModelListener : DeviceMonitorModelListener {
    override fun treeModelChanged(newTreeModel: DefaultTreeModel?, newTreeSelectionModel: DefaultTreeSelectionModel?) {
      val tree = panel.tree
      tree.model = newTreeModel
      tree.selectionModel = newTreeSelectionModel
      if (newTreeModel != null) {
        val rootNode = ProcessTreeNode.fromNode(newTreeModel.root)
        if (rootNode != null) {
          tree.isRootVisible = false
          tree.expandPath(TreePath(rootNode.path))
        }
        else {
          // Show root, since it contains an error message (ErrorNode)
          tree.isRootVisible = true
        }
      }
    }

  }
}