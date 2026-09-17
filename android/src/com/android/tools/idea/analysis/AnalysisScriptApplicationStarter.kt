/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.analysis

import com.intellij.ide.CliResult
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.ApplicationStarter.Companion.NOT_IN_EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import kotlin.system.exitProcess

class AnalysisScriptApplicationStarter : ApplicationStarter {

  override val requiredModality = NOT_IN_EDT

  override fun main(args: List<String>) {
    System.err.println("Error: Could not find existing IDE instance")
    exitProcess(1)
  }

  override fun canProcessExternalCommandLine() = true

  @Suppress("UnstableApiUsage")
  override suspend fun processExternalCommandLine(args: List<String>, currentDirectory: String?): CliResult {
    try {
      if (args.size != 2) {
        return CliResult(1, "Error: Usage: ide-launcher SCRIPT")
      }

      if (currentDirectory == null) {
        return CliResult(1, "Error: Could not get current directory")
      }

      val localFileSystem = LocalFileSystem.getInstance()

      val currentDirectoryFile = File(currentDirectory)
      val currentDirectoryVirtualFile =
        localFileSystem.findFileByPath(currentDirectory)
          ?: return CliResult(1, "Error: Could not find current directory in local file system: ${currentDirectoryFile.path}")

      // Script.
      val scriptFile = currentDirectoryFile.resolve(args[1])
      val scriptVirtualFile =
        localFileSystem.findFileByPath(scriptFile.path)
          ?: return CliResult(1, "Error: Could not find script in local file system: ${scriptFile.path}")

      // Project.
      val projects = readAction {
        ProjectManager.getInstance().openProjects.filter {
          it.isInitialized &&
            !it.isDisposed &&
            ProjectRootManager.getInstance(it).fileIndex.isInProjectOrExcluded(currentDirectoryVirtualFile)
        }
      }

      if (projects.isEmpty()) {
        return CliResult(1, "Error: Could not find a project for current directory: ${currentDirectoryFile.path}")
      }

      if (projects.size > 1) {
        return CliResult(1, "Error: Multiple open projects found for current directory: ${currentDirectoryFile.path}")
      }

      val project = projects[0]
      val result = project.getService(AnalysisScriptService::class.java).runAnalysisScript(scriptVirtualFile)
      return CliResult(0, result)
    } catch (exception: Exception) {
      return CliResult(1, "Error: ${exception.stackTraceToString()}")
    }
  }
}
