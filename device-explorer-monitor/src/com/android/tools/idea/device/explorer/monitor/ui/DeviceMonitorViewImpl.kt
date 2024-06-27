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
import com.android.tools.idea.device.explorer.monitor.processes.ProcessInfo
import com.android.tools.idea.device.explorer.monitor.ui.ProcessListTableBuilder.Companion.EMPTY_TREE_TEXT
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.BackupMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.DebugMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.ForceStopMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.KillMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.MenuContext
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.PackageFilterMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.RefreshMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.RestoreMenuItem
import com.android.tools.idea.flags.StudioFlags
import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.table.JBTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.JComponent

class DeviceMonitorViewImpl(
  private val project: Project,
  private val model: DeviceMonitorModel,
  private val table: JBTable = ProcessListTableBuilder().build(model.tableModel)
): DeviceMonitorView, DeviceMonitorActionsListener {

  private val panel = DeviceMonitorPanel()
  private val listeners = mutableListOf<DeviceMonitorViewListener>()
  private val packageFilterMenuItem = PackageFilterMenuItem(this@DeviceMonitorViewImpl)

  override val panelComponent: JComponent
    get() = panel.component

  override fun setup() {
    setUpTable()
    createTreePopupMenu()
    createToolbar()
  }

  override fun trackModelChanges(coroutineScope: CoroutineScope) {
    coroutineScope.launch {
      model.isPackageFilterActive.collect {
        packageFilterMenuItem.isActionSelected = it
        table.emptyText.text = if (it) "No results (Some processes hidden due to app ID filter)" else EMPTY_TREE_TEXT
      }
    }

    coroutineScope.launch {
      model.isApplicationIdsEmpty.collect {
        packageFilterMenuItem.shouldBeEnabled = !it
      }
    }
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

  override val selectedProcessInfo: List<ProcessInfo>
    get() {
      val selectedNodes = getModelRows(table.selectedRows)
      val processInfoList = mutableListOf<ProcessInfo>()
      for (selectedNode in selectedNodes) {
        processInfoList.add(model.tableModel.getValueForRow(selectedNode))
      }

      return processInfoList
    }

  override fun refreshNodes() {
    listeners.forEach { it.refreshInvoked() }
  }

  override fun killNodes() {
    listeners.forEach { it.killNodesInvoked(getModelRows(table.selectedRows)) }
    table.clearSelection()
  }

  override fun forceStopNodes() {
    listeners.forEach { it.forceStopNodesInvoked(getModelRows(table.selectedRows)) }
    table.clearSelection()
  }

  override fun debugNodes() {
    listeners.forEach{ it.debugNodes(getModelRows(table.selectedRows)) }
    table.clearSelection()
  }

  override fun packageFilterToggled(isActive: Boolean) {
    listeners.forEach { it.packageFilterToggled(isActive) }
  }

  override fun backupApplication() {
    listeners.forEach { it.backupApplication(table.selectedRows) }
    table.clearSelection()
  }

  override fun restoreApplication() {
    listeners.forEach { it.restoreApplication(table.selectedRows) }
    table.clearSelection()
  }

  private fun setUpTable() {
    panel.processTablePane.viewport.add(table)
  }

  private fun createTreePopupMenu() {
    ComponentPopupMenu(table).apply {
      addItem(KillMenuItem(this@DeviceMonitorViewImpl, MenuContext.Popup))
      addItem(ForceStopMenuItem(this@DeviceMonitorViewImpl, MenuContext.Popup))
      addItem(DebugMenuItem(this@DeviceMonitorViewImpl, MenuContext.Popup, RunManager.getInstance(project)))
      if (StudioFlags.BACKUP_SHOW_ACTIONS_IN_DEVICE_EXPLORER.get()) {
        addItem(BackupMenuItem(this@DeviceMonitorViewImpl, MenuContext.Popup))
        addItem(RestoreMenuItem(this@DeviceMonitorViewImpl, MenuContext.Popup))
      }
      install()
    }
  }

  private fun createToolbar() {
    createToolbarSubSection(DefaultActionGroup().apply {
      add(KillMenuItem(this@DeviceMonitorViewImpl, MenuContext.Toolbar).action)
      add(ForceStopMenuItem(this@DeviceMonitorViewImpl, MenuContext.Toolbar).action)
      add(DebugMenuItem(this@DeviceMonitorViewImpl, MenuContext.Toolbar, RunManager.getInstance(project)).action)
      add(RefreshMenuItem(this@DeviceMonitorViewImpl).action)
      if (StudioFlags.DEVICE_EXPLORER_PROCESSES_PACKAGE_FILTER.get()) add(packageFilterMenuItem.action)
    })
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