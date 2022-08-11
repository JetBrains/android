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

import com.android.tools.idea.util.ZipData
import com.android.tools.idea.util.zipFiles
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DiagnosticSummaryAction : DumbAwareAction("Create Diagnostics Summary File") {
  override fun actionPerformed(e: AnActionEvent) {
    val message =
      try {
        val destination = createSummaryFile(e.project)
        "Diagnostics file created successfully. Path: $destination"
      }
      catch (e: Exception) {
        "Error creating diagnostics report: ${e.message}"
      }

    Messages.showInfoMessage(message, "Diagnostics Summary File")
  }

  companion object {
    @JvmStatic
    fun createSummaryFile(project: Project?) : String {
      val fileInfo = DiagnosticSummaryFileProviders.map {
        it.getFiles(project).filter { file -> Files.exists(file.source) }
      }.flatten()

      val zipInfo = fileInfo.map { ZipData(it.source.toString(), it.destination.toString()) }.toTypedArray()

      val datetime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

      val dir = DiagnosticsSummaryFileProvider.getDiagnosticsDirectoryPath(PathManager.getLogPath())
      val destination = dir.resolve("DiagnosticsReport${datetime}.zip").toString()

      zipFiles(zipInfo, destination)
      return destination
    }
  }
}
