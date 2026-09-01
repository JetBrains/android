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
package com.android.tools.idea.gradle.project.build.output.tomlParser

import com.android.tools.idea.gradle.project.build.output.tomlParser.TomlErrorParser.Companion.BUILD_ISSUE_TOML_TITLE
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.Navigatable
import java.nio.file.Paths

class TomlSyntaxErrorHandler : TomlErrorHandler {
  override fun tryExtractMessage(reader: BuildOutputInstantReader): List<BuildIssueEvent> {
    reader.readLine() ?: return listOf()
    if (reader.readLine()?.endsWith("TOML syntax error") == true) {
      val description = StringBuilder().appendLine(BUILD_ISSUE_TOML_TITLE)
      return extractIssueInformation(description, reader)
    }
    return listOf()
  }

  private data class ErrorDescription(val absolutePath: String?, val line: Int?, val column: Int?)

  private fun extractIssueInformation(description: StringBuilder, reader: BuildOutputInstantReader): List<BuildIssueEvent> {
    val errorDescriptions = mutableListOf<ErrorDescription>()
    while (true) {
      val line = reader.readLine() ?: break
      description.appendLine(line)
      val match = Regex("\\s*Location: ([^ ]+) line ([0-9]+).*").matchEntire(line)
      if (match != null) {
        errorDescriptions.add(ErrorDescription(match.groups[1]?.value, match.groups[2]?.value?.toIntOrNull(), null))
      }
    }

    return errorDescriptions.map { e ->
      val buildIssue =
        object : TomlErrorMessageAwareIssue(description.toString()) {
          override fun getNavigatable(project: Project): Navigatable? {
            if (e.absolutePath == null) return null
            val file = VfsUtil.findFile(Paths.get(e.absolutePath), false) ?: return null
            return OpenFileDescriptor(project, file, e.line?.minus(1) ?: 0, e.column?.minus(1) ?: 0)
          }
        }
      BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR)
    }
  }
}
