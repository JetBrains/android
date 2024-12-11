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
package com.android.tools.idea.profilers.actions

import com.android.tools.profilers.ExportableArtifact
import com.intellij.openapi.actionSystem.AnActionEvent
import java.io.File
import java.io.FileOutputStream

/**
 * (Test-only action) Exports the currently selected recording to the project base path as `ExportedTraceFile`
 */
class ExportRecordingAction : ProfilerTaskActionBase() {
  @Suppress("VisibleForTests")
  override fun actionPerformed(e: AnActionEvent) {
    val profilers = getStudioProfilers(e.project!!)
    val sessionArtifact = profilers.pastRecordingsTabModel.recordingListModel.exportableArtifact
    val exportableArtifact = sessionArtifact as ExportableArtifact
    val pathToSaveTheRecording = "${e.project?.basePath}/ExportedTraceFile.${exportableArtifact.exportExtension}"
    profilers.ideServices
      .saveFile(File(pathToSaveTheRecording),
                {outputStream: FileOutputStream -> sessionArtifact.export(outputStream)}, null)
  }
}