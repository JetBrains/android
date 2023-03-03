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
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorViewListener
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.DebugMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.ForceStopMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.KillMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.MenuContext
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.RefreshMenuItem
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.util.function.Consumer
import javax.swing.JComponent

class DeviceMonitorViewImpl(
  model: DeviceMonitorModel,
  private val table: JBTable = ProcessListTableBuilder().build(model.tableModel)
): DeviceMonitorView, DeviceMonitorActionsListener {

  private val panel = DeviceMonitorPanel()
  private val listeners = mutableListOf<DeviceMonitorViewListener>()

  override val panelComponent: JComponent
    get() = panel.component

  override fun setup() {
    setUpTable()
    createTreePopupMenu()
    createToolbar()
  }

  override fun addListener(listener: DeviceMonitorViewListener) {
    listeners.add(listener)
  }
  override fun removeListener(listener: DeviceMonitorViewListener) {
    listeners.remove(listener)
  }

  override val numOfSelectedNodes: Int
    get() {
      return table.selectedRowCount
    }

  override fun refreshNodes() {
    listeners.forEach(Consumer { it.refreshInvoked() })
  }

  override fun killNodes() {
    listeners.forEach(Consumer { it.killNodesInvoked(getModelRows(table.selectedRows)) })
  }

  override fun forceStopNodes() {
    listeners.forEach(Consumer { it.forceStopNodesInvoked(getModelRows(table.selectedRows)) })
  }

  override fun debugNodes() {
    listeners.forEach(Consumer { it.debugNodes(getModelRows(table.selectedRows)) })
  }

  private fun setUpTable() {
    panel.processTablePane.viewport.add(table)
  }

  private fun createTreePopupMenu() {
    ComponentPopupMenu(table).apply {
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
      targetComponent = panel.processTablePane
    }
    panel.toolbar.add(actionToolbar.component, BorderLayout.WEST)
  }

  private fun getModelRows(viewRows: IntArray): IntArray =
    IntArray(viewRows.size) { index ->
      table.convertRowIndexToModel(viewRows[index])
    }
}