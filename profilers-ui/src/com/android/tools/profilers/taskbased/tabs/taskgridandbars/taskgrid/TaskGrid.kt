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
package com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskgrid

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.MAX_NUM_TASKS_IN_ROW
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_GRID_HORIZONTAL_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_GRID_HORIZONTAL_PADDING_V2_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_GRID_HORIZONTAL_SPACE_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_GRID_HORIZONTAL_SPACE_V2_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_GRID_VERTICAL_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_GRID_VERTICAL_PADDING_V2_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_GRID_VERTICAL_SPACE_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_GRID_VERTICAL_SPACE_V2_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_WIDTH_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_WIDTH_V2_DP
import com.android.tools.profilers.taskbased.task.TaskGridModel
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.TaskSupportUtils
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler

@Composable
private fun TaskGridContainer(taskGridModel: TaskGridModel, taskGridContent: (ProfilerTaskType, LazyGridScope) -> Unit) {
  val listState = rememberLazyGridState()
  val selectedTask by taskGridModel.selectedTaskType.collectAsState()

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(TASK_WIDTH_DP),
      state = listState,
      modifier =
        Modifier.align(Alignment.Center)
          .widthIn(
            max =
              ((TASK_WIDTH_DP * MAX_NUM_TASKS_IN_ROW) +
                (TASK_GRID_HORIZONTAL_SPACE_DP * (MAX_NUM_TASKS_IN_ROW - 1)) +
                (TASK_GRID_HORIZONTAL_PADDING_DP * 2))
          )
          .padding(horizontal = TASK_GRID_HORIZONTAL_PADDING_DP),
      horizontalArrangement = Arrangement.spacedBy(TASK_GRID_HORIZONTAL_SPACE_DP),
      verticalArrangement = Arrangement.spacedBy(TASK_GRID_VERTICAL_SPACE_DP),
      contentPadding = PaddingValues(vertical = TASK_GRID_VERTICAL_PADDING_DP),
    ) {
      taskGridContent(selectedTask, this)
    }
    VerticalScrollbar(adapter = rememberScrollbarAdapter(listState), modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd))
  }
}

@Composable
fun TaskGrid(taskGridModel: TaskGridModel, taskTypes: List<ProfilerTaskType>) {
  val sortedTaskTypes = taskTypes.sortedBy { it.rank }
  TaskGridContainer(taskGridModel) { selectedTask: ProfilerTaskType, lazyGridScope: LazyGridScope ->
    with(lazyGridScope) {
      items(sortedTaskTypes) { taskType ->
        taskType.let { task ->
          TaskGridItem(task = task, isSelectedTask = task == selectedTask, onTaskSelection = { taskGridModel.onTaskSelection(it) })
        }
      }
    }
  }
}

@Composable
fun TaskGrid(taskGridModel: TaskGridModel, selectedRecording: SessionItem?, taskHandlers: Map<ProfilerTaskType, ProfilerTaskHandler>) {
  TaskGridContainer(taskGridModel) { selectedTask: ProfilerTaskType, lazyGridScope: LazyGridScope ->
    with(lazyGridScope) {
      // If the task is not supported/enabled, do not render it for the recording-based task selection.
      items(
        taskHandlers.entries.toList().filter {
          selectedRecording != null && TaskSupportUtils.isTaskSupportedByRecording(it.value, selectedRecording)
        }
      ) { (taskType, _) ->
        taskType.let { TaskGridItem(task = it, isSelectedTask = it == selectedTask, onTaskSelection = taskGridModel::onTaskSelection) }
      }
    }
  }
}

@Composable
fun TaskGridV2(taskGridModel: TaskGridModel, taskTypes: List<ProfilerTaskType>) {
  val sortedTaskTypes = taskTypes.sortedBy { it.rank }
  val selectedTask by taskGridModel.selectedTaskType.collectAsState()

  val scrollState = rememberScrollState()

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    BoxWithConstraints(
      modifier =
        Modifier.align(Alignment.Center)
          .fillMaxWidth()
          .widthIn(
            max =
              ((TASK_WIDTH_V2_DP * MAX_NUM_TASKS_IN_ROW) +
                (TASK_GRID_HORIZONTAL_SPACE_V2_DP * (MAX_NUM_TASKS_IN_ROW - 1)) +
                (TASK_GRID_HORIZONTAL_PADDING_V2_DP * 2))
          )
          .padding(horizontal = TASK_GRID_HORIZONTAL_PADDING_V2_DP)
          .verticalScroll(scrollState)
          .padding(vertical = TASK_GRID_VERTICAL_PADDING_V2_DP)
    ) {
      val hSpacing = TASK_GRID_HORIZONTAL_SPACE_V2_DP
      val vSpacing = TASK_GRID_VERTICAL_SPACE_V2_DP
      val columns =
        ((maxWidth.value + hSpacing.value) / (TASK_WIDTH_V2_DP.value + hSpacing.value)).toInt().coerceIn(1, MAX_NUM_TASKS_IN_ROW)

      Layout(
        content = {
          sortedTaskTypes.forEach { task ->
            TaskGridItemV2(task = task, isSelectedTask = task == selectedTask, onTaskSelection = { taskGridModel.onTaskSelection(it) })
          }
        }
      ) { measurables, constraints ->
        val colWidth = (constraints.maxWidth - (columns - 1) * hSpacing.roundToPx()) / columns

        // Step 1: Find the maximum height among all tiles using intrinsic measurements
        val maxTileHeight = measurables.maxOfOrNull { it.maxIntrinsicHeight(colWidth) } ?: 0

        // Step 2: Measure all children ONCE with the fixed max height
        val placeables =
          measurables.map {
            it.measure(constraints.copy(minWidth = colWidth, maxWidth = colWidth, minHeight = maxTileHeight, maxHeight = maxTileHeight))
          }

        val rows = (placeables.size + columns - 1) / columns
        val totalHeight = if (rows == 0) 0 else rows * maxTileHeight + (rows - 1) * vSpacing.roundToPx()

        layout(constraints.maxWidth, totalHeight) {
          placeables.forEachIndexed { index, placeable ->
            val row = index / columns
            val col = index % columns
            val x = col * (colWidth + hSpacing.roundToPx())
            val y = row * (maxTileHeight + vSpacing.roundToPx())
            placeable.placeRelative(x, y)
          }
        }
      }
    }

    VerticalScrollbar(adapter = rememberScrollbarAdapter(scrollState), modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd))
  }
}
