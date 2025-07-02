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

import com.android.tools.leakcanarylib.data.LeakingStatus
import com.android.tools.profilers.taskbased.home.StartTaskSelectionError
import com.android.tools.profilers.taskbased.home.StartTaskSelectionError.StarTaskSelectionErrorCode
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
  const val ACTION_BAR_RECORDING = "Recording:"
  const val ACTION_BAR_STOP_RECORDING = "Stop Recording"

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
  const val NO_RECORDINGS_TITLE = "No recordings"
  const val NO_RECORDINGS_INSTRUCTIONS_TEXT = "To add a new recording, start a new task or import a previously captured one."

  // Delete recording strings
  const val DELETE_RECORDING_DESC = "Delete recording"

  // Recording banner message
  const val RECORDING_BANNER_MESSAGE = "Profiler recordings are not preserved across Android Studio restarts. If the task supports " +
                                       "exporting you can export the recording to preserve it."

  // Do not show again option text
  const val DONT_SHOW_AGAIN_TITLE = "Don't show again"

  // For LeakCanary task
  // LeakList Strings
  const val LEAKCANARY_LEAK_HEADER_TEXT = "Leak"
  const val LEAKCANARY_OCCURRENCES_HEADER_TEXT = "Occurrences"
  const val LEAKCANARY_TOTAL_LEAKED_HEADER_TEXT = "Total leaked"
  const val LEAKCANARY_LEAK_LIST_EMPTY_INITIAL_MESSAGE = "Try to reproduce leaks by triggering potentially " +
                                                         "leaking actions within your app while the recording is ongoing."
  const val LEAKCANARY_INSTALLATION_REQUIRED_MESSAGE =
    "This task requires LeakCanary to be installed into your app for any leaks to be visible in Android Studio."
  const val LEAKCANARY_NO_LEAK_FOUND_MESSAGE = "No leaks found."

  // LeakDetails Strings
  const val LEAKCANARY_BULLET_UNICODE = "\u2022"
  const val LEAKCANARY_LEAKING = "Leaking"
  const val LEAKCANARY_WHY = "Why"
  const val LEAKCANARY_NOT_LEAKING = "Not Leaking"
  const val LEAKCANARY_REFERENCING_FIELD = "Referencing Field: "
  const val LEAKCANARY_RETAINED_BYTES = "Retained Bytes: "
  const val LEAKCANARY_REFERENCING_OBJECTS = "Referencing Objects: "
  const val LEAKCANARY_MORE_INFO = "More info"
  const val LEAKCANARY_LEAK_DETAIL_EMPTY_INITIAL_MESSAGE = "Once the current ongoing recording has captured memory leaks their details" +
                                                           " will appear here"
  const val LEAKCANARY_GO_TO_DECLARATION = "Go to declaration"

  fun getTaskTooltip(taskType: ProfilerTaskType) = when (taskType) {
    ProfilerTaskType.SYSTEM_TRACE -> "Captures a trace that can help you understand how your app interacts with system resources"
    ProfilerTaskType.HEAP_DUMP -> "Dumps the heap showing which objects in your app are using memory at the time of capture"
    ProfilerTaskType.CALLSTACK_SAMPLE -> "Uses sampling to capture the call stacks of an app's native and Java/Kotlin code"
    ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> "Records Java and Kotlin memory allocations"
    ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> "Captures the call stacks during your app’s Java/Kotlin code execution"
    ProfilerTaskType.NATIVE_ALLOCATIONS -> "Captures native memory allocations"
    ProfilerTaskType.LIVE_VIEW -> "Displays and records a streaming timeline of CPU usage and memory footprint"
    ProfilerTaskType.LEAKCANARY -> "Pulls memory leaks detected by LeakCanary from an Android device"
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

  // Rebuild instructions
  const val PROFILEABLE_REBUILD_INSTRUCTION_TOOLTIP = "You can restart the process as 'profileable' by clicking " +
                                                      "'${PROFILE_WITH_LOW_OVERHEAD_ACTION_NAME}' in the main toolbar's more " +
                                                      "actions menu. This will trigger a rebuild."
  const val DEBUGGABLE_REBUILD_INSTRUCTION_TOOLTIP = "You can restart the process as 'debuggable' by clicking " +
                                                     "'${PROFILE_WITH_COMPLETE_DATA_ACTION_NAME}' in the main toolbar's more " +
                                                     "actions menu. This will trigger a rebuild."

  fun getTaskTitle(taskType: ProfilerTaskType, isTaskTitleV2Enabled: Boolean) =
    if (isTaskTitleV2Enabled) {
      when (taskType) {
        ProfilerTaskType.SYSTEM_TRACE -> "Record App and System Performance Data"
        ProfilerTaskType.HEAP_DUMP -> "Analyze Memory Usage"
        ProfilerTaskType.CALLSTACK_SAMPLE -> "Analyze Time Spent per Call Stack"
        ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> "Record Memory Allocations (Java/Kotlin)"
        ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> "Record Method Calls"
        ProfilerTaskType.NATIVE_ALLOCATIONS -> "Record Memory Allocations (Native)"
        ProfilerTaskType.LIVE_VIEW -> "View Live Telemetry"
        ProfilerTaskType.LEAKCANARY -> "Find memory leaks with LeakCanary"
        ProfilerTaskType.UNSPECIFIED -> ""
      }
    }
    else {
      when (taskType) {
        ProfilerTaskType.SYSTEM_TRACE -> "Capture System Activities"
        ProfilerTaskType.HEAP_DUMP -> "Analyze Memory Usage"
        ProfilerTaskType.CALLSTACK_SAMPLE -> "Find CPU Hotspots"
        ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> "Track Memory Consumption"
        ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> "Find CPU Hotspots"
        ProfilerTaskType.NATIVE_ALLOCATIONS -> "Track Memory Consumption"
        ProfilerTaskType.LIVE_VIEW -> "View Live Telemetry"
        ProfilerTaskType.LEAKCANARY -> "Find memory leaks with LeakCanary"
        ProfilerTaskType.UNSPECIFIED -> ""
      }
    }

  fun getTaskSubtitle(taskType: ProfilerTaskType, isTaskTitleV2Enabled: Boolean) =
    when (taskType) {
      ProfilerTaskType.CALLSTACK_SAMPLE -> if (isTaskTitleV2Enabled) "Stack Sampling" else "Callstack Sample"

      ProfilerTaskType.SYSTEM_TRACE,
      ProfilerTaskType.HEAP_DUMP,
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS,
      ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING,
      ProfilerTaskType.NATIVE_ALLOCATIONS -> taskType.description

      ProfilerTaskType.LIVE_VIEW, ProfilerTaskType.LEAKCANARY, ProfilerTaskType.UNSPECIFIED -> ""
    }

  fun getStartTaskErrorMessage(taskStartError: StarTaskSelectionErrorCode) =
    when (taskStartError) {
      StarTaskSelectionErrorCode.INVALID_DEVICE -> "No valid device is selected"
      StarTaskSelectionErrorCode.INVALID_PROCESS -> "No valid process is selected"
      StarTaskSelectionErrorCode.INVALID_TASK -> "No valid task is selected"
      StarTaskSelectionErrorCode.PREFERRED_PROCESS_NOT_SELECTED_FOR_STARTUP_TASK -> "Tasks configured to run at process start require " +
                                                                                    "your app's process to be selected"

      StarTaskSelectionErrorCode.TASK_UNSUPPORTED_ON_STARTUP -> "The selected task does not support 'Start profiler task from process " +
                                                                "start'"

      StarTaskSelectionErrorCode.TASK_FROM_PROCESS_START_USING_API_BELOW_MIN -> "The API level is too low to 'Start profiler task from " +
                                                                                "process start'"

      StarTaskSelectionErrorCode.TASK_FROM_NOW_USING_API_BELOW_MIN -> "The API level is too low to 'Start profiler task from now'"
      StarTaskSelectionErrorCode.TASK_FROM_NOW_USING_DEAD_PROCESS -> "'Start profiler task from now' requires a running process"
      StarTaskSelectionErrorCode.DEVICE_SELECTION_IS_OFFLINE -> "The selected device is offline"
      StarTaskSelectionErrorCode.TASK_REQUIRES_DEBUGGABLE_PROCESS -> "'Start profiler task from now' requires a running debuggable " +
                                                                     "process"

      StarTaskSelectionErrorCode.NO_STARTING_POINT_SELECTED -> "No task starting point selected"
      StarTaskSelectionErrorCode.GENERAL_ERROR -> "This task cannot be run in this configuration"
    }

  fun getStartTaskErrorNotificationText(taskStartError: StartTaskSelectionError): String {
    val errorMessage = getStartTaskErrorMessage(taskStartError.starTaskSelectionErrorCode)
    if (taskStartError.actionableInfo == null) {
      return errorMessage
    }

    return "$errorMessage. ${taskStartError.actionableInfo}."
  }

  fun getLeakStatusText(leakingStatus: LeakingStatus): String {
    return when (leakingStatus) {
      LeakingStatus.YES -> "Yes"
      LeakingStatus.NO -> "No"
      LeakingStatus.UNKNOWN -> "Unknown"
    }
  }
}