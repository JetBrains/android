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

import com.intellij.openapi.project.Project
import java.nio.file.Path

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
}

// TODO (b/231162502)
val DiagnosticSummaryFileProviders: Array<DiagnosticsSummaryFileProvider> = arrayOf(DefaultLogFileProvider, SystemInfoFileProvider,
                                                                                    DefaultMetricsLogFileProvider)