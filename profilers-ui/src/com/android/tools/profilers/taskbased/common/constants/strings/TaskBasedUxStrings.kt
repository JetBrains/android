/*
 * Copyright (C) 2026 The Android Open Source Project
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
import com.android.tools.profilers.taskbased.home.StartTaskSelectionError.StartTaskSelectionErrorCode
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
  const val STOPPING_TIME_WARNING = "It might take up to a few minutes for the recording to stop."
  const val ACTION_BAR_RECORDING = "Recording:"
  const val ACTION_BAR_STOP_RECORDING = "Stop Recording"
  const val FAILED_TO_RECORD_TITLE = "%s task did not complete successfully"
  const val FAILED_TO_RECORD_MESSAGE = "An unexpected error occurred. Please close this tab and try again."

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
  const val MULTIPLE_DEVICES_SELECTED_MESSAGE =
    "Multiple device selected. Profilers does not support multiple device selection. Please " +
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
  const val RECORDING_BANNER_MESSAGE =
    "Profiler recordings are not preserved across Android Studio restarts. If the task supports " +
      "exporting you can export the recording to preserve it."

  // Do not show again option text
  const val DONT_SHOW_AGAIN_TITLE = "Don't show again"

  // For LeakCanary task
  // LeakList Strings
  const val LEAKCANARY_LEAK_HEADER_TEXT = "Leak"
  const val LEAKCANARY_GC_ROOT = "GC Root"
  const val LEAKCANARY_OCCURRENCES_HEADER_TEXT = "Occurrences"
  const val LEAKCANARY_TOTAL_LEAKED_HEADER_TEXT = "Total leaked"
  const val LEAKCANARY_LEAK_LIST_EMPTY_INITIAL_MESSAGE =
    "Try to reproduce leaks by triggering potentially " + "leaking actions within your app while the recording is ongoing."
  const val LEAKCANARY_NO_LEAK_FOUND_MESSAGE = "No leaks found."
  const val LEAKCANARY_MISSING_MESSAGE =
    "LeakCanary is missing. Retry after adding the dependency: debugImplementation " + "'com.squareup.leakcanary:leakcanary-android:2.14'."
  const val LEAKCANARY_CHECKING_PRESENCE = "Checking LeakCanary presence..."
  const val TASK_HAS_DEBUGGER_ATTACHED_MESSAGE = "Cannot start the selected task while a debugger is attached to the process."

  const val LEAKCANARY_CHECK_TIMEOUT_MESSAGE =
    "Connection timeout: The app may be running in the background. Bring the app to the foreground and reselect the process to retry."

  // LeakDetails Strings
  const val LEAKCANARY_BULLET_UNICODE = "\u2022"
  const val LEAKCANARY_LEAKING = "Leaking"
  const val LEAKCANARY_WHY = "Why"
  const val LEAKCANARY_NOT_LEAKING = "Not Leaking"
  const val LEAKCANARY_REFERENCING_FIELD = "Referencing Field: "
  const val LEAKCANARY_RETAINED_BYTES = "Retained Bytes: "
  const val LEAKCANARY_REFERENCING_OBJECTS = "Referencing Objects: "
  const val LEAKCANARY_MORE_INFO = "More info"
  const val LEAKCANARY_LEAK_DETAIL_EMPTY_INITIAL_MESSAGE =
    "Once the current ongoing recording has captured memory leaks their details" + " will appear here"
  const val LEAKCANARY_GO_TO_DECLARATION = "Go to declaration"
  const val LEAKCANARY_NO_DECLARATION_FOUND = "No declaration found"
  const val LEAKCANARY_NO_DECLARATION_FOUND_TOOLTIP = "No declaration found in Project Files"
  const val LEAKCANARY_OPEN = "open"
  const val LEAKCANARY_CLOSE = "close"
  const val LEAKCANARY_EXPAND_ALL = "Expand all nodes"
  const val LEAKCANARY_COLLAPSE_ALL = "Collapse all nodes"
  const val LEAKCANARY_EXPAND_ALL_SHORTCUT = "Ctrl+NumPad+"
  const val LEAKCANARY_COLLAPSE_ALL_SHORTCUT = "Ctrl+NumPad-"
  const val LEAKCANARY_FIX_WITH_AGENT = "Fix with Agent"
  const val LEAKCANARY_COPY_TO_CLIPBOARD = "Copy trace to clipboard"
  const val LEAKCANARY_ANALYSIS = "Analyzing results:"
  const val LEAKCANARY_WAITING_HEAP_DUMP = "Waiting to dump heap until"
  const val LEAKCANARY_RETAINED_OBJECT = "retained object"
  const val LEAKCANARY_CAPTURING_DUMP = "Retained objects detected. Capturing heap dump for leak analysis..."
  const val LEAKCANARY_FORCE_DUMP = "Force dump"
  const val LEAKCANARY_TOOLTIP_FORCE_DUMP_WAITING = "Wait for at least 1 retained object to be detected before forcing a heap dump."
  const val LEAKCANARY_TOOLTIP_FORCE_DUMP_THRESHOLD_REACHED = "An automatic heap dump is already being captured."
  const val LEAKCANARY_TOOLTIP_FORCE_DUMP_EXECUTING = "A forced heap dump is already in progress..."
  const val LEAKCANARY_TOOLTIP_FORCE_DUMP_STOPPING = "A final heap dump is being captured before stopping."
  const val LEAKCANARY_TOOLTIP_FORCE_DUMP_STOPPING_NO_DUMP = "The recording is currently stopping."
  const val LEAKCANARY_BANNER_MESSAGE = "On-device customizations are being bypassed. Switch to App Customization to enable them."
  const val LEAKCANARY_EDIT_CONFIGURATION = "Edit configuration"

  fun getTaskTooltip(taskType: ProfilerTaskType, isProfilerHomeTabV2Enabled: Boolean = false) =
    if (isProfilerHomeTabV2Enabled) {
      when (taskType) {
        ProfilerTaskType.HEAP_DUMP ->
          "Examine memory footprint to investigate issues such as leaks, duplicated bitmaps, and large object retention."
        ProfilerTaskType.LIVE_VIEW ->
          "Monitor live CPU and memory behavior to identify anomalies or determine if a deeper recording is needed."
        ProfilerTaskType.SYSTEM_TRACE ->
          "Identify performance bottlenecks with low overhead and precise timing. Supports default and custom instrumentation."
        ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS ->
          "Track allocation patterns and memory consumption to optimize footprint and surface potential leaks."
        ProfilerTaskType.LEAKCANARY -> "Narrow down the root cause of memory leaks with a leak trace."
        ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING ->
          "Analyze exact execution paths and per-method timing for Java/Kotlin code. Adds significant overhead and may skew timing measurements."
        ProfilerTaskType.CALLSTACK_SAMPLE -> "Find hotspots and see where threads spend their time across Java/Kotlin and C++ code."
        ProfilerTaskType.NATIVE_ALLOCATIONS -> "Monitor native memory usage and identify consumption or leaks in C++ code."
        else -> ""
      }
    } else {
      when (taskType) {
        ProfilerTaskType.HEAP_DUMP -> "Dumps the heap showing which objects in your app are using memory at the time of capture"
        ProfilerTaskType.LIVE_VIEW -> "Displays and records a streaming timeline of CPU usage and memory footprint"
        ProfilerTaskType.SYSTEM_TRACE -> "Captures a trace that can help you understand how your app interacts with system resources"
        ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> "Records Java and Kotlin memory allocations"
        ProfilerTaskType.LEAKCANARY -> "Pulls memory leaks detected by LeakCanary from an Android device"
        ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> "Captures the call stacks during your app’s Java/Kotlin code execution"
        ProfilerTaskType.CALLSTACK_SAMPLE -> "Uses sampling to capture the call stacks of an app's native and Java/Kotlin code"
        ProfilerTaskType.NATIVE_ALLOCATIONS -> "Captures native memory allocations"
        else -> ""
      }
    }

  // Profiler actions
  const val PROFILE_WITH_LOW_OVERHEAD_ACTION_NAME = "Profiler: Run as profileable (low overhead)"
  const val PROFILE_WITH_COMPLETE_DATA_ACTION_NAME = "Profiler: Run as debuggable (complete data)"

  // Task notifications
  const val START_TASK_SELECTION_ERROR_ICON_DESC = "Task start selection error"
  const val PROFILEABLE_PREFERRED_WARNING_MAIN_TEXT = "This task recommends the process to be 'profileable'"
  const val PROFILEABLE_PREFERRED_WARNING_TOOLTIP =
    "The process currently selected is running in the 'debuggable' state. This will " +
      "result in inconsistent readings. For more accurate data, restart the process as" +
      " 'profileable'."

  // Rebuild instructions
  const val PROFILEABLE_REBUILD_INSTRUCTION_TOOLTIP =
    "You can restart the process as 'profileable' by clicking " +
      "'${PROFILE_WITH_LOW_OVERHEAD_ACTION_NAME}' in the main toolbar's more " +
      "actions menu. This will trigger a rebuild."
  const val DEBUGGABLE_REBUILD_INSTRUCTION_TOOLTIP =
    "You can restart the process as 'debuggable' by clicking " +
      "'${PROFILE_WITH_COMPLETE_DATA_ACTION_NAME}' in the main toolbar's more " +
      "actions menu. This will trigger a rebuild."

  fun getTaskTitle(taskType: ProfilerTaskType) =
    when (taskType) {
      ProfilerTaskType.HEAP_DUMP -> "Analyze Memory Usage"
      ProfilerTaskType.LIVE_VIEW -> "View Live Telemetry"
      ProfilerTaskType.SYSTEM_TRACE -> "Capture System Activities"
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> "Track Memory Consumption"
      ProfilerTaskType.LEAKCANARY -> "Find Memory Leaks with LeakCanary"
      ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> "Find CPU Hotspots"
      ProfilerTaskType.CALLSTACK_SAMPLE -> "Find CPU Hotspots"
      ProfilerTaskType.NATIVE_ALLOCATIONS -> "Track Memory Consumption"
      ProfilerTaskType.UNSPECIFIED -> ""
    }

  fun getTaskShortName(taskType: ProfilerTaskType, isProfilerHomeTabV2Enabled: Boolean) =
    when (taskType) {
      ProfilerTaskType.HEAP_DUMP,
      ProfilerTaskType.SYSTEM_TRACE,
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS,
      ProfilerTaskType.NATIVE_ALLOCATIONS -> taskType.description
      ProfilerTaskType.LIVE_VIEW -> if (isProfilerHomeTabV2Enabled) "Live Telemetry" else ""
      ProfilerTaskType.LEAKCANARY -> if (isProfilerHomeTabV2Enabled) "LeakCanary" else ""
      ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> if (isProfilerHomeTabV2Enabled) "Method Trace" else taskType.description
      ProfilerTaskType.CALLSTACK_SAMPLE -> if (isProfilerHomeTabV2Enabled) "Stack Sampling" else "Callstack Sample"
      ProfilerTaskType.UNSPECIFIED -> ""
    }

  fun getTaskDescriptions(taskType: ProfilerTaskType) =
    when (taskType) {
      ProfilerTaskType.HEAP_DUMP -> "Captures all live objects on the Java/Kotlin heap at a single point in time."
      ProfilerTaskType.LIVE_VIEW -> "Displays a real-time timeline of CPU and memory usage as you interact with the app."
      ProfilerTaskType.SYSTEM_TRACE -> "Records a system-wide Perfetto trace of CPU, threads, frame timing, and other system events."
      ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS -> "Records each Java/Kotlin object allocation along with its allocating stack."
      ProfilerTaskType.LEAKCANARY -> "Detects and reports memory leaks using LeakCanary in debug builds."
      ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> "Records every Java/Kotlin method call with full entry and exit detail."
      ProfilerTaskType.CALLSTACK_SAMPLE -> "Periodically samples call stacks across Java/Kotlin and C++ code using simpleperf."
      ProfilerTaskType.NATIVE_ALLOCATIONS -> "Captures net native memory allocations over the recording period."
      else -> ""
    }

  fun getStartTaskErrorMessage(taskStartError: StartTaskSelectionErrorCode) =
    when (taskStartError) {
      StartTaskSelectionErrorCode.INVALID_DEVICE -> "No valid device is selected"
      StartTaskSelectionErrorCode.INVALID_PROCESS -> "No valid process is selected"
      StartTaskSelectionErrorCode.INVALID_TASK -> "No valid task is selected"
      StartTaskSelectionErrorCode.PREFERRED_PROCESS_NOT_SELECTED_FOR_STARTUP_TASK ->
        "Tasks configured to run at process start require " + "your app's process to be selected"

      StartTaskSelectionErrorCode.TASK_UNSUPPORTED_ON_STARTUP ->
        "The selected task does not support 'Start profiler task from process " + "start'"

      StartTaskSelectionErrorCode.TASK_FROM_PROCESS_START_USING_API_BELOW_MIN ->
        "The API level is too low to 'Start profiler task from " + "process start'"

      StartTaskSelectionErrorCode.TASK_FROM_NOW_USING_API_BELOW_MIN -> "The API level is too low to 'Start profiler task from now'"
      StartTaskSelectionErrorCode.TASK_FROM_NOW_USING_DEAD_PROCESS -> "'Start profiler task from now' requires a running process"
      StartTaskSelectionErrorCode.DEVICE_SELECTION_IS_OFFLINE -> "The selected device is offline"
      StartTaskSelectionErrorCode.TASK_REQUIRES_DEBUGGABLE_PROCESS ->
        "'Start profiler task from now' requires a running debuggable " + "process"

      StartTaskSelectionErrorCode.NO_STARTING_POINT_SELECTED -> "No task starting point selected"
      StartTaskSelectionErrorCode.LEAKCANARY_NOT_FOUND ->
        "Studio's LeakCanary integration library not detected in the process. Please try 'Start profiler task from process start'."
      StartTaskSelectionErrorCode.LEAKCANARY_NOT_FOUND_EXTERNAL_PROCESS ->
        "Studio's LeakCanary integration library not detected in the process. Please add the 'studio-leakcanary' dependency to the app's build."
      StartTaskSelectionErrorCode.LEAKCANARY_CHECK_IN_PROGRESS -> LEAKCANARY_CHECKING_PRESENCE
      StartTaskSelectionErrorCode.LEAKCANARY_CHECK_TIMEOUT -> LEAKCANARY_CHECK_TIMEOUT_MESSAGE
      StartTaskSelectionErrorCode.LEAKCANARY_REFLECTION_FAILED ->
        "Studio's LeakCanary integration library failed to attach. Required internal APIs are missing from the compiled app."
      StartTaskSelectionErrorCode.TASK_HAS_DEBUGGER_ATTACHED -> TASK_HAS_DEBUGGER_ATTACHED_MESSAGE
      StartTaskSelectionErrorCode.LEAKCANARY_NOT_SUPPORTED_FOR_PCC ->
        "LeakCanary profiling is not supported for apps containing Private Compute Core (PCC) components."
      StartTaskSelectionErrorCode.GENERAL_ERROR -> "This task cannot be run in this configuration"
    }

  fun getStartTaskErrorNotificationText(taskStartError: StartTaskSelectionError): String {
    val errorMessage = getStartTaskErrorMessage(taskStartError.startTaskSelectionErrorCode)
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
