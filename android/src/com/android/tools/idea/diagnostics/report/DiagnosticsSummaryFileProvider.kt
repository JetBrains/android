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

import com.android.tools.idea.diagnostics.DIAGNOSTICS_REPORTS_DIR
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.nio.file.Paths

/*
  FileInfo contains a pair of values used for creating a diagnostic report
  @param source: the path to the file
  @param destination: the path where the file should be stored in the report
 */
data class FileInfo(val source: Path, val destination: Path)

/*
  DiagnosticsSummaryFileProvider returns a list of source/destination pairs corresponding to debugging artifacts
  */
interface DiagnosticsSummaryFileProvider {
  fun getFiles(project: Project?): List<FileInfo>

  companion object {
    val DiagnosticSummaryFileProviders: Map<String, DiagnosticsSummaryFileProvider> = mapOf(
      "Logs" to DefaultLogFileProvider,
      "System Info" to SystemInfoFileProvider,
      "Metrics" to DefaultMetricsLogFileProvider,
      "Heap Reports" to HeapReportProvider,
      "Thread Dumps" to ThreadDumpProvider,
      "UI Freezes" to UIFreezeProvider)

    /*
       Build a list of FileInfo objects based on the specified providers. Each file info object will be resolved
       relative to the name associated with the provider that created it.
     */
    @JvmStatic
    fun buildFileList(project: Project? = null): List<FileInfo> {
      val list = mutableListOf<FileInfo>()
      for ((name, provider) in DiagnosticSummaryFileProviders.entries) {
        val files = provider.getFiles(project).map {
          FileInfo(it.source, Paths.get(name).resolve(it.destination))
        }
        list.addAll(files)
      }
      return list.sortedBy { it.destination }
    }

    /**
     * Returns the diagnostics file directory based on the log path
     * Will create the diagnostics directory if it doesn't exist
     * Assumes that the log directory exists
     */
    fun getDiagnosticsDirectoryPath(logDir: String): Path {
      val dir = Paths.get(logDir).resolve(DIAGNOSTICS_REPORTS_DIR)
      dir.toFile().mkdirs()
      return dir
    }
  }
}
