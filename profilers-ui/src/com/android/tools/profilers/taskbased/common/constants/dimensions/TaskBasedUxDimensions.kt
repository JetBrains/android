/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.common.constants.dimensions

import androidx.compose.ui.unit.dp

object TaskBasedUxDimensions {
  // Selection panel (process and recording list) minimum and maximum ratio in horizontal split divider component.
  const val SELECTION_PANEL_MIN_RATIO_FLOAT = 0.275f
  const val SELECTION_PANEL_MAX_RATIO_FLOAT = 0.35f

  // Size of each Task grid item in the Task selection grid. This includes both the task icon and title.
  val TASK_WIDTH_DP = 150.dp
  val TASK_GRID_HORIZONTAL_SPACE_DP = 25.dp
  val TASK_GRID_VERTICAL_SPACE_DP = 10.dp
  val TASK_GRID_HORIZONTAL_PADDING_DP = 50.dp
  const val MAX_NUM_TASKS_IN_ROW = 5

  // Task tooltip width
  val TASK_TOOLTIP_WIDTH_DP = 200.dp

  // Common table dimensions
  val TABLE_ROW_HEIGHT_DP = 24.dp
  val TABLE_ROW_HORIZONTAL_PADDING_DP = 5.dp

  // Process table dimensions
  val PID_COL_WIDTH_DP = 55.dp
  val MANIFEST_CONFIG_COL_WIDTH_DP = 175.dp

  // Common dropdown dimensions
  val DROPDOWN_HORIZONTAL_PADDING_DP = 5.dp
  val DROPDOWN_PROMPT_HORIZONTAL_SPACE_DP = 7.5.dp

  // Device selection dropdown and restart buttons content padding
  val DEVICE_SELECTION_DROPDOWN_VERTICAL_PADDING_DP = 10.dp
  val DEVICE_SELECTION_HORIZONTAL_PADDING_DP = 10.dp
  val DEVICE_SELECTION_VERTICAL_PADDING_DP = 5.dp
  val DEVICE_SELECTION_TITLE_HORIZONTAL_SPACE_DP = 5.dp

  // Recording table dimension
  val RECORDING_TIME_COL_WIDTH_DP = 150.dp
  val RECORDING_TASKS_COL_WIDTH_DP = 200.dp

  // Task action bar content padding.
  val TASK_ACTION_BAR_CONTENT_PADDING_DP = 5.dp
  val TASK_ACTION_BAR_ACTION_HORIZONTAL_SPACE_DP = 10.dp
  val TASK_ACTION_BAR_FULL_CONTENT_MIN_WIDTH_DP = 1200.dp

  // Top bar dimensions
  val TOP_BAR_HEIGHT_DP = 30.dp
  val TOP_BAR_START_PADDING_DP = 10.dp
  val TOP_BAR_END_PADDING_DP = 5.dp

  // Multiline tooltip dimensions
  val TOOLTIP_VERTICAL_SPACING_DP = 5.dp
  val TOOLTIP_MAX_WIDTH_DP = 300.dp

  // Task notification dimensions
  val TASK_NOTIFICATION_CONTAINER_PADDING_DP = 5.dp
  val TASK_NOTIFICATION_ICON_TEXT_HORIZONTAL_SPACE_DP = 5.dp
}