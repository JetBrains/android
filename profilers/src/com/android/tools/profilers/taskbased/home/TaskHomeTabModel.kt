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
import com.android.tools.profilers.LogUtils
import com.android.tools.profilers.ProcessUtils.isProfileable
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.taskbased.TaskEntranceTabModel
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.canTaskStartFromNow
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.canTaskStartFromProcessStart
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.isTaskStartFromNowEnabled
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.isTaskStartFromProcessStartEnabled
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel.ProfilerDeviceSelection
import com.android.tools.profilers.taskbased.logging.TaskLoggingUtils
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
  private val _profilingProcessStartingPoint = MutableStateFlow(ProfilingProcessStartingPoint.UNSPECIFIED)
  val profilingProcessStartingPoint = _profilingProcessStartingPoint.asStateFlow()
  private val _isProfilingFromNowOptionEnabled = MutableStateFlow(false)
  val isProfilingFromNowOptionEnabled = _isProfilingFromNowOptionEnabled.asStateFlow()
  private val _isProfilingFromProcessStartOptionEnabled = MutableStateFlow(false)
  val isProfilingFromProcessStartOptionEnabled = _isProfilingFromProcessStartOptionEnabled.asStateFlow()

  /**
   *  This field is only used/matters when TaskHomeTabModel#doesTaskHaveRecordingTypes returns true.
   */
  private val _taskRecordingType = MutableStateFlow(TaskRecordingType.INSTRUMENTED)
  val taskRecordingType = _taskRecordingType.asStateFlow()

  /**
   * The user's selections made at the moment of clicking the start profiler task button. This state needs to be stored as performing a
   * startup task is an asynchronous operation, and therefore there is a non-zero amount of time in between the user clicking the start
   * profiler task button and the app actually launching where the user can change their selections.
   *
   * Note: Once the selection state is consumed for startup task purposes, the value is reset to null.
   */
  var selectionStateOnTaskEnter: SelectionStateOnTaskEnter? = null

  /**
   * Similar to `selectionStateOnTaskEnter`, `persistentStateOnTaskEnter` stores selection state in the task home tab on task enter.
   * However, one fundamental difference is that this state persists and is never reset by the program automatically, while
   * `selectionStateOnTaskEnter` is.
   *
   * Why this is needed:
   * When executing a startup task, certain information needs to persist beyond the task's initial launch. For instance, during a
   * Java/Kotlin Method Recording startup task, the `selectionStateOnTaskEnter` gets reset upon task initiation. However, to keep track
   * of the recording type selected in the task metrics, we require the previously selected recording type for the current task. Therefore,
   * the state of the recording type selection remains persistent and is never reset. Nevertheless, it can be modified or updated when
   * another task is initiated.
   */
  var persistentStateOnTaskEnter = PersistentSelectionStateOnTaskEnter(null)

  val processListModel = ProcessListModel(profilers, this::updateProfilingProcessStartingPointDropdown)

  /**
   * Updated the TaskStartingPointDropdown options availability, and performs auto-selection of options in certain scenarios as a
   * convenience to the user.
   *
   * To be called on process and task selection as both selections can influence the dropdown state.
   */
  override fun updateProfilingProcessStartingPointDropdown() {
    val isNowOptionEnabled = isTaskStartFromNowEnabled(selectedProcess)
    val isProcessStartOptionEnabled = isTaskStartFromProcessStartEnabled(selectedTaskType, selectedProcess, profilers)

    // Update the options' availability
    _isProfilingFromNowOptionEnabled.value = isNowOptionEnabled
    _isProfilingFromProcessStartOptionEnabled.value = isProcessStartOptionEnabled

    // Perform auto-selection of starting point dropdown options in two scenarios:
    // Scenario 1: If no selection has been made yet (such as on initial opening of the Profiler) as indicated by the UNSPECIFIED
    // selection value, then prefer selection of NOW option if its enabled (a running process is selected), otherwise choose PROCESS_START.
    if (_profilingProcessStartingPoint.value == ProfilingProcessStartingPoint.UNSPECIFIED) {
      if (isNowOptionEnabled) {
        setProfilingProcessStartingPoint(ProfilingProcessStartingPoint.NOW)
      }
      else {
        setProfilingProcessStartingPoint(ProfilingProcessStartingPoint.PROCESS_START)
      }
    }
    // Scenario 2: If a selection has already been made prior, then auto-select an option if it is the only one enabled.
    else {
      if (isNowOptionEnabled && !isProcessStartOptionEnabled) {
        setProfilingProcessStartingPoint(ProfilingProcessStartingPoint.NOW)
      }
      else if (!isNowOptionEnabled && isProcessStartOptionEnabled) {
        setProfilingProcessStartingPoint(ProfilingProcessStartingPoint.PROCESS_START)
      }
    }
  }

  fun setProfilingProcessStartingPoint(profilingProcessStartingPoint: ProfilingProcessStartingPoint) {
    _profilingProcessStartingPoint.value = profilingProcessStartingPoint
  }

  fun setTaskRecordingType(recordingType: TaskRecordingType) {
    _taskRecordingType.value = recordingType
  }

  fun resetSelectionStateAndClearStartupTaskConfigs() {
    selectionStateOnTaskEnter = null
    // The call to clearStartupTaskConfigs might already be done by the `AndroidProfilerTaskLaunchContributor` after consuming the config
    // to start the task capture, but in other cases (such as when the user cancels a debuggable build via the dialog), it is not.
    // Nonetheless, it is harmless to call this method multiple times.
    profilers.ideServices.clearStartupTaskConfigs()
  }

  private fun setSelectionState() {
    selectionStateOnTaskEnter = SelectionStateOnTaskEnter(_profilingProcessStartingPoint.value, selectedTaskType)
    // If the selected task has recording types, then the recording type selection is registered.
    if (doesTaskHaveRecordingTypes(selectedTaskType)) {
      persistentStateOnTaskEnter = PersistentSelectionStateOnTaskEnter(_taskRecordingType.value)
    }
  }

  @VisibleForTesting
  val selectedDevice: ProfilerDeviceSelection? get() = processListModel.selectedDevice.value

  @VisibleForTesting
  val selectedProcess: Common.Process get() = processListModel.selectedProcess.value

  override fun doEnterTaskButton() {
    // Save snapshot of the task home selections made just in case user changes any selection in between enter task button click and usage
    // of the selection state.
    setSelectionState()

    val profilingProcessStartingPoint = _profilingProcessStartingPoint.value

    // Reset the current task type as starting a new task should populate the current task type on processing of new session.
    profilers.sessionsManager.currentTaskType = ProfilerTaskType.UNSPECIFIED

    // Log selections to aid troubleshooting future user-reported issues.
    LogUtils.log(javaClass, TaskLoggingUtils.buildStartTaskLogMessage(selectedTaskType, profilingProcessStartingPoint,
                                                                      selectedProcess.isProfileable()))

    when (profilingProcessStartingPoint) {
      ProfilingProcessStartingPoint.PROCESS_START -> {
        assert(canTaskStartFromProcessStart(selectedTaskType, selectedDevice, selectedProcess, profilers))

        // The only way the user would be able to set `isProfilingFromProcessStart` to be true is if they already selected a startup-capable
        // task. Thus, it is safe to enable the corresponding startup config for the selected task.
        profilers.ideServices.enableStartupTask(selectedTaskType, _taskRecordingType.value)

        val prefersProfileable = taskGridModel.selectedTaskType.value.prefersProfileable
        profilers.ideServices.buildAndLaunchAction(prefersProfileable, selectedDevice!!.featureLevel)

        // Reset process selection as process will be recreated and thus the original selection will be lost.
        processListModel.resetProcessSelection()
      }

      ProfilingProcessStartingPoint.NOW -> {
        assert(canTaskStartFromNow(selectedTaskType, selectedDevice, selectedProcess, profilers.taskHandlers))
        profilers.setProcess(selectedDevice!!.device, selectedProcess, TaskTypeMappingUtils.convertTaskType(selectedTaskType), false)
      }

      else -> {
        throw IllegalStateException("Could not start profiler task with the current selections made.")
      }
    }
  }

  data class SelectionStateOnTaskEnter(
    val profilingProcessStartingPoint: ProfilingProcessStartingPoint,
    val selectedStartupTaskType: ProfilerTaskType
  )

  data class PersistentSelectionStateOnTaskEnter(
    val recordingType: TaskRecordingType?
  )

  enum class ProfilingProcessStartingPoint {
    UNSPECIFIED,
    NOW,
    PROCESS_START
  }

  enum class TaskRecordingType {
    // The INSTRUMENTED type is listed first as this is the default and recommended option. This ordering will be reflected in the
    // recording type dropdown options order.
    INSTRUMENTED,
    SAMPLED
  }

  companion object {
    fun doesTaskHaveRecordingTypes(taskType: ProfilerTaskType) =
      when (taskType) {
        ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> true
        else -> false
      }
  }
}