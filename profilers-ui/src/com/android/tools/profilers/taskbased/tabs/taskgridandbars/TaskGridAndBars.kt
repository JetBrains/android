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
package com.android.tools.profilers.taskbased.tabs.taskgridandbars

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.taskbased.common.dividers.ToolWindowHorizontalDivider
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars.TaskActionBar
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars.TopBar
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskgrid.TaskGrid
import com.android.tools.profilers.taskbased.tasks.TaskGridModel
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler

@Composable
private fun TaskGridAndBarsContainer(taskGrid: @Composable () -> Unit,
                                     topBar: @Composable () -> Unit,
                                     taskActionBar: @Composable () -> Unit,
                                     modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    topBar()
    ToolWindowHorizontalDivider()
    Box(modifier = Modifier.weight(1f)) {
      taskGrid()
    }
    ToolWindowHorizontalDivider()
    taskActionBar()
  }
}

@Composable
fun TaskGridAndBars(taskGridModel: TaskGridModel,
                    selectedDevice: Common.Device,
                    selectedProcess: Common.Process,
                    taskHandlers: Map<ProfilerTaskType, ProfilerTaskHandler>,
                    onEnterProfilerTask: () -> Unit,
                    profilers: StudioProfilers,
                    ideProfilerComponents: IdeProfilerComponents,
                    modifier: Modifier) {
  val selectedTaskType by taskGridModel.selectedTaskType.collectAsState()
  val canStartTask = selectedDevice != Common.Device.getDefaultInstance()
                     && selectedProcess != Common.Process.getDefaultInstance()
                     && selectedTaskType != ProfilerTaskType.UNSPECIFIED
  TaskGridAndBarsContainer(
    taskGrid = {
      TaskGrid(taskGridModel = taskGridModel, selectedDevice = selectedDevice, selectedProcess = selectedProcess,
               taskHandlers = taskHandlers)
    },
    topBar = { TopBar(profilers, ideProfilerComponents) },
    taskActionBar = { TaskActionBar(canStartTask, onEnterProfilerTask, true) },
    modifier = modifier)
}

@Composable
fun TaskGridAndBars(taskGridModel: TaskGridModel,
                    selectedRecording: SessionItem?,
                    taskHandlers: Map<ProfilerTaskType, ProfilerTaskHandler>,
                    onEnterProfilerTask: () -> Unit,
                    modifier: Modifier) {
  val selectedTaskType by taskGridModel.selectedTaskType.collectAsState()
  val canStartTask = selectedRecording != null && selectedTaskType != ProfilerTaskType.UNSPECIFIED
  TaskGridAndBarsContainer(
    taskGrid = { TaskGrid(taskGridModel = taskGridModel, selectedRecording = selectedRecording, taskHandlers = taskHandlers) },
    topBar = { TopBar() },
    taskActionBar = { TaskActionBar(canStartTask, onEnterProfilerTask, false) },
    modifier = modifier)
}