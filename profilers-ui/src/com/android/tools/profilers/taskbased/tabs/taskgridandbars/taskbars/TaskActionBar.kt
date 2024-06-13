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
package com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.taskbased.common.buttons.OpenTaskButton
import com.android.tools.profilers.taskbased.common.buttons.StartTaskButton
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.TASK_ACTION_BAR_ACTION_HORIZONTAL_SPACE_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.TASK_ACTION_BAR_CONTENT_PADDING_DP
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import com.android.tools.profilers.taskbased.pastrecordings.PastRecordingsTabModel
import com.android.tools.profilers.taskbased.tabs.pastrecordings.recordinglist.RecordingActionGroup
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars.options.TaskRecordingTypeDropdown
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars.options.TaskStartingPointDropdown

/**
 * The action bar for performing a task from the profiler's past recordings tab. This action bar allows the user to perform a task using a
 * recording and task selection.
 */
@Composable
fun TaskActionBar(pastRecordingsTabModel: PastRecordingsTabModel, ideProfilerComponents: IdeProfilerComponents) {
  val recordingListModel = pastRecordingsTabModel.recordingListModel
  val selectedRecording by recordingListModel.selectedRecording.collectAsState()
  val isRecordingExportable = recordingListModel.isSelectedRecordingExportable()
  val isRecordingSelected = recordingListModel.isRecordingSelected()
  val selectedTaskType by pastRecordingsTabModel.taskGridModel.selectedTaskType.collectAsState()

  Row(
    modifier = Modifier.fillMaxWidth().padding(TASK_ACTION_BAR_CONTENT_PADDING_DP)
  ) {
    RecordingActionGroup(artifact = recordingListModel.exportableArtifact, isRecordingExportable = isRecordingExportable,
                         isRecordingSelected = isRecordingSelected,
                         doDeleteSelectedRecording = recordingListModel::doDeleteSelectedRecording,
                         profilers = recordingListModel.profilers, ideProfilerComponents = ideProfilerComponents)
    Spacer(modifier = Modifier.weight(1f))
    // The enter task button.
    OpenTaskButton(selectedTaskType = selectedTaskType, selectedRecording = selectedRecording,
                   onClick = pastRecordingsTabModel::onEnterTaskButtonClick)
  }
}

/**
 * The action bar for performing a task from the profiler's home tab. This action bar allows the user to perform a task using a device,
 * process, and task selection. It also allows the user to set the starting point of their task.
 */
@Composable
fun TaskActionBar(taskHomeTabModel: TaskHomeTabModel) {
  val profilingProcessStartingPoint by taskHomeTabModel.profilingProcessStartingPoint.collectAsState()
  val isProfilingProcessFromNowEnabled by taskHomeTabModel.isProfilingFromNowOptionEnabled.collectAsState()
  val isProfilingProcessFromProcessStartEnabled by taskHomeTabModel.isProfilingFromProcessStartOptionEnabled.collectAsState()
  val taskRecordingType by taskHomeTabModel.taskRecordingType.collectAsState()

  val taskGridModel = taskHomeTabModel.taskGridModel
  val selectedTaskType by taskGridModel.selectedTaskType.collectAsState()

  val processListModel = taskHomeTabModel.processListModel
  val selectedDevice by processListModel.selectedDevice.collectAsState()
  val selectedProcess by processListModel.selectedProcess.collectAsState()

  Row(modifier = Modifier.fillMaxWidth().padding(TASK_ACTION_BAR_CONTENT_PADDING_DP), verticalAlignment = Alignment.CenterVertically) {
    TaskStartingPointDropdown(profilingProcessStartingPoint, taskHomeTabModel::setProfilingProcessStartingPoint,
                              isProfilingProcessFromNowEnabled, isProfilingProcessFromProcessStartEnabled)
    if (TaskHomeTabModel.doesTaskHaveRecordingTypes(selectedTaskType)) {
      Spacer(modifier = Modifier.width(TASK_ACTION_BAR_ACTION_HORIZONTAL_SPACE_DP))
      TaskRecordingTypeDropdown(taskRecordingType, taskHomeTabModel::setTaskRecordingType)
    }
    Spacer(modifier = Modifier.weight(1f))
    // The start task button.
    StartTaskButton(selectedTaskType = selectedTaskType, selectedDevice = selectedDevice, selectedProcess = selectedProcess,
                    profilingProcessStartingPoint = profilingProcessStartingPoint,
                    canStartTask = taskHomeTabModel::canStartTask,
                    onClick = taskHomeTabModel::onEnterTaskButtonClick)
  }
}