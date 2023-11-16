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
  const val NO_SUPPORTED_DEVICES = "No supported devices"
  const val DEVICE_SELECTION_PROMPT = "Please select a device"

  // Export and import strings
  const val IMPORT_RECORDING_DESC = "Import recording"
  const val EXPORT_RECORDING_DESC = "Export recording"
  const val EXPORT_RECORDING_DISABLED_TOOLTIP = "Recording is not exportable"

  // Delete recording strings
  const val DELETE_RECORDING_DESC = "Delete recording"
  const val DELETE_RECORDING_DISABLED_TOOLTIP = "Recording is not deletable"
}