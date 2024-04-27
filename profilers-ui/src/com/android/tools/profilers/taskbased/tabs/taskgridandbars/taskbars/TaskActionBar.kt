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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.profilers.taskbased.common.buttons.OpenTaskButton
import com.android.tools.profilers.taskbased.common.buttons.StartTaskButton
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.TASK_ACTION_BAR_CONTENT_PADDING_DP

@Composable
fun TaskActionBar(canStartTask: Boolean, onEnterProfilerTask: () -> Unit, isTaskNew: Boolean) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(TASK_ACTION_BAR_CONTENT_PADDING_DP)
  ) {
    // TODO(b/277798531): This is where the "Start profiler task from" dropdown will be placed.
    Spacer(modifier = Modifier.weight(1f))
    // The enter task button.
    if (isTaskNew) {
      StartTaskButton(isEnabled = canStartTask, onClick = onEnterProfilerTask)
    }
    else {
      OpenTaskButton(isEnabled = canStartTask, onClick = onEnterProfilerTask)
    }
  }
}