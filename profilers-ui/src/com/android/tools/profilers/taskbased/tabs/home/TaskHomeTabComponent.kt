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
package com.android.tools.profilers.taskbased.tabs.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.taskbased.common.constants.strings.StringUtils
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import com.android.tools.profilers.taskbased.tabs.TaskTabComponent
import com.android.tools.profilers.taskbased.tabs.home.banner.HomeTabLiveTaskBanner
import com.android.tools.profilers.taskbased.tabs.home.processlist.ProcessList
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.TaskGridAndBars
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState

/**
 * Composable representing the Profiler Home tab. Displays an informational banner when a live profiler task is running in an unfocused
 * editor tab, along with the process list and task selection grid.
 */
@Composable
fun TaskHomeTab(taskHomeTabModel: TaskHomeTabModel, ideProfilerComponents: IdeProfilerComponents) {
  Column(modifier = Modifier.fillMaxSize()) {
    val unfocusedLiveTask by taskHomeTabModel.unfocusedLiveTaskInEditor.collectAsState()
    // Show notification banner if a live profiler task is running in an unfocused editor tab.
    unfocusedLiveTask?.let { taskType ->
      val taskName = StringUtils.getTaskTabTitle(taskType, taskHomeTabModel.profilers.ideServices.featureConfig.isProfilerHomeTabV2Enabled)
      HomeTabLiveTaskBanner(
        taskName = taskName,
        onReturnToTaskClick = { taskHomeTabModel.profilers.openTaskTab() },
      )
    }
    HorizontalSplitLayout(
      firstPaneMinWidth = 400.dp,
      secondPaneMinWidth = 250.dp,
      first = { ProcessList(taskHomeTabModel.processListModel) },
      second = { TaskGridAndBars(taskHomeTabModel, ideProfilerComponents) },
      state = rememberSplitLayoutState(.3f),
      modifier = Modifier.weight(1f),
    )
  }
}

class TaskHomeTabComponent(taskHomeTabModel: TaskHomeTabModel, ideProfilerComponents: IdeProfilerComponents) :
  TaskTabComponent({ TaskHomeTab(taskHomeTabModel, ideProfilerComponents) })
