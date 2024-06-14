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
package com.android.tools.profilers.taskbased.common.constants.strings

import com.android.tools.profilers.taskbased.home.StartTaskSelectionError
import com.android.tools.profilers.tasks.ProfilerTaskType

object TaskBasedUxStrings {

  // Commonly used strings
  const val INFO_ICON_DESC = "More info"

  // EnterTaskButton strings
  const val OPEN_PROFILER_TASK = "Open profiler task"
  const val START_PROFILER_TASK = "Start profiler task"
  const val START_PROFILER_TASK_ANYWAY = "Start anyway"

  // Recording screen strings
  const val RECORDING_IN_PROGRESS = "Recording..."
  const val STOPPING_IN_PROGRESS = "Stopping..."
  const val STOP_RECORDING = "Stop recording and show results"
  const val STOPPING_TIME_WARNING = "It might take up to a minute for the recording to stop."

  // Icon description strings
  const val TASK_CONFIG_DIALOG_DESC = "Task Configurations"

  // Top bar title
  const val TOP_BAR_TITLE = "Tasks"

  // Device dropdown strings
  const val NO_SUPPORTED_DEVICES_TITLE = "No supported devices"
  const val NO_DEVICE_SELECTED_TITLE = "No Devices"
  const val MULTIPLE_DEVICES_SELECTED_TITLE = "Multiple Devices"
  const val LOADING_SELECTED_DEVICE_INFO = "Loading"
  const val SELECTED_DEVICE_OFFLINE = "Not running"

  // Process list message strings
  const val NO_DEVICE_SELECTED_MESSAGE = "No device selected. You can select a device via the Devices dropdown at the main window toolbar."
  const val MULTIPLE_DEVICES_SELECTED_MESSAGE = "Multiple device selected. Profilers does not support multiple device selection. Please " +
                                                "select a single device via the Devices dropdown at the main window toolbar."
  const val PREFERRED_PROCESS_DESC = "Preferred process"
  const val PREFERRED_PROCESS_TOOLTIP = "The process for the selected Run/Debug Configuration"

  const val DEBUGGABLE_PROCESS_TITLE = "Debuggable"
  const val PROFILEABLE_PROCESS_TITLE = "Profileable"
  const val DEAD_PROCESS_TITLE = "Not running"
  const val DEVICE_SELECTION_TOOLTIP = "The selected device can be changed via the main toolbar"

  // Task starting point dropdown strings
  const val STARTING_POINT_DROPDOWN_TITLE = "Start profiler task from"
  const val STARTUP_STARTING_POINT_DROPDOWN_OPTION_PRIMARY_TEXT = "Process start"
  const val STARTUP_STARTING_POINT_DROPDOWN_OPTION_SECONDARY_TEXT_START = "starts process"
  const val STARTUP_STARTING_POINT_DROPDOWN_OPTION_SECONDARY_TEXT_RESTART = "restarts process"
  const val NOW_STARTING_POINT_DROPDOWN_OPTION_PRIMARY_TEXT = "Now"
  const val NOW_STARTING_POINT_DROPDOWN_OPTION_SECONDARY_TEXT = "attaches to selected process"

  // Task recording type dropdown strings (only available for the ART-based task)
  const val RECORDING_TYPE_DROPDOWN_TITLE = "Recording type"
  const val ART_INSTRUMENTED_RECORDING_TYPE_OPTION = "Tracing"
  const val ART_SAMPLED_RECORDING_TYPE_OPTION_PRIMARY_TEXT = "Sampling"
  const val ART_SAMPLED_RECORDING_TYPE_OPTION_SECONDARY_TEXT = "legacy"

  // Export and import strings
  const val IMPORT_RECORDING_DESC = "Import recording"
  const val EXPORT_RECORDING_DESC = "Export recording"

  // Delete recording strings
  const val DELETE_RECORDING_DESC = "Delete recording"

  fun getTaskTooltip(taskType: ProfilerTaskType) = when(taskType) {
    ProfilerTaskType.SYSTEM_TRACE -> "Captures a trace that can help you understand how your app interacts with system resources"
    ProfilerTaskType.HEAP_DUMP -> "Dumps the heap showing which objects in your app are using memory at the time of capture"
    ProfilerTaskType.CALLSTACK_SAMPLE -> "Uses sampling to capture the call stacks of an app's native and Java/Kotlin code"
    ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS ->  "Records Java and Kotlin memory allocations"
    ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> "Captures the call stacks during your app’s Java/Kotlin code execution"
    ProfilerTaskType.NATIVE_ALLOCATIONS -> "Captures native memory allocations"
    ProfilerTaskType.LIVE_VIEW -> "Displays and records a streaming timeline of CPU usage and memory footprint"
    else -> ""
  }

  // Profiler actions
  const val PROFILE_WITH_LOW_OVERHEAD_ACTION_NAME = "Profiler: Run as profileable (low overhead)"
  const val PROFILE_WITH_COMPLETE_DATA_ACTION_NAME = "Profiler: Run as debuggable (complete data)"

  // Task notifications
  const val START_TASK_SELECTION_ERROR_ICON_DESC = "Task start selection error"
  const val PROFILEABLE_PREFERRED_WARNING_MAIN_TEXT = "This task recommends the process to be 'profileable'"
  const val PROFILEABLE_PREFERRED_WARNING_TOOLTIP = "The process currently selected is running in the 'debuggable' state. This will " +
                                                    "result in inconsistent readings. For more accurate data, restart the process as" +
                                                    " 'profileable'."
  const val PROFILEABLE_PREFERRED_REBUILD_INSTRUCTION_TOOLTIP = "You can restart the process as 'profileable' by clicking " +
                                                                "'${PROFILE_WITH_LOW_OVERHEAD_ACTION_NAME}' in the main toolbar more " +
                                                                "actions menu. This will trigger a rebuild."

  fun getTaskTitle(taskType: ProfilerTaskType) =
    when (taskType)  {
      ProfilerTaskType.SYSTEM_TRACE -> "Capture System Activities"
      ProfilerTaskType.HEAP_DUMP -> "Analyze Memory Usage"
      ProfilerTaskType.CALLSTACK_SAMPLE -> "Find CPU Hotspots"
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> "Track Memory Consumption"
      ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> "Find CPU Hotspots"
      ProfilerTaskType.NATIVE_ALLOCATIONS -> "Track Memory Consumption"
      ProfilerTaskType.LIVE_VIEW -> "View Live Telemetry"
      ProfilerTaskType.UNSPECIFIED -> ""
    }

  fun getTaskSubtitle(taskType: ProfilerTaskType) =
    when (taskType)  {
      ProfilerTaskType.SYSTEM_TRACE,
      ProfilerTaskType.HEAP_DUMP,
      ProfilerTaskType.CALLSTACK_SAMPLE,
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS,
      ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING,
      ProfilerTaskType.NATIVE_ALLOCATIONS -> taskType.description
      ProfilerTaskType.LIVE_VIEW, ProfilerTaskType.UNSPECIFIED -> ""
    }

  fun getStartTaskErrorMessage(taskStartError: StartTaskSelectionError) =
    when (taskStartError) {
      StartTaskSelectionError.INVALID_DEVICE -> "No valid device is selected"
      StartTaskSelectionError.INVALID_PROCESS -> "No valid process is selected"
      StartTaskSelectionError.INVALID_TASK -> "No valid task is selected"
      StartTaskSelectionError.PREFERRED_PROCESS_NOT_SELECTED_FOR_STARTUP_TASK -> "Tasks configured to run at process start require your " +
                                                                                 "app's process to be selected"
      StartTaskSelectionError.TASK_UNSUPPORTED_ON_STARTUP -> "The selected task does not support 'Start profiler task from process start'"
      StartTaskSelectionError.STARTUP_TASK_USING_UNSUPPORTED_DEVICE -> "The currently selected device does not support tasks configured " +
                                                                       "to run at process start"
      StartTaskSelectionError.DEVICE_SELECTION_IS_OFFLINE -> "The selected device is offline"
      StartTaskSelectionError.TASK_UNSUPPORTED_BY_DEVICE_OR_PROCESS -> "Task is not supported by the selected device or process"
      StartTaskSelectionError.GENERAL_ERROR -> "This task cannot be run in this configuration"
    }
}