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

import com.android.tools.idea.adb.AdbHostLog
import com.intellij.openapi.project.Project
import kotlin.io.path.Path

/** A [DiagnosticsSummaryFileProvider] for adb host log. */
object AdbHostLogFileProvider : DiagnosticsSummaryFileProvider {

  override val name: String
    get() = "Adb Host"

  override fun getFiles(project: Project?): List<FileInfo> {
    if (project == null) {
      return emptyList()
    }
    val logPath = AdbHostLog.getInstance(project).path ?: return emptyList()
    return listOf(FileInfo(Path(logPath), Path(logPath).fileName))
  }
}