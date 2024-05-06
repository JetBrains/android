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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.TASK_WIDTH_DP
import com.android.tools.profilers.taskbased.tasks.TaskGridModel
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
      modifier = Modifier
        .align(Alignment.Center)
        .padding(start = 50.dp, end = 50.dp),
      horizontalArrangement = Arrangement.spacedBy(20.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      taskGridContent(selectedTask, this)
    }
    VerticalScrollbar(
      adapter = rememberScrollbarAdapter(listState),
      modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
    )
  }
}

@Composable
fun TaskGrid(taskGridModel: TaskGridModel,
             selectedDevice: Common.Device,
             selectedProcess: Common.Process,
             taskHandlers: Map<ProfilerTaskType, ProfilerTaskHandler>) {
  TaskGridContainer(taskGridModel) { selectedTask: ProfilerTaskType, lazyGridScope: LazyGridScope ->
    with(lazyGridScope) {
      items(taskHandlers.entries.toList()) { (taskType, taskHandler) ->
        val isTaskSupported = taskHandler.supportsDeviceAndProcess(selectedDevice, selectedProcess)
        // If the task is not supported/enabled, render it for the process-based task selection, but display it as disabled.
        taskType.let {
          TaskGridItem(
            task = it,
            isSelectedTask = it == selectedTask,
            onTaskSelection = taskGridModel::onTaskSelection,
            isTaskEnabled = isTaskSupported
          )
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
      items(taskHandlers.entries.toList().filter {
        selectedRecording != null && TaskSupportUtils.isTaskSupportedByRecording(it.key, it.value, selectedRecording)
      }) { (taskType, _) ->
        taskType.let {
          TaskGridItem(
            task = it,
            isSelectedTask = it == selectedTask,
            onTaskSelection = taskGridModel::onTaskSelection,
            // If the task item is being rendered, the task should be enabled in the recording-based task grid.
            isTaskEnabled = true
          )
        }
      }
    }
  }
}