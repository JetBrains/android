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
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.android.tools.profilers.ExportArtifactUtils
import com.android.tools.profilers.ExportableArtifact
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionsView.Companion.getImportAction
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.TASK_ACTION_BAR_ACTION_HORIZONTAL_SPACE_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.text.EllipsisText
import com.intellij.openapi.ui.Messages
import org.jetbrains.jewel.ui.component.OutlinedButton

@Composable
fun RecordingActionGroup(artifact: SessionArtifact<*>?,
                         isRecordingExportable: Boolean,
                         isRecordingSelected: Boolean,
                         doDeleteSelectedRecording: () -> Unit,
                         profilers: StudioProfilers,
                         ideProfilerComponents: IdeProfilerComponents) {
  // TODO (b/332359184): Add back tooltips when b/332359184 is fixed.
  Row(horizontalArrangement = Arrangement.spacedBy(TASK_ACTION_BAR_ACTION_HORIZONTAL_SPACE_DP)) {
    OutlinedButton(onClick = { getImportAction(ideProfilerComponents, profilers, null).run() }) {
      EllipsisText(text = TaskBasedUxStrings.IMPORT_RECORDING_DESC)
    }

    OutlinedButton(enabled = isRecordingExportable, modifier = Modifier.testTag("ExportRecordingButton"), onClick = {
      val exportableArtifact = artifact as ExportableArtifact
      ExportArtifactUtils.exportArtifact(exportableArtifact.exportableName, exportableArtifact.exportExtension,
                                         artifact::export, ideProfilerComponents, profilers.ideServices)
    }) {
      EllipsisText(text = TaskBasedUxStrings.EXPORT_RECORDING_DESC)
    }

    OutlinedButton(enabled = isRecordingSelected, modifier = Modifier.testTag("DeleteRecordingButton"), onClick = {
      if (ideProfilerComponents.createUiMessageHandler().displayOkCancelMessage("Confirm Deletion",
                                                                                "Do you really want to delete this recording?", "OK",
                                                                                "Cancel", Messages.getQuestionIcon(), null)) {
        doDeleteSelectedRecording()
      }
    }) {
      EllipsisText(text = TaskBasedUxStrings.DELETE_RECORDING_DESC)
    }
  }
}