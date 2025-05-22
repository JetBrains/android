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
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.Navigatable
import java.io.File
import java.util.function.Consumer

class DeclarativeErrorParser : BuildOutputParser {
  private val FAILED_BUILD_FILE_PATTERN: Regex = "> Failed to interpret the declarative DSL file '([^']+.gradle.dcl)':".toRegex()
  private val SUBJECT_PROBLEM_LINE_PATTERN: Regex = "[\\s]{4}[^\\s].*".toRegex()
  private val PROBLEM_LINE_PATTERN: Regex = "[\\s]{6}([\\d]+):([\\d]+): .*".toRegex()

  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    if (!line.startsWith(BuildOutputParserUtils.BUILD_FAILED_WITH_EXCEPTION_LINE)) return false

    if (!reader.readLine().isNullOrBlank()) return false

    if (reader.readLine() != "* What went wrong:") return false
    if (reader.readLine()?.startsWith(BUILD_ISSUE_START) == true) {
      return readFileIssues(reader, messageConsumer)
    }
    return false
  }

  private fun readFileIssues(reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
     var result = false
      val problemLine = reader.readLine() ?: return false
      FAILED_BUILD_FILE_PATTERN.matchEntire(problemLine)?.let { // now we fully sure that it's a declarative problem
        val (filename) = it.destructured
        val projectFile = File(filename)
        val issueTitle = buildIssueTitle(filename)

        var successReadSubject = false
        do {
          successReadSubject = readSubject(reader, messageConsumer, issueTitle, projectFile)
          if(successReadSubject) result = true
        }
        while (successReadSubject)
    }
    return result
  }

  private fun readSubject(reader: BuildOutputInstantReader,
                          messageConsumer: Consumer<in BuildEvent>,
                          issueTitle: String,
                          projectFile: File): Boolean {
    var foundIssue = false
    val subject = reader.readLine() ?: return false
    SUBJECT_PROBLEM_LINE_PATTERN.matchEntire(subject)?.let {
      var readResult: ReadResult? = null
      do {
        val reasonLine = reader.readLine() ?: return foundIssue
        val description = StringBuilder().appendLine(issueTitle).appendLine(reasonLine)

        readResult = readIssue(reasonLine, description.toString(), projectFile, reader.parentEventId)?.also {
          // filter out errors for same position
          if (notOnSamePosition(readResult, it)) messageConsumer.accept(it.event)
          foundIssue = true
        }
        if (readResult == null) {
          reader.pushBack()
        }
      }
      while (readResult != null)
    }
    return foundIssue
  }

  data class ReadResult(val event: BuildIssueEvent, val lineNumber: Int, val columnNumber: Int)

  private fun notOnSamePosition(previous:ReadResult?, current:ReadResult) :Boolean =
    previous == null || (current.lineNumber != previous.lineNumber || current.columnNumber != previous.columnNumber)

  private fun readIssue(reasonLine: String, description: String, projectFile: File, parentEventId: Any): ReadResult? {
    PROBLEM_LINE_PATTERN.matchEntire(reasonLine)?.let { reason ->
      val (lineNumber, columnNumber) = reason.destructured
      val buildIssue = object : ErrorMessageAwareBuildIssue {
        override val description: String = description.trimEnd()
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
      return ReadResult(
        BuildIssueEventImpl(parentEventId, buildIssue, MessageEvent.Kind.ERROR),
        lineNumber.toInt(),
        columnNumber.toInt()
      )
    }
    return null
  }

  companion object {
    const val BUILD_ISSUE_TITLE: String = "Declarative project configure issue"
    const val BUILD_ISSUE_START: String = "A problem occurred configuring project "
    fun buildIssueTitle(fileName: String): String = "Failed to interpret declarative file '$fileName'"
  }

}