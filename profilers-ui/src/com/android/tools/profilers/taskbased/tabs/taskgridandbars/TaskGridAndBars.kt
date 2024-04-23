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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.taskbased.common.dividers.ToolWindowHorizontalDivider
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars.TaskActionBar
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars.TopBar
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskgrid.TaskGrid

@Composable
private fun TaskGridAndBarsContainer(taskGrid: @Composable () -> Unit,
                                     topBar: @Composable () -> Unit,
                                     taskActionBar: @Composable () -> Unit,
                                     modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    topBar()
    ToolWindowHorizontalDivider()
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
      taskGrid()
    }
    ToolWindowHorizontalDivider()
    taskActionBar()
  }
}

@Composable
fun TaskGridAndBars(taskHomeTabModel: TaskHomeTabModel,
                    ideProfilerComponents: IdeProfilerComponents,
                    modifier: Modifier) {
  val taskTypes = taskHomeTabModel.taskHandlers.keys.toList()
  val profilers = taskHomeTabModel.profilers
  val taskGridModel = taskHomeTabModel.taskGridModel

  TaskGridAndBarsContainer(
    taskGrid = {
      TaskGrid(taskGridModel = taskGridModel, taskTypes = taskTypes)
    },
    topBar = { TopBar(profilers, ideProfilerComponents) },
    taskActionBar = {
      TaskActionBar(taskHomeTabModel)
    },
    modifier = modifier)
}