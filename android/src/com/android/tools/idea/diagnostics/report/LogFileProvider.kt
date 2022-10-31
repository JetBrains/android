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

import com.intellij.diagnostic.VMOptions
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.nio.file.Paths

private val JVM_CRASH_REGEX = Regex("^java_error_in_STUDIO_[0-9]+.log$")

/**
 * PathProvider contains various system paths used by the log file provider. It is used as a parameter in
 * order to allow testing of the LogFileProvider class.
 */
data class PathProvider(val logDir: String, val vmOptionsFile: Path?, val customOptionsDir: String?, val homeDir: String?)

val DefaultPathProvider = PathProvider(PathManager.getLogPath(),
                                       VMOptions.getUserOptionsFile(),
                                       PathManager.getCustomOptionsDirectory(),
                                       System.getProperty("user.home"))

/**
 * LogFileProvider calculates the paths to various log and debugging files so
 * that they can be included in the diagnostic summary report.
 */
class LogFileProvider(private val pathProvider: PathProvider) : DiagnosticsSummaryFileProvider {
  override fun getFiles(project: Project?): List<FileInfo> {
    val fileInfo = mutableListOf<FileInfo>()

    val logDirPath = Paths.get(pathProvider.logDir)
    val logPath = Paths.get("idea.log")

    fileInfo.add(FileInfo(logDirPath.resolve(logPath), logPath))

    pathProvider.vmOptionsFile?.let {
      fileInfo.add(FileInfo(it, it.fileName))
    }

    pathProvider.customOptionsDir?.let {
      val customOptionsPath = Paths.get(it)
      val propertiesFilePath = Paths.get(PathManager.PROPERTIES_FILE_NAME)
      fileInfo.add(FileInfo(customOptionsPath.resolve(propertiesFilePath), propertiesFilePath))
    }

    pathProvider.homeDir?.let {
      fileInfo.addAll(getFiles(Paths.get(it)))
    }

    return fileInfo
  }

  /**
   * getFiles returns all files matching the specified pattern within the specified directory
   */
  private fun getFiles(root: Path) = sequence {
    val files = root.toFile().listFiles() ?: return@sequence
    for (file in files) {
      if (file.isFile && file.name.matches(JVM_CRASH_REGEX)) {
        val path = file.toPath()
        yield(FileInfo(path, root.relativize(path)))
      }
    }
  }
}

val DefaultLogFileProvider = LogFileProvider(DefaultPathProvider)