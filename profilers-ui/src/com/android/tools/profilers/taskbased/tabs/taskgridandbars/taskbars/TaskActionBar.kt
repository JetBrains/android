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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.android.tools.profilers.taskbased.common.buttons.OpenTaskButton
import com.android.tools.profilers.taskbased.common.buttons.StartTaskButton
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.TASK_ACTION_BAR_CONTENT_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxStrings
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text

/**
 * The action bar for performing a task from the profiler's past recordings tab. This action bar allows the user to perform a task using a
 * recording and task selection.
 */
@Composable
fun TaskActionBar(canStartTask: Boolean, onEnterProfilerTask: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(TASK_ACTION_BAR_CONTENT_PADDING_DP)
  ) {
    Spacer(modifier = Modifier.weight(1f))
    // The enter task button.
    OpenTaskButton(isEnabled = canStartTask, onClick = onEnterProfilerTask)
  }
}

/**
 * The action bar for performing a task from the profiler's home tab. This action bar allows the user to perform a task using a device,
 * process, and task selection. It also allows the user to set the starting point of their task.
 */
@Composable
fun TaskActionBar(canStartTask: Boolean,
                  onEnterProfilerTask: () -> Unit,
                  isProfilingFromProcessStart: Boolean,
                  setIsProfilingFromProcessStart: (Boolean) -> Unit,
                  isPreferredProcessSelected: Boolean,
                  isTaskSupportedOnStartup: Boolean) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(TASK_ACTION_BAR_CONTENT_PADDING_DP),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TaskStartingPointDropdown(isProfilingFromProcessStart, setIsProfilingFromProcessStart, isPreferredProcessSelected,
                              isTaskSupportedOnStartup)
    Spacer(modifier = Modifier.weight(1f))
    // The start task button.
    StartTaskButton(isEnabled = canStartTask, onClick = onEnterProfilerTask)
  }
}

@Composable
private fun TaskStartingPointDropdown(isProfilingFromProcessStart: Boolean,
                                      setIsProfilingFromProcessStart: (Boolean) -> Unit,
                                      isPreferredProcessSelected: Boolean,
                                      isTaskSupportedOnStartup: Boolean) {
  Row (verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(TaskBasedUxStrings.STARTING_POINT_DROPDOWN_TITLE)
    Dropdown(menuContent = {
      selectableItem(selected = !isProfilingFromProcessStart, onClick = { setIsProfilingFromProcessStart(false) }) {
        TaskStartingPointOption(false)
      }
      if (isPreferredProcessSelected && isTaskSupportedOnStartup) {
        selectableItem(selected = isProfilingFromProcessStart, onClick = { setIsProfilingFromProcessStart(true) }) {
          TaskStartingPointOption(true)
        }
      }
    }) {
      TaskStartingPointOption(isProfilingFromProcessStart)
    }
  }
}

@Composable
private fun TaskStartingPointOption(isProfilingFromProcessStart: Boolean) {
  val optionText =
    if (isProfilingFromProcessStart) TaskBasedUxStrings.STARTUP_STARTING_POINT_DROPDOWN_OPTION
    else TaskBasedUxStrings.NOW_STARTING_POINT_DROPDOWN_OPTION

  Text(text = optionText, fontSize = TextUnit(14f, TextUnitType.Sp), lineHeight = TextUnit(18f, TextUnitType.Sp), maxLines = 1,
       overflow = TextOverflow.Ellipsis,
       modifier = Modifier.padding(horizontal = TaskBasedUxDimensions.DEVICE_SELECTION_DROPDOWN_HORIZONTAL_PADDING_DP))
}