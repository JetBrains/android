/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.idea.adb.AdbServerStatusRetriever
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.Path

/** A [DiagnosticsSummaryFileProvider] for adb host log. */
object AdbHostLogFileProvider : DiagnosticsSummaryFileProvider {

  private const val SERVER_STATUS_FILE: String = "server-status.log"

  override val name: String
    get() = "Adb Host"

  override fun getFiles(project: Project?): List<FileInfo> {
    if (!StudioFlags.ADB_HOST_LOGS_ENABLED.get()) {
      return emptyList()
    }
    if (project == null) {
      return emptyList()
    }
    val serverStatus = AdbServerStatusRetriever.getInstance(project).serverStatus.value ?: return emptyList()
    val serverStatusOutputFile =
      DiagnosticsSummaryFileProvider.getDiagnosticsDirectoryPath(PathManager.getLogPath())
        .resolve(SERVER_STATUS_FILE)
    Files.writeString(serverStatusOutputFile, serverStatus.toString())
    return listOf(FileInfo(Path(serverStatus.absoluteLogPath), Path(serverStatus.absoluteLogPath).fileName),
                  FileInfo(serverStatusOutputFile, Paths.get(SERVER_STATUS_FILE)))
  }
}