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
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.DROPDOWN_PROMPT_HORIZONTAL_SPACE_DP
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.dropdowns.DropdownOptionText
import com.android.tools.profilers.taskbased.common.text.EllipsisText
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import org.jetbrains.jewel.ui.component.Dropdown

@Composable
fun TaskRecordingTypeDropdown(taskRecordingType: TaskHomeTabModel.TaskRecordingType,
                              setProfilingProcessStartingPoint: (TaskHomeTabModel.TaskRecordingType) -> Unit) {

  Row(verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(DROPDOWN_PROMPT_HORIZONTAL_SPACE_DP)) {
    EllipsisText(text = TaskBasedUxStrings.RECORDING_TYPE_DROPDOWN_TITLE)
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
  when (taskRecordingType) {
    TaskHomeTabModel.TaskRecordingType.SAMPLED -> DropdownOptionText(modifier,
                                                                     TaskBasedUxStrings.ART_SAMPLED_RECORDING_TYPE_OPTION_PRIMARY_TEXT,
                                                                     TaskBasedUxStrings.ART_SAMPLED_RECORDING_TYPE_OPTION_SECONDARY_TEXT,
                                                                     true)

    TaskHomeTabModel.TaskRecordingType.INSTRUMENTED -> DropdownOptionText(modifier,
                                                                          TaskBasedUxStrings.ART_INSTRUMENTED_RECORDING_TYPE_OPTION, null,
                                                                          true)
  }
}