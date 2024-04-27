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
package com.android.tools.profilers.taskbased.tabs.pastrecordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.SELECTION_PANEL_MAX_RATIO_FLOAT
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.SELECTION_PANEL_MIN_RATIO_FLOAT
import com.android.tools.profilers.taskbased.pastrecordings.PastRecordingsTabModel
import com.android.tools.profilers.taskbased.tabs.TaskTabComponent
import com.android.tools.profilers.taskbased.tabs.pastrecordings.recordinglist.RecordingList
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.TaskGridAndBars
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout

@Composable
fun TaskPastRecordingsTab(pastRecordingsTabModel: PastRecordingsTabModel, ideProfilerComponents: IdeProfilerComponents) {
  Column(
    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val taskGridModel = pastRecordingsTabModel.taskGridModel
    val taskHandlers = pastRecordingsTabModel.taskHandlers
    val recordingListModel = pastRecordingsTabModel.recordingListModel
    val selectedRecording by recordingListModel.selectedRecording.collectAsState()

    HorizontalSplitLayout(
      minRatio = SELECTION_PANEL_MIN_RATIO_FLOAT,
      maxRatio = SELECTION_PANEL_MAX_RATIO_FLOAT,
      dividerThickness = TaskBasedUxDimensions.PROFILER_TOOLWINDOW_DIVIDER_THICKNESS_DP,
      first = {
        RecordingList(recordingListModel, ideProfilerComponents, it)
      },
      second = {
        TaskGridAndBars(taskGridModel, selectedRecording, taskHandlers, pastRecordingsTabModel::onEnterTaskButtonClick, it)
      }
    )
  }
}

class TaskPastRecordingsTabComponent(pastRecordingsTabModel: PastRecordingsTabModel,
                                     ideProfilerComponents: IdeProfilerComponents) : TaskTabComponent(
  { TaskPastRecordingsTab(pastRecordingsTabModel, ideProfilerComponents) })
