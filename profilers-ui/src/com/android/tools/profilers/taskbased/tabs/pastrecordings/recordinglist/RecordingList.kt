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
package com.android.tools.profilers.taskbased.tabs.pastrecordings.recordinglist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.NO_RECORDINGS_INSTRUCTIONS_TEXT
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.NO_RECORDINGS_TITLE
import com.android.tools.profilers.taskbased.common.text.EllipsisText
import com.android.tools.profilers.taskbased.home.selections.recordings.RecordingListModel

@Composable
fun RecordingList(recordingListModel: RecordingListModel, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    val selectedRecording by recordingListModel.selectedRecording.collectAsState()
    val recordingList by recordingListModel.recordingList.collectAsState()
    if (recordingList.isEmpty()) {
      EmptyRecordingMessage()
    }
    else {
      RecordingTable(recordingList = recordingList, selectedRecording = selectedRecording,
                     onRecordingSelection = recordingListModel::onRecordingSelection)
    }
  }
}

@Composable
fun EmptyRecordingMessage() {
  Column(modifier = Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
         verticalArrangement = Arrangement.Center) {
    EllipsisText(text = NO_RECORDINGS_TITLE, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    EllipsisText(text = NO_RECORDINGS_INSTRUCTIONS_TEXT)
  }
}