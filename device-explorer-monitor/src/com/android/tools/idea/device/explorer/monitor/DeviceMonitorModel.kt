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
package com.android.tools.idea.device.explorer.monitor

import com.android.adblib.ConnectedDevice
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.backup.BackupManager
import com.android.tools.idea.device.explorer.common.DeviceExplorerSettings
import com.android.tools.idea.device.explorer.monitor.processes.DeviceProcessService
import com.android.tools.idea.device.explorer.monitor.processes.ProcessInfo
import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorTableModel
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import kotlinx.coroutines.flow.MutableStateFlow

@UiThread
class DeviceMonitorModel @NonInjectable constructor(
  private val processService: DeviceProcessService,
  private val packageNamesProvider: ProjectApplicationIdsProvider) {
  private val logger = thisLogger()
  private var activeDevice: ConnectedDevice? = null
  private var allProcesses: List<ProcessInfo> = listOf()
  val tableModel = DeviceMonitorTableModel()
  val isPackageFilterActive = MutableStateFlow(DeviceExplorerSettings.getInstance().isPackageFilterActive)
  val isApplicationIdsEmpty = MutableStateFlow(true)

  constructor(project: Project, processService: DeviceProcessService) : this(processService,
                                                                             ProjectApplicationIdsProvider.getInstance(project))

  fun setPackageFilter(isActive: Boolean) {
    if (isPackageFilterActive.value != isActive) {
      isPackageFilterActive.value = isActive
      refreshCurrentProcessList()
    }
  }

  fun projectApplicationIdListChanged() {
    isApplicationIdsEmpty.value = packageNamesProvider.getPackageNames().isEmpty()
    refreshCurrentProcessList()
  }

  fun setAllProcesses(allProcesses: List<ProcessInfo>) {
    this.allProcesses = allProcesses
    refreshCurrentProcessList()
  }

  fun setActiveDevice(connectedDevice: ConnectedDevice?) {
    activeDevice = connectedDevice
  }

  private fun refreshCurrentProcessList() {
    if (allProcesses.isEmpty()) {
      tableModel.clearProcesses()
    } else {
      logger.debug("$activeDevice: Process list updated to ${allProcesses.size} processes")
      tableModel.updateProcessRows(filterProcessList(allProcesses))
    }
  }

  suspend fun killNodesInvoked(rows: IntArray) {
    invokeOnProcessInfo(rows) { processInfo ->
      activeDevice?.let {
        processService.killProcess(processInfo, it)
      }
    }
  }

  suspend fun forceStopNodesInvoked(rows: IntArray) {
    invokeOnProcessInfo(rows) { processInfo ->
      activeDevice?.let {
        processService.forceStopProcess(processInfo, it)
      }
    }
  }

  suspend fun debugNodesInvoked(project: Project, rows: IntArray) {
    invokeOnProcessInfo(rows) { processInfo ->
      activeDevice?.let {
        processService.debugProcess(project, processInfo, it)
      }
    }
  }

  suspend fun backupApplication(project: Project, rows: IntArray) {
    val adbDevice = activeDevice ?: return
    assert(rows.size == 1)
    val processInfo = tableModel.getValueForRow(rows.first())
    processService.backupApplication(project, processInfo, adbDevice)
  }

  fun restoreApplication(project: Project, rows: IntArray) {
    val adbDevice = activeDevice ?: return
    assert(rows.size == 1)
    val backupFile = BackupManager.getInstance(project).chooseRestoreFile() ?: return

    processService.restoreApplication(project, adbDevice, backupFile)
  }

  private suspend fun invokeOnProcessInfo(rows: IntArray, block: suspend (ProcessInfo) -> Unit) {
    rows.forEach { row ->
      val processInfo = tableModel.getValueForRow(row)
      block(processInfo)
    }
  }

  private fun filterProcessList(list: List<ProcessInfo>): List<ProcessInfo> {
    if (!isPackageFilterActive.value || isApplicationIdsEmpty.value) {
      return list
    }

    val filteredList = mutableListOf<ProcessInfo>()
    val projectPackages = packageNamesProvider.getPackageNames()
    for (process in list) {
      val packageName = process.packageName
      if (projectPackages.contains(packageName)) {
        filteredList.add(process)
      }
    }

    return filteredList
  }
}