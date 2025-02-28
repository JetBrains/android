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
package com.android.tools.idea.gradle.project.build.output

import com.android.tools.idea.gradle.project.sync.idea.issues.ErrorMessageAwareBuildIssue
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.Navigatable
import java.util.function.Consumer

class DeclarativeErrorParser : BuildOutputParser {
  private val FAILED_BUILD_FILE_PATTERN: Regex = "> Failed to interpret the declarative DSL file '([^']+.gradle.dcl)':".toRegex()
  private val SUBJECT_PROBLEM_LINE_PATTERN: Regex = "[\\s]{4}[^\\s].*".toRegex()
  private val PROBLEM_LINE_PATTERN: Regex = "      ([\\d]+):([\\d]+): .*".toRegex()

  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    if (!line.startsWith(BuildOutputParserUtils.BUILD_FAILED_WITH_EXCEPTION_LINE)) return false

    if (!reader.readLine().isNullOrBlank()) return false

    if (reader.readLine() != "* What went wrong:") return false

    if (reader.readLine()?.startsWith(BUILD_ISSUE_START) == true) {
      val problemLine = reader.readLine() ?: return false
      FAILED_BUILD_FILE_PATTERN.matchEntire(problemLine)?.let { // now we fully sure that it's a declarative problem
        val (filename) = it.destructured
        val projectFile = java.io.File(filename)
        val description = StringBuilder().appendLine(buildIssueTitle(filename))
        val subject = reader.readLine() ?: return false
        SUBJECT_PROBLEM_LINE_PATTERN.matchEntire(subject)?.let {

          val reasonLine = reader.readLine() ?: return false
          description.appendLine(reasonLine)

          PROBLEM_LINE_PATTERN.matchEntire(reasonLine)?.let { reason ->
            val (lineNumber, columnNumber) = reason.destructured
            val buildIssue = object : ErrorMessageAwareBuildIssue {
              override val description: String = description.toString().trimEnd()
              override val quickFixes: List<BuildIssueQuickFix> = emptyList()
              override val title: String = BUILD_ISSUE_TITLE
              override val buildErrorMessage: BuildErrorMessage
                get() = BuildErrorMessage.newBuilder().apply {
                  errorShownType = BuildErrorMessage.ErrorType.INVALID_DECLARATIVE_DEFINITION
                  fileLocationIncluded = true
                  fileIncludedType = BuildErrorMessage.FileType.PROJECT_FILE
                  lineLocationIncluded = true
                }.build()

              override fun getNavigatable(project: Project): Navigatable? {
                val virtualFile = VfsUtil.findFileByIoFile(projectFile, false) ?: return null
                return OpenFileDescriptor(project, virtualFile, lineNumber.toInt(), columnNumber.toInt())
              }

            }
            messageConsumer.accept(BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR))
            return true
          }
        }
      }
    }
    return false
  }


  companion object {
    const val BUILD_ISSUE_TITLE: String = "Declarative project configure issue"
    const val BUILD_ISSUE_START: String = "A problem occurred configuring project "
    fun buildIssueTitle(fileName: String): String = "Failed to interpret declarative file '$fileName'"
  }

}