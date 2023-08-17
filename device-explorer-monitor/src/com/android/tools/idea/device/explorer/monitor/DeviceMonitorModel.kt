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

import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.IDevice
import com.android.tools.idea.device.explorer.common.DeviceExplorerSettings
import com.android.tools.idea.device.explorer.monitor.adbimpl.AdbDevice
import com.android.tools.idea.device.explorer.monitor.processes.DeviceProcessService
import com.android.tools.idea.device.explorer.monitor.processes.ProcessInfo
import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorTableModel
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@UiThread
class DeviceMonitorModel @NonInjectable constructor(
  private val processService: DeviceProcessService,
  private val packageNamesProvider: ProjectApplicationIdsProvider) {
  private var activeDevice: AdbDevice? = null
  private val activeDeviceMutex = Mutex()
  val tableModel = DeviceMonitorTableModel()
  val isPackageFilterActive = MutableStateFlow(DeviceExplorerSettings.getInstance().isPackageFilterActive)
  val isApplicationIdsEmpty = MutableStateFlow(true)

  constructor(project: Project, processService: DeviceProcessService) : this(processService, ProjectApplicationIdsProvider.getInstance(project))

  suspend fun setPackageFilter(isActive: Boolean) {
    if (isPackageFilterActive.value != isActive) {
      isPackageFilterActive.value = isActive
      refreshCurrentProcessList()
    }
  }

  suspend fun projectApplicationIdListChanged() {
    isApplicationIdsEmpty.value = packageNamesProvider.getPackageNames().isEmpty()
    refreshCurrentProcessList()
  }

  suspend fun activeDeviceChanged(device: IDevice?) {
    if (device != null) {
      if (activeDevice?.device != device) {
        activeDeviceMutex.withLock {
          activeDevice = AdbDevice(device)
        }
        refreshCurrentDeviceProcessList()
      }
    } else {
      activeDeviceMutex.withLock {
        activeDevice = null
      }
      tableModel.clearProcesses()
    }
  }

  suspend fun refreshProcessListForDevice(device: IDevice) {
    if (activeDevice?.device == device) {
      refreshCurrentProcessList()
    }
  }

  suspend fun refreshCurrentProcessList() {
    refreshCurrentDeviceProcessList()
  }

  suspend fun killNodesInvoked(rows: IntArray) {
    invokeOnProcessInfo(rows) { processInfo ->
      activeDevice?.let {
        processService.killProcess(processInfo, it.device)
      }
    }
  }

  suspend fun forceStopNodesInvoked(rows: IntArray) {
    invokeOnProcessInfo(rows) { processInfo ->
      activeDevice?.let {
        processService.forceStopProcess(processInfo, it.device)
      }
    }
  }

  suspend fun debugNodesInvoked(project: Project, rows: IntArray) {
    invokeOnProcessInfo(rows) { processInfo ->
      activeDevice?.let {
        processService.debugProcess(project, processInfo, it.device)
      }
    }
  }

  private suspend fun invokeOnProcessInfo(rows: IntArray, block: suspend (ProcessInfo) -> Unit) {
    rows.forEach { row ->
      val processInfo = tableModel.getValueForRow(row)
      block(processInfo)
    }
  }

  private suspend fun refreshCurrentDeviceProcessList() {
    activeDeviceMutex.withLock {
      activeDevice?.let {
        val processList = filterProcessList(processService.fetchProcessList(it))
        thisLogger().debug("$it: Process list updated to ${processList.size} processes")
        tableModel.updateProcessRows(processList)
      }
    }
  }

  private fun filterProcessList(list: List<ProcessInfo>): List<ProcessInfo> {
    if (!isPackageFilterActive.value || isApplicationIdsEmpty.value) {
      return list
    }

    val filteredList = mutableListOf<ProcessInfo>()
    val projectPackages = packageNamesProvider.getPackageNames()
    for (process in list) {
      if (projectPackages.contains(process.packageName)) {
        filteredList.add(process)
      }
    }

    return filteredList
  }
}