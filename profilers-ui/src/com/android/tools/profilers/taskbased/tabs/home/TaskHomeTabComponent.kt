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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import com.android.tools.profilers.taskbased.tabs.TaskTabComponent
import com.android.tools.profilers.taskbased.tabs.home.processlist.ProcessList
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.TaskGridAndBars
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState

@Composable
fun TaskHomeTab(taskHomeTabModel: TaskHomeTabModel, ideProfilerComponents: IdeProfilerComponents) {
  HorizontalSplitLayout(
    firstPaneMinWidth = 400.dp,
    secondPaneMinWidth = 250.dp,
    first = { ProcessList(taskHomeTabModel.processListModel) },
    second = { TaskGridAndBars(taskHomeTabModel, ideProfilerComponents) },
    state = rememberSplitLayoutState(.3f),
    modifier = Modifier.fillMaxSize(),
  )
}

class TaskHomeTabComponent(
  taskHomeTabModel: TaskHomeTabModel,
  ideProfilerComponents: IdeProfilerComponents,
) : TaskTabComponent({ TaskHomeTab(taskHomeTabModel, ideProfilerComponents) })