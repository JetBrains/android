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
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProcessListModel(val profilers: StudioProfilers, private val resetTaskSelection: () -> Unit) : AspectObserver() {
  private val _deviceToProcesses = MutableStateFlow(mapOf<Common.Device, List<Common.Process>>())
  val deviceToProcesses = _deviceToProcesses.asStateFlow()

  private val _deviceList = MutableStateFlow(listOf<Common.Device>())
  val deviceList = _deviceList.asStateFlow()
  private val _selectedDevice = MutableStateFlow(Common.Device.getDefaultInstance())
  val selectedDevice = _selectedDevice.asStateFlow()
  private val _selectedProcess = MutableStateFlow(Common.Process.getDefaultInstance())
  val selectedProcess = _selectedProcess.asStateFlow()

  private var preferredProcessName: String? = null

  init {
    profilers.addDependency(this)
      .onChange(ProfilerAspect.PROCESSES) { deviceToProcessesUpdated() }
      .onChange(ProfilerAspect.PREFERRED_PROCESS) { preferredProcessUpdated() }
  }

  fun getSelectedDeviceProcesses() = _deviceToProcesses.value.getOrDefault(_selectedDevice.value, listOf())

  private fun deviceToProcessesUpdated() {
    val newDeviceToProcesses = mutableMapOf<Common.Device, List<Common.Process>>()
    profilers.deviceProcessMap.forEach { (device, processes) ->
      if (device.state != Common.Device.State.ONLINE) {
        return
      }
      val newProcesses = mutableListOf<Common.Process>()
      for (process in processes) {
        if (process.state == Common.Process.State.ALIVE) {
          newProcesses.add(process)
        }
      }

      newDeviceToProcesses[device] = newProcesses
    }

    _deviceToProcesses.value = newDeviceToProcesses
    _deviceList.value = newDeviceToProcesses.keys.toList()
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
    if (!isDeviceSelected()) {
      return
    }

    // The following collection of indices (sorted by a custom comparator) of device process names that have the same substring up to the
    // first ":" instance as the preferred process name. These are the device processes that will be brought to the top of the device
    // process list and are considered to be the "preferred processes".
    val preferredProcessesWithIndicesSorted = if (!preferredProcessName.isNullOrBlank()) {
      getSelectedDeviceProcesses().withIndex().filter { (_, value) ->
        value.name.split(':').first() == preferredProcessName!!.split(':').first()
      }.sortedWith { a, b ->
        // The following comparator gives priority to a device process if the process name is equal to the preferred process name.
        // Otherwise, it uses a regular lexicographic comparison.
        val processNameA = a.value.name
        val processNameB = b.value.name
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

    val reorderedProcessList = mutableListOf<Common.Process>()
    // Populate the new, reordered list with the non-preferred processes and sort it lexicographically.
    reorderedProcessList.addAll(
      getSelectedDeviceProcesses().withIndex().filter { !preferredProcessesWithIndicesSorted.contains(it.index) }.map { it.value })
    reorderedProcessList.sortBy { it.name }
    // Add the preferred processes to the top of the new, reordered list.
    reorderedProcessList.addAll(0, preferredProcessesWithIndicesSorted.map { getSelectedDeviceProcesses()[it] })
    // Create and set new mapping of devices to sorted processes.
    val newDeviceToProcesses = mutableMapOf<Common.Device, List<Common.Process>>()
    newDeviceToProcesses.putAll(_deviceToProcesses.value)
    newDeviceToProcesses[_selectedDevice.value] = reorderedProcessList
    _deviceToProcesses.value = newDeviceToProcesses
  }

  fun onDeviceSelection(newDevice: Common.Device) {
    resetTaskSelection()
    _selectedDevice.value = newDevice
    // Force reordering now that device is selected. This makes sure the process list is reordered correctly using the preferred process
    // in the case the preferred process was set before a device selection was made.
    reorderProcessList()
  }

  fun onProcessSelection(newProcess: Common.Process) {
    resetTaskSelection()
    _selectedProcess.value = newProcess
  }

  private fun preferredProcessUpdated() {
    preferredProcessName = profilers.preferredProcessName
    // Force update of process list ordering.
    reorderProcessList()
  }

  @VisibleForTesting
  fun getPreferredProcessName() = preferredProcessName

  private fun isDeviceSelected() = _selectedDevice.value != Common.Device.getDefaultInstance()
}