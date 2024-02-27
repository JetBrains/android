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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.android.tools.profilers.taskbased.common.buttons.OpenTaskButton
import com.android.tools.profilers.taskbased.common.buttons.StartTaskButton
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.TASK_ACTION_BAR_CONTENT_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel.ProfilingProcessStartingPoint
import com.android.tools.profilers.taskbased.pastrecordings.PastRecordingsTabModel
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text

/**
 * The action bar for performing a task from the profiler's past recordings tab. This action bar allows the user to perform a task using a
 * recording and task selection.
 */
@Composable
fun TaskActionBar(pastRecordingsTabModel: PastRecordingsTabModel) {
  val selectedRecording by pastRecordingsTabModel.recordingListModel.selectedRecording.collectAsState()
  val selectedTaskType by pastRecordingsTabModel.taskGridModel.selectedTaskType.collectAsState()

  Row(
    modifier = Modifier.fillMaxWidth().padding(TASK_ACTION_BAR_CONTENT_PADDING_DP)
  ) {
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

  val taskGridModel = taskHomeTabModel.taskGridModel
  val selectedTaskType by taskGridModel.selectedTaskType.collectAsState()

  val processListModel = taskHomeTabModel.processListModel
  val selectedDevice by processListModel.selectedDevice.collectAsState()
  val selectedProcess by processListModel.selectedProcess.collectAsState()

  Row(
    modifier = Modifier.fillMaxWidth().padding(TASK_ACTION_BAR_CONTENT_PADDING_DP),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TaskStartingPointDropdown(profilingProcessStartingPoint, taskHomeTabModel::setProfilingProcessStartingPoint,
                              isProfilingProcessFromNowEnabled, isProfilingProcessFromProcessStartEnabled)
    Spacer(modifier = Modifier.weight(1f))
    // The start task button.
    StartTaskButton(selectedTaskType = selectedTaskType, selectedDevice = selectedDevice, selectedProcess = selectedProcess,
                    profilingProcessStartingPoint = profilingProcessStartingPoint,
                    canStartTask = taskHomeTabModel::canStartTask,
                    onClick = taskHomeTabModel::onEnterTaskButtonClick)
  }
}

@Composable
private fun TaskStartingPointDropdown(profilingProcessStartingPoint: ProfilingProcessStartingPoint,
                                      setProfilingProcessStartingPoint: (ProfilingProcessStartingPoint) -> Unit,
                                      isProfilingProcessFromNowEnabled: Boolean,
                                      isProfilingProcessFromProcessStartEnabled: Boolean) {
  val isDropdownEnabled = isProfilingProcessFromNowEnabled || isProfilingProcessFromProcessStartEnabled

  Row (verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(TaskBasedUxStrings.STARTING_POINT_DROPDOWN_TITLE)
    Dropdown(modifier = Modifier.testTag("TaskStartingPointDropdown"), enabled = isDropdownEnabled, menuContent = {
      selectableItem(enabled = isProfilingProcessFromNowEnabled,
                     selected = profilingProcessStartingPoint == ProfilingProcessStartingPoint.NOW,
                     onClick = { setProfilingProcessStartingPoint(ProfilingProcessStartingPoint.NOW) }) {
        TaskStartingPointOption(TaskBasedUxStrings.NOW_STARTING_POINT_DROPDOWN_OPTION, Modifier.testTag("TaskStartingPointOption"))
      }
      selectableItem(enabled = isProfilingProcessFromProcessStartEnabled,
                     selected = profilingProcessStartingPoint == ProfilingProcessStartingPoint.PROCESS_START,
                     onClick = { setProfilingProcessStartingPoint(ProfilingProcessStartingPoint.PROCESS_START) }) {
        TaskStartingPointOption(TaskBasedUxStrings.STARTUP_STARTING_POINT_DROPDOWN_OPTION, Modifier.testTag("TaskStartingPointOption"))
      }
    }) {
      val selectionText = when (profilingProcessStartingPoint) {
        ProfilingProcessStartingPoint.UNSPECIFIED -> "Please select a starting point"
        ProfilingProcessStartingPoint.NOW ->  TaskBasedUxStrings.NOW_STARTING_POINT_DROPDOWN_OPTION
        ProfilingProcessStartingPoint.PROCESS_START -> TaskBasedUxStrings.STARTUP_STARTING_POINT_DROPDOWN_OPTION
      }
      TaskStartingPointOption(selectionText)
    }
  }
}

@Composable
private fun TaskStartingPointOption(optionText: String, modifier: Modifier = Modifier) {
  Text(text = optionText, fontSize = TextUnit(14f, TextUnitType.Sp), lineHeight = TextUnit(18f, TextUnitType.Sp), maxLines = 1,
       overflow = TextOverflow.Ellipsis,
       modifier = modifier.padding(horizontal = TaskBasedUxDimensions.DEVICE_SELECTION_DROPDOWN_HORIZONTAL_PADDING_DP))
}