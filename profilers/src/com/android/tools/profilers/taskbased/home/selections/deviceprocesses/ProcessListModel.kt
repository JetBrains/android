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
import com.android.tools.idea.IdeInfo
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.ProfilerAspect
import com.android.tools.profilers.StudioProfilers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.swing.Icon

class ProcessListModel(val profilers: StudioProfilers, private val updateProfilingProcessStartingPoint: () -> Unit) : AspectObserver() {
  private val _deviceToProcesses = MutableStateFlow(mapOf<Common.Device, List<Common.Process>>())
  val deviceToProcesses = _deviceToProcesses.asStateFlow()

  private val _deviceList = MutableStateFlow(listOf<Common.Device>())
  val deviceList = _deviceList.asStateFlow()
  private val _selectedDevice = MutableStateFlow<ProfilerDeviceSelection?>(null)
  val selectedDevice = _selectedDevice.asStateFlow()
  private val _selectedProcess = MutableStateFlow(Common.Process.getDefaultInstance())
  val selectedProcess = _selectedProcess.asStateFlow()
  private val _selectedDevicesCount = MutableStateFlow(0)
  val selectedDevicesCount = _selectedDevicesCount.asStateFlow()
  private var _isPreferredProcessSelected = MutableStateFlow(false)
  val isPreferredProcessSelected = _isPreferredProcessSelected.asStateFlow()
  private var _preferredProcessName = MutableStateFlow<String?>(null)
  val preferredProcessName = _preferredProcessName.asStateFlow()

  init {
    profilers.addDependency(this)
      .onChange(ProfilerAspect.PROCESSES) { deviceToProcessesUpdated() }
      .onChange(ProfilerAspect.PREFERRED_PROCESS_NAME) { preferredProcessUpdated() }
  }

  fun getSelectedDeviceProcesses() = _deviceToProcesses.value.getOrDefault(
    _selectedDevice.value?.device ?: Common.Device.getDefaultInstance(), listOf())

  private fun deviceToProcessesUpdated() {
    val newDeviceToProcesses = mutableMapOf<Common.Device, List<Common.Process>>()
    profilers.deviceProcessMap.forEach { (device, processes) ->
      if (device.state != Common.Device.State.ONLINE) {
        return@forEach
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

    checkForPreferredProcess()

    // In the standalone/game-tool profiler, device selection is made via a dropdown in the profiler UI with only running devices listed.
    // Therefore, if the device selected in the dropdown is disconnected, and thus no longer available in the dropdown, the selection of
    // such device should be removed. This reset logic is not necessary in the case of the regular/non-standalone profiler as the selection
    // is made and read via the main toolbar's device dropdown. This allows for the selection of offline devices, unlike the standalone
    // profiler, making this reset logic unnecessary.
    if (!isSelectedDeviceRunning() && IdeInfo.isGameTool()) {
      resetDeviceSelection()
    }

    // Only the standalone profiler has the device selection-dropdown that can utilize auto-selection.
    if (IdeInfo.isGameTool()) {
      autoSelectDevice()
    }

    updateProcessSelection()
    reorderProcessList()
  }

  /**
   * Checks for the presence of the preferred process, and resets the selection if preferred process is selected and no longer present.
   */
  private fun checkForPreferredProcess() {
    val isPreferredProcessPresent = getSelectedDeviceProcesses().find { it.name == _preferredProcessName.value } != null
    if (!isPreferredProcessPresent && _isPreferredProcessSelected.value) {
      resetProcessSelection()
    }
  }

  /**
   * Resets the process selection if the process is no longer in the updated process list.
   */
  private fun updateProcessSelection() {
    if (selectedProcess.value != Common.Process.getDefaultInstance() && !getSelectedDeviceProcesses().contains(selectedProcess.value)) {
      resetProcessSelection()
    }
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
    val preferredProcessesWithIndicesSorted = if (!_preferredProcessName.value.isNullOrBlank()) {
      getSelectedDeviceProcesses().withIndex().filter { (_, value) ->
        value.name.split(':').first() == _preferredProcessName.value!!.split(':').first()
      }.sortedWith { a, b ->
        // The following comparator gives priority to a device process if the process name is equal to the preferred process name.
        // Otherwise, it uses a regular lexicographic comparison.
        val processNameA = a.value.name
        val processNameB = b.value.name
        when {
          processNameA == _preferredProcessName.value && processNameB != _preferredProcessName.value -> -1
          processNameA != _preferredProcessName.value && processNameB == _preferredProcessName.value -> 1
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
    reorderedProcessList.addAll(getSelectedDeviceProcesses().withIndex().filter {
      !preferredProcessesWithIndicesSorted.contains(it.index) && it.value.state == Common.Process.State.ALIVE
    }.map { it.value })
    reorderedProcessList.sortBy { it.name }
    // Add the preferred processes to the top of the new, reordered list.
    reorderedProcessList.addAll(0, preferredProcessesWithIndicesSorted.map { getSelectedDeviceProcesses()[it] })

    updatePreferredProcessAndSelect(preferredProcessesWithIndicesSorted, reorderedProcessList)

    // Create and set new mapping of devices to sorted processes.
    val newDeviceToProcesses = mutableMapOf<Common.Device, List<Common.Process>>()
    newDeviceToProcesses.putAll(_deviceToProcesses.value)
    newDeviceToProcesses[_selectedDevice.value!!.device] = reorderedProcessList
    _deviceToProcesses.value = newDeviceToProcesses
  }

  /**
   * Inspects the updated process list, and selects the preferred process entry if it is present. If not present, and the preferred process
   * name is available, a fake/dead process entry representing the preferred process is added and selected. This fake/dead process entry
   * will enable the user to perform a startup task from a device where the preferred process has not been launched on yet.
   *
   * TODO(b/326629716): Adapt this method to not always auto-select the preferred process if a non-preferred process is already selected.
   */
  private fun updatePreferredProcessAndSelect(preferredProcessesWithIndicesSorted: List<Int>, reorderedProcessList: MutableList<Common.Process>) {
    // If the preferred process name is present, but the process entry is not present, add a fake/dead process entry to represent it.
    if (!_preferredProcessName.value.isNullOrBlank() && preferredProcessesWithIndicesSorted.isEmpty()) {
      val deadPreferredProcess = Common.Process.newBuilder().setName(_preferredProcessName.value).setState(Common.Process.State.DEAD).build()
      // The preferred process, dead or alive, should always be added to the top.
      reorderedProcessList.add(0, deadPreferredProcess)
      selectPreferredProcess(reorderedProcessList)
    }
    // If a preferred process already exists and is running, and no process selection has been made, the preferred process is selected.
    else if (preferredProcessesWithIndicesSorted.isNotEmpty() &&
             ((_isPreferredProcessSelected.value && _selectedProcess.value.state == Common.Process.State.DEAD) ||
              _selectedProcess.value == Common.Process.getDefaultInstance())) {
      selectPreferredProcess(reorderedProcessList)
    }
  }

  private fun selectPreferredProcess(processList: List<Common.Process>) {
    val preferredProcess = processList.find { it.name == _preferredProcessName.value }
    preferredProcess?.let { onProcessSelection(it) }
  }

  /**
   * Auto-selects a device if there is no device is currently selected and there is only one online device.
   */
  private fun autoSelectDevice() {
    val updatedDevices = _deviceList.value;
    if (!isDeviceSelected() && updatedDevices.size == 1) {
      onDeviceSelection(updatedDevices.first())
    }
  }

  /**
   * Converts a Studio main toolbar device selection to a profiler-level device selection and registers it.
   *
   * There are three selection scenarios covered:
   *
   * 1. User selects a running device in the main toolbar, matching online device found in the transport pipeline:
   *    - Profiler-level selection constructed with device name, marked as running, and Common.Device instance fetched from the pipeline.
   *
   * 2. User selects an online device in the main toolbar, but no matching online device found in the transport pipeline:
   *    - Profiler-level selection with device name, marked as not running, and a default Common.Device instance.
   *    - This is an intermediate selection state as online toolbar device means the device should be fetched soon by transport pipeline.
   *
   * 3. User selects an offline device in the main toolbar, and no matching online device found in the transport pipeline:
   *    - Profiler-level selection with device name, marked as not running, and a default Common.Device instance.
   */
  fun onDeviceSelection(deviceSelection: ToolbarDeviceSelection) {
    val deviceName = deviceSelection.name
    val featureLevel = deviceSelection.featureLevel
    val icon = deviceSelection.icon

    if (deviceSelection.isRunning) {
      val device = deviceList.value.find { it.serial == deviceSelection.serial }

      if (device == null) {
        // Running device, but corresponding device from the pipeline not fetched yet.
        doDeviceSelection(deviceName, featureLevel, true, Common.Device.getDefaultInstance(), icon)
      } else {
        // Running device with corresponding device from the pipeline found.
        doDeviceSelection(deviceName, featureLevel, true, device, icon)
      }
    }
    else {
      // Offline devices have no mapped Common.Device, so use a default instance. Display device name to the user.
      doDeviceSelection(deviceName, featureLevel, false, Common.Device.getDefaultInstance(), icon)
    }
  }

  /**
   * Performs selection of Common.Device selected from standalone profiler device dropdown.
   */
  fun onDeviceSelection(newDevice: Common.Device) {
    doDeviceSelection(newDevice.model, newDevice.featureLevel, true, newDevice, null)
  }

  private fun doDeviceSelection(name: String,
                                featureLevel: Int,
                                isRunning: Boolean,
                                device: Common.Device,
                                icon: Icon?) {
    _selectedDevice.value = ProfilerDeviceSelection(name, featureLevel, isRunning, device, icon)
    setSelectedDevicesCount(1)
    onDeviceChange()
  }

  /**
   * Performs state changes necessary after user selects a new device such as resetting their currently selected process.
   */
  private fun onDeviceChange() {
    // Reset process selection to avoid ghost process selection on device change.
    resetProcessSelection()
    // Force reordering now that device is selected. This makes sure the process list is reordered correctly using the preferred process
    // in the case the preferred process was set before a device selection was made.
    reorderProcessList()
  }

  fun onProcessSelection(newProcess: Common.Process) {
    _selectedProcess.value = newProcess
    _isPreferredProcessSelected.value = newProcess.name == _preferredProcessName.value

    updateProfilingProcessStartingPoint()
  }

  private fun preferredProcessUpdated() {
    _preferredProcessName.value = profilers.preferredProcessName
    // Force update of process list ordering.
    reorderProcessList()
  }

  fun setSelectedDevicesCount(selectedDevicesCount: Int) {
    _selectedDevicesCount.value = selectedDevicesCount;
  }

  fun resetDeviceSelection() { _selectedDevice.value = null }

  fun resetProcessSelection() {
    onProcessSelection(Common.Process.getDefaultInstance())
  }

  private fun isDeviceSelected() = _selectedDevice.value != null

  /**
   * Returns whether the currently selected device is running or not.
   *
   * This can be determined by checking if the selected device is in the keys of the device to processes map returned by the transport
   * pipeline. This map only contains running devices.
   */
  private fun isSelectedDeviceRunning() = _deviceList.value.firstOrNull {
    _selectedDevice.value != null && it.deviceId == _selectedDevice.value!!.device.deviceId
  } != null

  data class ToolbarDeviceSelection(
    val name: String,
    val featureLevel: Int,
    val isRunning: Boolean,
    // The 'serial' field is only set to a non-empty string if isRunning is true, otherwise it will be an empty string.
    val serial: String,
    // The icon can be null if there is no icon found. Default is set to null for tests that do not require/test the icon.
    val icon: Icon? = null
  )

  data class ProfilerDeviceSelection(
    val name: String,
    val featureLevel: Int,
    // It is possible for the device to be running but not be discovered by the transport pipeline yet.
    val isRunning: Boolean,
    // The 'device' field is only set to a non default value when isRunning is true and the corresponding device in the transport pipeline
    // is found.
    val device: Common.Device,
    // The icon can be null if there is no icon found. Default is set to null for tests that do not require/test the icon.
    val icon: Icon? = null
  )
}