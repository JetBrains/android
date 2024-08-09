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
package com.android.tools.idea.streaming.device

import com.android.tools.idea.diagnostics.report.DiagnosticsSummaryFileProvider
import com.android.tools.idea.diagnostics.report.FileInfo
import com.intellij.openapi.project.Project

/** A [DiagnosticsSummaryFileProvider] for device mirroring log. */
class MirroringDiagnosticsSummaryFileProvider : DiagnosticsSummaryFileProvider {

  override val name: String
    get() = "Device Mirroring"

  override fun getFiles(project: Project?): List<FileInfo> {
    val logFile = AgentLogSaver.logFile
    return listOf(FileInfo(logFile, logFile.fileName))
  }
}
