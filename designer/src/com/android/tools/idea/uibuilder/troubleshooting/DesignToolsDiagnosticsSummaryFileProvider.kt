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
package com.android.tools.idea.uibuilder.troubleshooting

import com.android.tools.idea.diagnostics.report.DiagnosticsSummaryFileProvider
import com.android.tools.idea.diagnostics.report.FileInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Paths

private const val outputLogFileName: String = "DesignTools.log"

/**
 * A [DiagnosticsSummaryFileProvider] that leverages [DesignToolsTroubleInfoCollector] to collect
 * Design Tools specific troubleshooting information that will be put into a log for the users to
 * send it in bug reports.
 */
class DesignToolsDiagnosticsSummaryFileProvider : DiagnosticsSummaryFileProvider {
  override val name: String = "Design Tools"
  override fun getFiles(project: Project?): List<FileInfo> {
    if (project == null) return emptyList()

    val outputFile =
      DiagnosticsSummaryFileProvider.getDiagnosticsDirectoryPath(PathManager.getLogPath())
        .resolve(outputLogFileName)
    Files.writeString(outputFile, DesignToolsTroubleInfoCollector().collectInfo(project))
    return listOf(FileInfo(outputFile, Paths.get(outputLogFileName)))
  }
}
