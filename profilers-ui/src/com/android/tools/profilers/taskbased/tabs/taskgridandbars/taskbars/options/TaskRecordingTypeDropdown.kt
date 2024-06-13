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
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text

@Composable
fun TaskRecordingTypeDropdown(taskRecordingType: TaskHomeTabModel.TaskRecordingType,
                              setProfilingProcessStartingPoint: (TaskHomeTabModel.TaskRecordingType) -> Unit) {

  Row(verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(DROPDOWN_PROMPT_HORIZONTAL_SPACE_DP)) {
    Text(TaskBasedUxStrings.RECORDING_TYPE_DROPDOWN_TITLE)
    Dropdown(modifier = Modifier.testTag("TaskRecordingTypeDropdown"), menuContent = {
      TaskHomeTabModel.TaskRecordingType.values().forEach {
        selectableItem(selected = it == taskRecordingType,
                       onClick = { setProfilingProcessStartingPoint(it) }) {
          TaskRecordingTypeOption(it, Modifier.testTag("TaskRecordingTypeOption"))
        }
      }
    }) {
      TaskRecordingTypeOption(taskRecordingType)
    }
  }
}

@Composable
private fun TaskRecordingTypeOption(taskRecordingType: TaskHomeTabModel.TaskRecordingType, modifier: Modifier = Modifier) {
  val optionText = when (taskRecordingType) {
    TaskHomeTabModel.TaskRecordingType.SAMPLED -> TaskBasedUxStrings.ART_SAMPLED_RECORDING_TYPE_OPTION
    TaskHomeTabModel.TaskRecordingType.INSTRUMENTED -> TaskBasedUxStrings.ART_INSTRUMENTED_RECORDING_TYPE_OPTION
  }

  DropdownOptionText(optionText)
}