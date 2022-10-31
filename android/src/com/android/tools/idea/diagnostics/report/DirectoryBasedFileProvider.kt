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

import com.android.tools.idea.diagnostics.hprof.action.HEAP_REPORTS_DIR
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

private val THREAD_DUMP_REGEX = Regex("^threadDumps-.*")
private val UI_FREEZE_REGEX = Regex("^uiFreeze-.*")
private val HEAP_REPORT_REGEX = Regex("^$HEAP_REPORTS_DIR$")

/**
 * DirectoryBasedFileProvider returns all files under directories
 * that match the specified regex.
 */
class DirectoryBasedFileProvider(private val regex: Regex,
                                 private val pathProvider: PathProvider) : DiagnosticsSummaryFileProvider {

  override fun getFiles(project: Project?): List<FileInfo> {
    val list = mutableListOf<FileInfo>()

    for(directory in getDirectories()) {
      for (path in getFilePaths(directory)) {
        list.add(FileInfo(path, Paths.get(pathProvider.logDir).relativize(path)))
      }
    }
    return list
  }

  /*
  Get all directories that match the specified regex.
   */
  private fun getDirectories() = sequence<File> {
    val directories = Paths.get(pathProvider.logDir).toFile().listFiles { dir, name ->
      name.matches(regex) && File(dir, name).isDirectory
    } ?: return@sequence

    for(directory in directories) {
      yield(directory)
    }
  }

  /*
  Get all file paths under the specified directory.
   */
  private fun getFilePaths(directory: File) = sequence<Path> {
    val files = directory.listFiles { dir, name ->
      File(dir, name).isFile
    } ?: return@sequence

    for(file in files) {
      yield(file.toPath())
    }
  }
}

val ThreadDumpProvider = DirectoryBasedFileProvider(THREAD_DUMP_REGEX, DefaultPathProvider)
val UIFreezeProvider = DirectoryBasedFileProvider(UI_FREEZE_REGEX, DefaultPathProvider)
val HeapReportProvider = DirectoryBasedFileProvider(HEAP_REPORT_REGEX, DefaultPathProvider)