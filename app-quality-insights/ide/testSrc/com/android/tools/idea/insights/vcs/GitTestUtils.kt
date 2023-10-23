/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler

// Copied from IJ git4idea test src.
internal fun git(
  project: Project,
  command: GitCommand,
  parameters: List<String>,
  workingDir: VirtualFile
): String {
  val handler = GitLineHandler(project, workingDir, command)
  handler.setWithMediator(false)
  handler.addParameters(parameters)

  val result = Git.getInstance().runCommand(handler)
  if (result.exitCode != 0) {
    throw IllegalStateException(
      "Command [${command.name()}] failed with exit code ${result.exitCode}\n${result.output}\n${result.errorOutput}"
    )
  }
  return result.errorOutputAsJoinedString + result.outputAsJoinedString
}
