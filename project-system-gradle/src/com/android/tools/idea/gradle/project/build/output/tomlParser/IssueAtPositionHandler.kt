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
package com.android.tools.idea.gradle.project.build.output.tomlParser

import com.android.tools.idea.gradle.project.sync.idea.issues.ErrorMessageAwareBuildIssue
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.Navigatable
import java.nio.file.Paths

class IssueAtPositionHandler : TomlErrorHandler {
  val PROBLEM_LINE_PATTERN: Regex = "  - Problem: In version catalog ([^ ]+),.*".toRegex()
  val REASON_POSITION_PATTERN: Regex = "\\s+Reason: At line ([0-9]+), column ([0-9]+):.*".toRegex()
  val REASON_FILE_AND_POSITION_PATTERN: Regex = "\\s+Reason: In file '([^']+)' at line ([0-9]+), column ([0-9]+):.*".toRegex()
  val REASON_FILE_AND_POSITION_PATTERN_CONTINUATION: Regex = "\\s+In file '([^']+)' at line ([0-9]+), column ([0-9]+):.*".toRegex()

  override fun tryExtractMessage(reader: ResettableReader): List<BuildIssueEvent> {
    val firstDescriptionLine = reader.readLine() ?: return listOf()

    if (firstDescriptionLine.endsWith("Invalid TOML catalog definition:")) {
      val description = StringBuilder().appendLine(TomlErrorParser.BUILD_ISSUE_TITLE)
      val problemLine = reader.readLine() ?: return listOf()

      PROBLEM_LINE_PATTERN.matchEntire(problemLine)?.let {
        val catalogName = it.groupValues[1]
        description.appendLine(problemLine)
        return extractIssueInformation(catalogName, description, reader)
      }
    }
    return listOf()
  }

  private data class ErrorDescription(
    val absolutePath: String?,
    val line: Int?,
    val column: Int?
  )

  private fun extractIssueInformation(catalog: String,
                                      description: StringBuilder,
                                      reader: BuildOutputInstantReader
  ): List<BuildIssueEvent> {
    val errorDescriptions = mutableListOf<ErrorDescription>()
    while (true) {
      val descriptionLine = reader.readLine() ?: return listOf()
      if (descriptionLine.startsWith("> Invalid TOML catalog definition")) break
      if (errorDescriptions.isEmpty()) {
        REASON_POSITION_PATTERN.matchEntire(descriptionLine)?.run {
          val (line, column) = destructured
          errorDescriptions.add(ErrorDescription(null, line.toIntOrNull(), column.toIntOrNull()))
        }
        REASON_FILE_AND_POSITION_PATTERN.matchEntire(descriptionLine)?.let {
          val (file, line, column) = it.destructured
          errorDescriptions.add(ErrorDescription(file, line.toIntOrNull(), column.toIntOrNull()))
        }
      }
      else {
        REASON_FILE_AND_POSITION_PATTERN_CONTINUATION.matchEntire(descriptionLine)?.let {
          val (file, line, column) = it.destructured
          errorDescriptions.add(ErrorDescription(file, line.toIntOrNull(), column.toIntOrNull()))
        }
      }
      description.appendLine(descriptionLine)
    }

    return errorDescriptions.map { error ->
      val buildIssue = object : ErrorMessageAwareBuildIssue {
        override val description: String = description.toString().trimEnd()
        override val quickFixes: List<BuildIssueQuickFix> = emptyList()
        override val title: String = TomlErrorParser.BUILD_ISSUE_TITLE
        override val buildErrorMessage: BuildErrorMessage
          get() = BuildErrorMessage.newBuilder().apply {
            errorShownType = BuildErrorMessage.ErrorType.INVALID_TOML_DEFINITION
            fileLocationIncluded = true
            fileIncludedType = BuildErrorMessage.FileType.PROJECT_FILE
            lineLocationIncluded = error.line != null
          }.build()

        override fun getNavigatable(project: Project): Navigatable? {
          val tomlFile = when {
                           error.absolutePath != null -> VfsUtil.findFile(Paths.get(error.absolutePath), false)
                           catalog != null -> project.findCatalogFile(catalog)
                           else -> null
                         } ?: return null
          return OpenFileDescriptor(project, tomlFile, error.line?.minus(1) ?: 0, error.column?.minus(1) ?: 0)
        }
      }
      BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR)
    }
  }
}