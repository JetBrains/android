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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.android.tools.profilers.ExportArtifactUtils
import com.android.tools.profilers.ExportableArtifact
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionsView.Companion.getImportAction
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.RECORDING_LIST_ACTIONS_BAR_CONTENT_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxIcons.EXPORT_RECORDING_ICON
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxIcons.IMPORT_RECORDING_ICON
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton

@Composable
fun RecordingListActionsBar(artifact: SessionArtifact<*>?,
                            isRecordingExportable: Boolean,
                            profilers: StudioProfilers,
                            ideProfilerComponents: IdeProfilerComponents) {
  Row {
    IconButton(onClick = { getImportAction(ideProfilerComponents, profilers, null).run() }) {
      Icon(
        resource = IMPORT_RECORDING_ICON.path,
        contentDescription = "Import Recording",
        iconClass = IMPORT_RECORDING_ICON.iconClass,
        modifier = Modifier.padding(RECORDING_LIST_ACTIONS_BAR_CONTENT_PADDING_DP)
      )
    }

    IconButton(enabled = isRecordingExportable, modifier = Modifier.testTag("ExportRecordingButton"), onClick = {
      val exportableArtifact = artifact as ExportableArtifact
      ExportArtifactUtils.exportArtifact(exportableArtifact.exportableName, exportableArtifact.exportExtension,
                                         artifact::export, ideProfilerComponents, profilers.ideServices)
    }) {
      Icon(
        // The export button does not exist, so we will instead use a 180 rotated version of the import icon.
        resource = EXPORT_RECORDING_ICON.path,
        contentDescription = "Export Recording",
        iconClass = EXPORT_RECORDING_ICON.iconClass,
        modifier = Modifier.padding(RECORDING_LIST_ACTIONS_BAR_CONTENT_PADDING_DP),
      )
    }
  }
}