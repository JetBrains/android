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
package com.android.tools.idea.diagnostics

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import kotlin.io.path.name

private val MAX_AGE = Duration.ofDays(10)
private val DIRECTORIES = arrayOf(DIAGNOSTICS_REPORTS_DIR, HEAP_REPORTS_DIR)
private val DIRECTORY_REGEXES = arrayOf(Regex("^${UI_FREEZE_DIR_PREFIX}.*"))

/**
 * DiagnosticsReportCleaner deletes diagnostics reports that are older than MAX_AGE
 */
class DiagnosticsReportCleaner : StartupActivity.Background {
  override fun runActivity(project: Project) {
    val path = Paths.get(PathManager.getLogPath())

    // delete all old files in these directories
    for (directory in DIRECTORIES) {
      cleanupFiles(path.resolve(directory))
    }

    // delete all old matching directories and their contents
    cleanupDirectories(path, DIRECTORY_REGEXES)
  }

  companion object {
    fun cleanupFiles(path: Path) {
      for (child in Files.list(path).filter { shouldDeleteFile(it) }) {
        Files.delete(child)
      }
    }

    fun cleanupDirectories(path: Path, regexes: Array<Regex>) {
      for (directory in Files.list(path).filter { shouldDeleteDirectory(it, regexes) }) {
        directory.toFile().deleteRecursively()
      }
    }

    private fun shouldDeleteFile(path: Path): Boolean {
      return Files.isRegularFile(path) && isOld(path)
    }

    private fun shouldDeleteDirectory(path: Path, regexes: Array<Regex>): Boolean {
      return Files.isDirectory(path) && regexes.any { it.matches(path.name) } && isOld(path)
    }

    private fun isOld(path: Path): Boolean {
      return Duration.ofMillis(System.currentTimeMillis() - Files.getLastModifiedTime(path).toMillis()) > MAX_AGE
    }
  }
}