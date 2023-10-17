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
   * preferred processes will always be the first entries in the list.
   *
   * Preferred processes are defined to be processes with names that have the same content leading up to the first colon (':') character
   * in the process name as the preferred process name. The preferred process names themselves are sorted lexicographically, with the
   * process whose name fully matches the preferred process name being at the top of the list.
   */
  private fun reorderProcessList() {
    // The following collection of indices (sorted by a custom comparator) of device process names that have the same substring up to the
    // first ":" instance as the preferred process name. These are the device processes that will be brought to the top of the device
    // process list and are considered to be the "preferred processes".
    val preferredProcessesWithIndicesSorted = if (!preferredProcessName.isNullOrBlank()) {
      _deviceProcessList.value.withIndex().filter { (_, value) ->
        value.process.name.split(':').first() == preferredProcessName!!.split(':').first()
      }.sortedWith { a, b ->
        // The following comparator gives priority to a device process if the process name is equal to the preferred process name.
        // Otherwise, it uses a regular lexicographic comparison.
        val processNameA = a.value.process.name
        val processNameB = b.value.process.name
        when {
          processNameA == preferredProcessName && processNameB != preferredProcessName -> -1
          processNameA != preferredProcessName && processNameB == preferredProcessName -> 1
          else -> processNameA.compareTo(processNameB)
        }
      }.map { it.index }

    }
    // If there is no preferred process name, then we should not prioritize any processes, hence the empty list.
    else {
      listOf()
    }

    val reorderedProcessList = mutableListOf<DeviceProcess>()
    // Populate the new, reordered list with the non-preferred processes and sort it lexicographically.
    reorderedProcessList.addAll(
      _deviceProcessList.value.withIndex().filter { !preferredProcessesWithIndicesSorted.contains(it.index) }.map { it.value })
    reorderedProcessList.sortBy { it.process.name }
    // Add the preferred processes to the top of the new, reordered list.
    reorderedProcessList.addAll(0, preferredProcessesWithIndicesSorted.map { _deviceProcessList.value[it] })
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