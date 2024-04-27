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

import androidx.compose.ui.unit.dp

object TaskBasedUxDimensions {
  // Divider thickness for all top-level dividers used in the toolwindow.
  val PROFILER_TOOLWINDOW_DIVIDER_THICKNESS_DP = 1.dp
  // Selection panel (process and recording list) minimum and maximum ratio in horizontal split divider component.
  const val SELECTION_PANEL_MIN_RATIO_FLOAT = 0.35f
  const val SELECTION_PANEL_MAX_RATIO_FLOAT = 0.5f

  // Size of each Task grid item in the Task selection grid. This includes both the task icon and title.
  val TASK_WIDTH_DP = 150.dp
  // Size of the task grid item icon.
  const val ICON_SIZE_PX = 75

  // Common table dimensions
  val TABLE_ROW_HEIGHT_DP = 24.dp
  val TABLE_ROW_HORIZONTAL_PADDING_DP = 5.dp

  // Process table dimensions
  val PID_COL_WIDTH_DP = 55.dp
  val MANIFEST_CONFIG_COL_WIDTH_DP = 175.dp

  // Device selection dropdown and restart buttons content padding
  val DEVICE_SELECTION_DROPDOWN_CONTENT_PADDING_DP = 5.dp
  val RESTART_ACTION_CONTENT_PADDING_DP = 5.dp

  // Recording table dimension
  val RECORDING_TIME_COL_WIDTH_DP = 150.dp
  val RECORDING_TASKS_COL_WIDTH_DP = 200.dp

  // Recording list actions bar content padding
  val RECORDING_LIST_ACTIONS_BAR_CONTENT_PADDING_DP = 5.dp

  // Task action bar content padding.
  val TASK_ACTION_BAR_CONTENT_PADDING_DP = 5.dp

  // Top bar dimensions
  val TOP_BAR_HEIGHT_DP = 30.dp
  val TOP_BAR_START_PADDING_DP = 10.dp
  val TOP_BAR_END_PADDING_DP = 5.dp
}