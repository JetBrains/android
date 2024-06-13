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
package com.android.tools.profilers.taskbased.common.constants

import com.android.tools.profilers.tasks.ProfilerTaskType

object TaskBasedUxStrings {
  // EnterTaskButton strings
  const val OPEN_PROFILER_TASK = "Open profiler task"
  const val START_PROFILER_TASK = "Start profiler task"

  // Recording screen strings
  const val RECORDING_IN_PROGRESS = "Recording..."
  const val STOP_RECORDING = "Stop recording and show results"

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
  const val DEBUGGABLE_PROCESS_TITLE = "Debuggable"
  const val PROFILEABLE_PROCESS_TITLE = "Profileable"
  const val DEAD_PROCESS_TITLE = "Not running"

  // Task starting point dropdown strings
  const val STARTING_POINT_DROPDOWN_TITLE = "Start profiler task from"
  const val STARTUP_STARTING_POINT_DROPDOWN_OPTION = "Process start (restarts process)"
  const val NOW_STARTING_POINT_DROPDOWN_OPTION = "Now (attaches to selected process)"

  // Task recording type dropdown strings (only available for the ART-based task)
  const val RECORDING_TYPE_DROPDOWN_TITLE = "Recording type"
  const val ART_INSTRUMENTED_RECORDING_TYPE_OPTION = "Tracing"
  const val ART_SAMPLED_RECORDING_TYPE_OPTION = "Sampling (legacy)"

  // Export and import strings
  const val IMPORT_RECORDING_DESC = "Import recording"
  const val EXPORT_RECORDING_DESC = "Export recording"
  const val EXPORT_RECORDING_DISABLED_TOOLTIP = "Recording is not exportable"

  // Delete recording strings
  const val DELETE_RECORDING_DESC = "Delete recording"
  const val DELETE_RECORDING_DISABLED_TOOLTIP = "Recording is not deletable"

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
      ProfilerTaskType.LIVE_VIEW, ProfilerTaskType.UNSPECIFIED -> null
    }
}