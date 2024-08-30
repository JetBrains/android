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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.actionbars

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_ACTION_BAR_CONTENT_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.ACTION_BAR_RECORDING
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.ACTION_BAR_STOP_RECORDING
import com.android.tools.profilers.taskbased.task.interim.RecordingScreenModel
import icons.StudioIconsCompose
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text

/**
 * Composable function to display the LeakCanary action bar.
 *
 * This action bar shows a recording timer and a "Stop Recording" button when LeakCanary is actively recording.
 *
 * @param leakCanaryModel The LeakCanaryModel providing the recording state.
 * @param testMode A boolean flag indicating whether the component is in test mode (default: false).
 */
@Composable
fun LeakCanaryActionBar(leakCanaryModel: LeakCanaryModel) {
  val isRecording by leakCanaryModel.isRecording.collectAsState()
  if (isRecording) {
    Row(modifier = Modifier.fillMaxWidth().padding(TASK_ACTION_BAR_CONTENT_PADDING_DP),
        verticalAlignment = Alignment.CenterVertically) {
      RecordingTimer(leakCanaryModel)
      Spacer(modifier = Modifier.weight(1f))
      DefaultButton(onClick = leakCanaryModel::stopListening) {
        Text(ACTION_BAR_STOP_RECORDING)
      }
    }
  }
}

@Composable
fun RecordingTimer(leakCanaryModel: LeakCanaryModel) {
  val elapsedNs by leakCanaryModel.elapsedNs.collectAsState()
  val isRecording by leakCanaryModel.isRecording.collectAsState()
  val formattedTime = RecordingScreenModel.formatElapsedTime(elapsedNs)

  if (isRecording) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        StudioIconsCompose.Profiler.Toolbar.StopRecording,
        contentDescription = TaskBasedUxStrings.RECORDING_IN_PROGRESS
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(ACTION_BAR_RECORDING, fontWeight = FontWeight.SemiBold)
      Spacer(modifier = Modifier.width(2.dp))
      Text(formattedTime)
    }
  }
}