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
package com.android.tools.profilers.taskbased.home

import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.taskbased.TaskEntranceTabModel
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel.ProfilerDeviceSelection
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.TaskTypeMappingUtils
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The TaskHomeTabModel serves as the data model for the task home tab. It owns the process list model to manage the available processes
 * to the user to select from, as well as current process selection. It also implements the behavior on start Profiler task button click,
 * reading the process and Profiler task selection and using such values to launch the Profiler task.
 */
class TaskHomeTabModel(profilers: StudioProfilers) : TaskEntranceTabModel(profilers) {
  private val _isProfilingFromProcessStart = MutableStateFlow(false)
  val isProfilingFromProcessStart = _isProfilingFromProcessStart.asStateFlow()

  var selectionStateOnTaskEnter = SelectionStateOnTaskEnter(false, ProfilerTaskType.UNSPECIFIED)

  val processListModel = ProcessListModel(profilers, isProfilingFromProcessStart, taskGridModel::resetTaskSelection)

  fun setIsProfilingFromProcessStart(isProfilingFromProcessStart: Boolean) {
    _isProfilingFromProcessStart.value = isProfilingFromProcessStart
    // Task selection should be reset when toggling the startup tasks feature as the task enablement criteria changes.
    taskGridModel.resetTaskSelection()
  }

  data class SelectionStateOnTaskEnter(
    val isStartupTaskEnabled: Boolean,
    val selectedStartupTaskType: ProfilerTaskType
  )

  private fun setSelectionState() {
    selectionStateOnTaskEnter = SelectionStateOnTaskEnter(_isProfilingFromProcessStart.value, selectedTaskType)
  }

  private val isDeviceAndProcessSelected get() = selectedDevice != null &&
                                                 selectedDevice!!.device != Common.Device.getDefaultInstance() &&
                                                 selectedProcess != Common.Process.getDefaultInstance()

  @VisibleForTesting
  val selectedDevice: ProfilerDeviceSelection? get() = processListModel.selectedDevice.value
  @VisibleForTesting
  val selectedProcess: Common.Process get() = processListModel.selectedProcess.value

  override fun onEnterTaskButtonClick() {
    // Save snapshot of the task home selections made just in case user changes any selection in between enter task button click and usage
    // of the selection state.
    setSelectionState()

    if (_isProfilingFromProcessStart.value) {
      val prefersProfileable = taskGridModel.selectedTaskType.value.prefersProfileable
      profilers.ideServices.buildAndLaunchAction(prefersProfileable)
    }
    else if (isDeviceAndProcessSelected) {
      // If the user clicks to enter the task with startup profiling disabled, startup config selections should be reset.
      profilers.ideServices.disableStartupTasks()
      profilers.setProcess(selectedDevice!!.device, selectedProcess, TaskTypeMappingUtils.convertTaskType(selectedTaskType), false)
    }
    else {
      throw IllegalStateException("Non-startup tasks require a device selection. No device is currently selected.")
    }
  }
}