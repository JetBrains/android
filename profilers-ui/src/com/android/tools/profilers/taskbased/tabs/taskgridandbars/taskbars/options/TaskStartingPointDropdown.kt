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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.DROPDOWN_PROMPT_HORIZONTAL_SPACE_DP
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.dropdowns.DropdownOptionText
import com.android.tools.profilers.taskbased.common.text.EllipsisText
import com.android.tools.profilers.taskbased.home.StartTaskSelectionError
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskStartingPointDropdown(profilingProcessStartingPoint: TaskHomeTabModel.ProfilingProcessStartingPoint,
                              setProfilingProcessStartingPoint: (TaskHomeTabModel.ProfilingProcessStartingPoint) -> Unit,
                              isProfilingProcessFromNowEnabled: Boolean,
                              isProfilingProcessFromProcessStartEnabled: Boolean,
                              processStartDisabledReason: StartTaskSelectionError?) {
  val isDropdownEnabled = isProfilingProcessFromNowEnabled || isProfilingProcessFromProcessStartEnabled

  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DROPDOWN_PROMPT_HORIZONTAL_SPACE_DP)) {
    EllipsisText(text = TaskBasedUxStrings.STARTING_POINT_DROPDOWN_TITLE)
    Dropdown(modifier = Modifier.testTag("TaskStartingPointDropdown"), enabled = isDropdownEnabled, menuContent = {
      selectableItem(enabled = isProfilingProcessFromNowEnabled,
                     selected = profilingProcessStartingPoint == TaskHomeTabModel.ProfilingProcessStartingPoint.NOW,
                     onClick = { setProfilingProcessStartingPoint(TaskHomeTabModel.ProfilingProcessStartingPoint.NOW) }) {
        TaskStartingPointFromNowOption(isProfilingProcessFromNowEnabled, Modifier.testTag("TaskStartingPointOption"))
      }
      val isProcessStartSelected = profilingProcessStartingPoint == TaskHomeTabModel.ProfilingProcessStartingPoint.PROCESS_START
      selectableItem(enabled = isProfilingProcessFromProcessStartEnabled,
                     selected = isProcessStartSelected,
                     onClick = { setProfilingProcessStartingPoint(TaskHomeTabModel.ProfilingProcessStartingPoint.PROCESS_START) }) {
        // In the case where the process start option is disabled and not selected, a tooltip explaining why is provided.
        if (isProfilingProcessFromProcessStartEnabled || isProcessStartSelected) {
          TaskStartingFromProcessStartOption(isProfilingProcessFromProcessStartEnabled, Modifier.testTag("TaskStartingPointOption"))
        }
        else {
          Tooltip(tooltip = {
            Text(processStartDisabledReason?.let { TaskBasedUxStrings.getStartTaskErrorMessage(it) } ?: "Option unavailable")
          }) {
            TaskStartingFromProcessStartOption(false, Modifier.testTag("TaskStartingPointOption"))
          }
        }
      }
    }) {
      when (profilingProcessStartingPoint) {
        TaskHomeTabModel.ProfilingProcessStartingPoint.UNSPECIFIED -> DropdownOptionText(primaryText = "Please select a starting point",
                                                                                         secondaryText = null, isEnabled = false)
        TaskHomeTabModel.ProfilingProcessStartingPoint.NOW -> TaskStartingPointFromNowOption(isProfilingProcessFromNowEnabled)
        TaskHomeTabModel.ProfilingProcessStartingPoint.PROCESS_START -> TaskStartingFromProcessStartOption(
          isProfilingProcessFromProcessStartEnabled)
      }
    }
  }
}

@Composable
private fun TaskStartingPointFromNowOption(isEnabled: Boolean, modifier: Modifier = Modifier) {
  DropdownOptionText(modifier,
                     TaskBasedUxStrings.NOW_STARTING_POINT_DROPDOWN_OPTION_PRIMARY_TEXT,
                     TaskBasedUxStrings.NOW_STARTING_POINT_DROPDOWN_OPTION_SECONDARY_TEXT,
                     isEnabled)
}

@Composable
private fun TaskStartingFromProcessStartOption(isEnabled: Boolean, modifier: Modifier = Modifier) {
  DropdownOptionText(modifier,
                     TaskBasedUxStrings.STARTUP_STARTING_POINT_DROPDOWN_OPTION_PRIMARY_TEXT,
                     TaskBasedUxStrings.STARTUP_STARTING_POINT_DROPDOWN_OPTION_SECONDARY_TEXT,
                     isEnabled)
}