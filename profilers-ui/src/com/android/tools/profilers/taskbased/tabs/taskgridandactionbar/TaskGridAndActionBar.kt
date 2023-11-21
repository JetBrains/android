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
package com.android.tools.profilers.taskbased.tabs.taskgridandactionbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.PROFILER_TOOLWINDOW_DIVIDER_THICKNESS_DP
import com.android.tools.profilers.taskbased.tabs.taskgridandactionbar.actionbar.TaskActionBar
import com.android.tools.profilers.taskbased.tabs.taskgridandactionbar.taskgrid.TaskGrid
import com.android.tools.profilers.taskbased.tasks.TaskGridModel
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider

@Composable
private fun TaskGridActionBarContainer(taskGrid: @Composable () -> Unit, taskActionBar: @Composable () -> Unit, modifier: Modifier) {
  Column(modifier = modifier) {
    Box(modifier = Modifier.weight(1f)) {
      taskGrid()
    }
    Divider(thickness = PROFILER_TOOLWINDOW_DIVIDER_THICKNESS_DP, modifier = Modifier.fillMaxWidth(), orientation = Orientation.Horizontal)
    taskActionBar()
  }
}

@Composable
fun TaskGridAndActionBar(taskGridModel: TaskGridModel,
                         selectedDevice: Common.Device,
                         selectedProcess: Common.Process,
                         taskHandlers: Map<ProfilerTaskType, ProfilerTaskHandler>,
                         onEnterProfilerTask: () -> Unit,
                         modifier: Modifier = Modifier) {
  val selectedTaskType by taskGridModel.selectedTaskType.collectAsState()
  val canStartTask = selectedDevice != Common.Device.getDefaultInstance()
                     && selectedProcess != Common.Process.getDefaultInstance()
                     && selectedTaskType != ProfilerTaskType.UNSPECIFIED
  TaskGridActionBarContainer(
    taskGrid = {
      TaskGrid(taskGridModel = taskGridModel, selectedDevice = selectedDevice, selectedProcess = selectedProcess,
               taskHandlers = taskHandlers)
    },
    taskActionBar = { TaskActionBar(canStartTask, onEnterProfilerTask, true) }, modifier)
}

@Composable
fun TaskGridAndActionBar(taskGridModel: TaskGridModel,
                         selectedRecording: SessionItem?,
                         taskHandlers: Map<ProfilerTaskType, ProfilerTaskHandler>,
                         onEnterProfilerTask: () -> Unit,
                         modifier: Modifier = Modifier) {
  val selectedTaskType by taskGridModel.selectedTaskType.collectAsState()
  val canStartTask = selectedRecording != null && selectedTaskType != ProfilerTaskType.UNSPECIFIED
  TaskGridActionBarContainer(
    taskGrid = { TaskGrid(taskGridModel = taskGridModel, selectedRecording = selectedRecording, taskHandlers = taskHandlers) },
    taskActionBar = { TaskActionBar(canStartTask, onEnterProfilerTask, false) }, modifier)
}