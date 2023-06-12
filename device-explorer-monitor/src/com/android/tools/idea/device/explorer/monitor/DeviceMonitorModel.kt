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
import com.android.tools.idea.device.explorer.monitor.adbimpl.AdbDevice
import com.android.tools.idea.device.explorer.monitor.processes.DeviceProcessService
import com.android.tools.idea.device.explorer.monitor.processes.ProcessInfo
import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorTableModel
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

@UiThread
class DeviceMonitorModel(private val processService: DeviceProcessService) {
  private var activeDevice: AdbDevice? = null
  val tableModel = DeviceMonitorTableModel()

  suspend fun activeDeviceChanged(device: IDevice?) {
    if (device != null) {
      if (activeDevice?.device != device) {
        activeDevice = AdbDevice(device).apply {
          refreshProcessList(this)
        }
      }
    } else {
      tableModel.clearProcesses()
    }
  }

  suspend fun refreshProcessListForDevice(device: IDevice) {
    if (activeDevice?.device == device) {
      refreshCurrentProcessList()
    }
  }

  suspend fun refreshCurrentProcessList() {
    refreshProcessList(activeDevice)
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

  private suspend fun refreshProcessList(device: AdbDevice?) {
    if (device != null) {
      val processList = processService.fetchProcessList(device)
      thisLogger().debug("${device}: Process list updated to ${processList.size} processes")
      tableModel.updateProcessRows(processList)
    }
  }
}