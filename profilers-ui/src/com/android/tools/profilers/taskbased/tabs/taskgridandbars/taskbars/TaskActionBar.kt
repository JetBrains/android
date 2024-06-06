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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_ACTION_BAR_ACTION_HORIZONTAL_SPACE_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_ACTION_BAR_CONTENT_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_ACTION_BAR_FULL_CONTENT_MIN_WIDTH_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_NOTIFICATION_CONTAINER_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.getStartTaskErrorMessage
import com.android.tools.profilers.taskbased.home.StartTaskSelectionError
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.STARTUP_TASK_ERRORS
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.canStartTask
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.canTaskStartFromProcessStart
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.getStartTaskError
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.isProfileablePreferredButNotPresent
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.isSelectedProcessPreferred
import com.android.tools.profilers.taskbased.pastrecordings.PastRecordingsTabModel
import com.android.tools.profilers.taskbased.tabs.pastrecordings.recordinglist.RecordingActionGroup
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars.notifications.ProfileablePreferredWarning
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars.notifications.StartTaskError
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars.options.TaskRecordingTypeDropdown
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars.options.TaskStartingPointDropdown
import org.jetbrains.jewel.foundation.theme.JewelTheme

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

  Box(
    modifier = Modifier.fillMaxWidth().padding(start = TASK_ACTION_BAR_CONTENT_PADDING_DP)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterStart)) {
      RecordingActionGroup(artifact = recordingListModel.exportableArtifact, isRecordingExportable = isRecordingExportable,
                           isRecordingSelected = isRecordingSelected,
                           doDeleteSelectedRecording = recordingListModel::doDeleteSelectedRecording,
                           profilers = recordingListModel.profilers, ideProfilerComponents = ideProfilerComponents)
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterEnd).background(
      color = JewelTheme.globalColors.panelBackground.copy(alpha = 1.0f)).padding(TASK_NOTIFICATION_CONTAINER_PADDING_DP)) {
      // The enter task button.
      OpenTaskButton(selectedTaskType = selectedTaskType, selectedRecording = selectedRecording,
                     onClick = pastRecordingsTabModel::onEnterTaskButtonClick)
    }
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

  val profilers = taskHomeTabModel.profilers

  val canStartTask = canStartTask(selectedTaskType, selectedDevice, selectedProcess, profilingProcessStartingPoint, profilers)
  BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(start = TASK_ACTION_BAR_CONTENT_PADDING_DP)) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterStart)) {
      val canStartTaskFromProcessStart = canTaskStartFromProcessStart(selectedTaskType, selectedDevice, selectedProcess, profilers)
      val processStartDisabledReason =
        if (canStartTaskFromProcessStart) null else {
          // Only show start task from process start error message if it's a startup task error.
          getStartTaskError(selectedTaskType, selectedDevice, selectedProcess, TaskHomeTabModel.ProfilingProcessStartingPoint.PROCESS_START,
                            profilers).takeIf { STARTUP_TASK_ERRORS.contains(it) }
        }
      TaskStartingPointDropdown(profilingProcessStartingPoint, taskHomeTabModel::setProfilingProcessStartingPoint,
                                isProfilingProcessFromNowEnabled, isProfilingProcessFromProcessStartEnabled, processStartDisabledReason)
      if (TaskHomeTabModel.doesTaskHaveRecordingTypes(selectedTaskType)) {
        Spacer(modifier = Modifier.width(TASK_ACTION_BAR_ACTION_HORIZONTAL_SPACE_DP))
        TaskRecordingTypeDropdown(taskRecordingType, taskHomeTabModel::setTaskRecordingType)
      }
    }

    val isCollapsed = maxWidth < TASK_ACTION_BAR_FULL_CONTENT_MIN_WIDTH_DP
    val isProfileablePreferredButNotPresent = isProfileablePreferredButNotPresent(selectedTaskType, selectedProcess,
                                                                                  profilingProcessStartingPoint)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterEnd).background(
      color = JewelTheme.globalColors.panelBackground.copy(alpha = 1.0f)).padding(TASK_NOTIFICATION_CONTAINER_PADDING_DP),
        horizontalArrangement = Arrangement.spacedBy(TASK_ACTION_BAR_ACTION_HORIZONTAL_SPACE_DP)) {
      if (!canStartTask) {
        val startTaskError = getStartTaskError(selectedTaskType, selectedDevice, selectedProcess, profilingProcessStartingPoint,
                                               profilers)
        val startTaskErrorMessage = getStartTaskErrorMessage(startTaskError)
        StartTaskError(startTaskErrorMessage, isCollapsed)
      }
      else if (isProfileablePreferredButNotPresent) {
        ProfileablePreferredWarning(isSelectedProcessPreferred(selectedProcess, profilers), isCollapsed)
      }
      // The start task button.
      StartTaskButton(canStartTask = canStartTask, isProfileablePreferredButNotPresent = isProfileablePreferredButNotPresent,
                      onClick = taskHomeTabModel::onEnterTaskButtonClick)
    }
  }
}
