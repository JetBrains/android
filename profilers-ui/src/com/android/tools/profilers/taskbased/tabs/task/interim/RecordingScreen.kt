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
package com.android.tools.profilers.taskbased.tabs.task.interim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.task.interim.RecordingScreenModel
import icons.StudioIconsCompose
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text

@Composable
fun RecordingScreen(recordingScreenModel: RecordingScreenModel<*>) {
  val elapsedNs by recordingScreenModel.elapsedNs.collectAsState()
  val isStopButtonClicked by recordingScreenModel.isStopButtonClicked.collectAsState()
  val canRecordingStop by recordingScreenModel.canRecordingStop.collectAsState()
  val isUserStoppable = recordingScreenModel.isUserStoppable
  Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center,
         horizontalAlignment = Alignment.CenterHorizontally) {
    Column {
      Row(modifier = Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp),
          verticalAlignment = Alignment.CenterVertically) {
        Icon(
          painter = StudioIconsCompose.Profiler.Toolbar.StopRecording().getPainter().value,
          contentDescription = TaskBasedUxStrings.RECORDING_IN_PROGRESS,
          modifier = Modifier.size(20.dp)
        )
        val ongoingTaskName = recordingScreenModel.taskName.lowercase()
        Text(if (isUserStoppable) TaskBasedUxStrings.RECORDING_IN_PROGRESS else "Saving a $ongoingTaskName...",
             fontSize = TextUnit(18f, TextUnitType.Sp), modifier = Modifier.testTag("RecordingScreenMessage"))
      }
    }
    Text(recordingScreenModel.formatElapsedTime(elapsedNs), modifier = Modifier.padding(bottom = 24.dp))
    if (isUserStoppable) {
      DefaultButton(onClick = { recordingScreenModel.onStopRecordingButtonClick() },
                    enabled = canRecordingStop && !isStopButtonClicked,
                    modifier = Modifier.testTag("StopRecordingButton"))
      {
        Text(TaskBasedUxStrings.STOP_RECORDING)
      }
    }
  }
}