/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.report

import com.android.tools.idea.actions.SendFeedbackAction
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import java.nio.file.Paths

private const val FILE_NAME = "SystemInfo.log"

/*
  SystemInfoFileProvider copies the text from the SendFeedbackAction into
  a text file so that it can be included in the diagnostic summary report
 */
object SystemInfoFileProvider : DiagnosticsSummaryFileProvider {
  override val name: String = "System Info"
  override fun getFiles(project: Project?): List<FileInfo> {
    val dir = DiagnosticsSummaryFileProvider.getDiagnosticsDirectoryPath(PathManager.getLogPath())
    val path = dir.resolve(FILE_NAME)
    path.toFile().writeText(SendFeedbackAction.getDescription(project))

    return listOf(FileInfo(path, Paths.get(FILE_NAME)))
  }
}