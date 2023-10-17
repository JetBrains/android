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
package com.android.tools.profilers.taskbased.home.selections.deviceprocesses

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.ProfilerAspect
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.SupportLevel
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProcessListModel(val profilers: StudioProfilers) : AspectObserver() {
  private val _deviceProcessList = MutableStateFlow(listOf<DeviceProcess>())
  val deviceProcessList = _deviceProcessList.asStateFlow()

  private val _selectedDeviceProcess = MutableStateFlow(
    DeviceProcess(Common.Device.getDefaultInstance(), Common.Process.getDefaultInstance()))
  val selectedDeviceProcess = _selectedDeviceProcess.asStateFlow()

  private var preferredProcessName: String? = null

  data class DeviceProcess(
    val device: Common.Device,
    val process: Common.Process
  )

  init {
    profilers.addDependency(this)
      .onChange(ProfilerAspect.PROCESSES) { deviceProcessListUpdated() }
      .onChange(ProfilerAspect.PREFERRED_PROCESS) { preferredProcessUpdated() }
  }

  private fun deviceProcessListUpdated() {
    val newDeviceProcessList = mutableListOf<DeviceProcess>()
    profilers.deviceProcessMap.map { (device, deviceProcesses) ->
      if (device.state != Common.Device.State.ONLINE) {
        return@map
      }
      for (process in deviceProcesses) {
        if (process.state == Common.Process.State.ALIVE) {
          newDeviceProcessList.add(DeviceProcess(device = device, process = process))
        }
      }
    }

    _deviceProcessList.value = newDeviceProcessList
    reorderProcessList()
  }

  /**
   * Reorders the device process list by process name in increasing lexicographic order. One exception to this sort/order is that the
   * preferred process will always be the first entry in the list.
   */
  private fun reorderProcessList() {
    val reorderedProcessList = mutableListOf<DeviceProcess>()
    reorderedProcessList.addAll(_deviceProcessList.value)
    reorderedProcessList.sortBy { it.process.name }

    // Move the preferred process, if it exists, to the front of the device process list.
    if (!preferredProcessName.isNullOrBlank()){
      val preferredProcessIdx = reorderedProcessList.indexOfFirst { it.process.name ==  preferredProcessName}
      if (preferredProcessIdx != -1) {
        val preferredProcess = reorderedProcessList.removeAt(preferredProcessIdx)
        reorderedProcessList.add(0, preferredProcess)
      }
    }

    _deviceProcessList.value = reorderedProcessList
  }

  fun onDeviceProcessSelection(newDeviceProcess: DeviceProcess) {
    _selectedDeviceProcess.value = newDeviceProcess
  }

  private fun preferredProcessUpdated() {
    preferredProcessName = profilers.preferredProcessName
    // Force update of process list ordering.
    reorderProcessList()
  }

  @VisibleForTesting
  fun getPreferredProcessName() = preferredProcessName
}