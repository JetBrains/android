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
package com.android.tools.profilers.taskbased.common.buttons

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.OPEN_PROFILER_TASK
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.START_PROFILER_TASK
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.START_PROFILER_TASK_ANYWAY
import com.android.tools.profilers.taskbased.common.text.EllipsisText
import com.android.tools.profilers.tasks.ProfilerTaskType
import org.jetbrains.jewel.ui.component.DefaultButton

@Composable
fun StartTaskButton(canStartTask: Boolean, isProfileablePreferredButNotPresent: Boolean, onClick: () -> Unit) {
  EnterTaskButton(if (isProfileablePreferredButNotPresent && canStartTask) START_PROFILER_TASK_ANYWAY else START_PROFILER_TASK,
                  canStartTask, onClick)
}

@Composable
fun OpenTaskButton(selectedTaskType: ProfilerTaskType, selectedRecording: SessionItem?, onClick: () -> Unit) {
  val isEnabled = selectedRecording != null && selectedTaskType != ProfilerTaskType.UNSPECIFIED
  EnterTaskButton(OPEN_PROFILER_TASK, isEnabled, onClick)
}

@Composable
private fun EnterTaskButton(text: String, isEnabled: Boolean, onClick: () -> Unit) {
  DefaultButton(onClick = onClick, enabled = isEnabled, modifier = Modifier.testTag("EnterTaskButton")) {
    EllipsisText(text = text)
  }
}