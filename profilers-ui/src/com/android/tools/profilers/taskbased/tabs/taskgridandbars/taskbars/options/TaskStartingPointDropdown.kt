/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars.options

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.DROPDOWN_PROMPT_HORIZONTAL_SPACE_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.dropdowns.DropdownOptionText
import com.android.tools.profilers.taskbased.common.text.EllipsisText
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import org.jetbrains.jewel.ui.component.Dropdown

@Composable
fun TaskStartingPointDropdown(profilingProcessStartingPoint: TaskHomeTabModel.ProfilingProcessStartingPoint,
                              setProfilingProcessStartingPoint: (TaskHomeTabModel.ProfilingProcessStartingPoint) -> Unit,
                              isProfilingProcessFromNowEnabled: Boolean,
                              isProfilingProcessFromProcessStartEnabled: Boolean) {
  val isDropdownEnabled = isProfilingProcessFromNowEnabled || isProfilingProcessFromProcessStartEnabled

  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DROPDOWN_PROMPT_HORIZONTAL_SPACE_DP)) {
    EllipsisText(text = TaskBasedUxStrings.STARTING_POINT_DROPDOWN_TITLE)
    Dropdown(modifier = Modifier.testTag("TaskStartingPointDropdown"), enabled = isDropdownEnabled, menuContent = {
      selectableItem(enabled = isProfilingProcessFromNowEnabled,
                     selected = profilingProcessStartingPoint == TaskHomeTabModel.ProfilingProcessStartingPoint.NOW,
                     onClick = { setProfilingProcessStartingPoint(TaskHomeTabModel.ProfilingProcessStartingPoint.NOW) }) {
        TaskStartingPointOption(TaskBasedUxStrings.NOW_STARTING_POINT_DROPDOWN_OPTION, Modifier.testTag("TaskStartingPointOption"))
      }
      selectableItem(enabled = isProfilingProcessFromProcessStartEnabled,
                     selected = profilingProcessStartingPoint == TaskHomeTabModel.ProfilingProcessStartingPoint.PROCESS_START,
                     onClick = { setProfilingProcessStartingPoint(TaskHomeTabModel.ProfilingProcessStartingPoint.PROCESS_START) }) {
        TaskStartingPointOption(TaskBasedUxStrings.STARTUP_STARTING_POINT_DROPDOWN_OPTION, Modifier.testTag("TaskStartingPointOption"))
      }
    }) {
      val selectionText = when (profilingProcessStartingPoint) {
        TaskHomeTabModel.ProfilingProcessStartingPoint.UNSPECIFIED -> "Please select a starting point"
        TaskHomeTabModel.ProfilingProcessStartingPoint.NOW -> TaskBasedUxStrings.NOW_STARTING_POINT_DROPDOWN_OPTION
        TaskHomeTabModel.ProfilingProcessStartingPoint.PROCESS_START -> TaskBasedUxStrings.STARTUP_STARTING_POINT_DROPDOWN_OPTION
      }
      TaskStartingPointOption(selectionText)
    }
  }
}

@Composable
private fun TaskStartingPointOption(optionText: String, modifier: Modifier = Modifier) {
  DropdownOptionText(optionText, modifier)
}