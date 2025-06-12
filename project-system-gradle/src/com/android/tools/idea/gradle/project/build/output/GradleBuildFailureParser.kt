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

import com.android.tools.idea.gradle.project.build.output.BuildOutputParserUtils.isCompilationFailureLine
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.DuplicateMessageAware
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputParser
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.UnresolvedDependencyBuildIssue
import java.io.File
import java.util.function.Consumer
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.text.startsWith
import kotlin.text.trimStart

abstract class GradleBuildFailureParser(
  val failureHandlers: List<FailureDetailsHandler>,
  val knownIssuesCheckers: List<GradleIssueChecker>
) : BuildOutputParser {

  class ParsedFailureDetails {
    private val whereSection = mutableListOf<String>()
    private val whatWentWrongSection = mutableListOf<String>()
    private val trySection = mutableListOf<String>()
    private val exceptionSection = mutableListOf<String>()

    val headerToSection = mapOf(
      "* Where:" to whereSection,
      "* What went wrong:" to whatWentWrongSection,
      "* Try:" to trySection,
      "* Exception is:" to exceptionSection,
    )

    val whereSectionLines: List<String> get() = whereSection
    val whatWentWrongSectionLines: List<String> get() = whatWentWrongSection
    val whatWentWrongSectionText: String get() = whatWentWrongSection.joinToString(separator = "\n").trim()
    val trySectionText: String get() = trySection.joinToString(separator = "\n").trim()
    val exceptionSectionText: String get() = exceptionSection.joinToString(separator = "\n").trim()

    val locationLine: String? get() = whereSectionLines.firstOrNull()
    private val filter: GradleConsoleFilter? get() {
      val locationLine = locationLine ?: return null
      return GradleConsoleFilter(null)
        .takeIf { it.applyFilter(locationLine, locationLine.length) != null }
    }
    val location: FilePosition? get() = filter?.let { fileFilter ->
      FilePosition(File(fileFilter.filteredFileName), fileFilter.filteredLineNumber - 1, 0)
    }
    val description: String get() = buildString {
      if (!locationLine.isNullOrBlank()) {
        appendLine(locationLine).appendLine()
      }
      append(whatWentWrongSectionText)
    }

    val reasonLine: String get() = whatWentWrongSectionLines
      .lastOrNull { it.trimStart().startsWith("> ") }
      ?.substringAfter("> ")?.trimEnd('.')
      ?: whatWentWrongSectionLines.first()

    val taskName: String? get() = whatWentWrongSectionLines.firstOrNull { it.startsWith("Execution failed for task '") }
      ?.substringAfter("Execution failed for task '")
      ?.substringBefore("'.")
  }

  interface FailureDetailsHandler {
    fun consumeFailureMessage(
      failure: ParsedFailureDetails,
      location: FilePosition?,
      parentEventId: Any,
      messageConsumer: Consumer<in BuildEvent>
    ): Boolean
  }

  protected fun processErrorMessage(
    parentId: Any,
    parsedMessage: ParsedFailureDetails,
    messageConsumer: Consumer<in BuildEvent>
  ): Boolean {
    val trySuggestions = parsedMessage.trySectionText
    val exception = parsedMessage.exceptionSectionText
    val filePosition = parsedMessage.location
    val reasonLine: String = parsedMessage.reasonLine
    val errorText: String = parsedMessage.description

    for (failureHandler in failureHandlers) {
      if (failureHandler.consumeFailureMessage(parsedMessage, filePosition, parentId, messageConsumer)) {
        return true
      }
    }

    // compilation errors should be added by the respective compiler output parser.
    if (reasonLine.isCompilationFailureLine()) {
      handleCompilationFailure(parentId, errorText, messageConsumer)
      return false
    }

    for (issueChecker in knownIssuesCheckers) {
      if (issueChecker.consumeBuildOutputFailureMessage(errorText, reasonLine, exception, filePosition, parentId, messageConsumer)) {
        return true
      }
    }

    val detailedMessage = StringBuilder(errorText)

    if (!trySuggestions.isNullOrBlank()) {
      detailedMessage.append("\n\n* Try:\n$trySuggestions")
    }
    if (!exception.isNullOrBlank()) {
      detailedMessage.append("\n\n* Exception is:\n$exception")
    }

    if (filePosition != null) {
      messageConsumer.accept(object : FileMessageEventImpl(
        parentId, MessageEvent.Kind.ERROR, null, reasonLine, detailedMessage.toString(), filePosition), DuplicateMessageAware {} //NON-NLS
      )
    }
    else {
      val unresolvedMessageEvent = checkUnresolvedDependencyError(reasonLine, errorText, parentId)
      if (unresolvedMessageEvent != null) {
        messageConsumer.accept(unresolvedMessageEvent)
      }
      else {
        messageConsumer.accept(object : MessageEventImpl(parentId, MessageEvent.Kind.ERROR, null, reasonLine,
                                                         detailedMessage.toString()), DuplicateMessageAware {}) //NON-NLS
      }
    }
    return true
  }

  protected open fun handleCompilationFailure(
    parentId: Any,
    errorText: String,
    messageConsumer: Consumer<in BuildEvent>
  ) {
    // In GradleBuildScriptErrorParser in case of single failure parsing is just aborted
    // giving chance to other compilation parsers to parse the following lines.
    // However, this is wrong to do, there are two reasons:
    // - In case of multi-failure compilation errors message is only one of the parsed failures.
    // - In case of single-failure if it was run with stacktrace javac will generate junk messages from it.
    // Instead, take this message text and run compilation parsers on it giving them the chance.
    // For now there seems to be one parser that is supposed to parse messages from failures.
    // If there is a need to add more, this function should become more like BuildOutputInstantReaderImpl
    // runnable iterating over all parsers.
    val parser by lazy { FilteringGradleCompilationReportParser() }
    val reader = LinesBuildOutputInstantReader(errorText, parentId)
    while (true) {
      val line = reader.readLine() ?: return
      if(parser.parse(line, reader, messageConsumer)) return
    }
  }

  private fun checkUnresolvedDependencyError(reason: String, description: String, parentId: Any): BuildEvent? {
    val noCachedVersionPrefix = "No cached version of "
    val couldNotFindPrefix = "Could not find "
    val cannotResolvePrefix = "Cannot resolve external dependency "
    val cannotDownloadPrefix = "Could not download "
    val prefix = when {
                   reason.startsWith(noCachedVersionPrefix) -> noCachedVersionPrefix
                   reason.startsWith(couldNotFindPrefix) -> couldNotFindPrefix
                   reason.startsWith(cannotResolvePrefix) -> cannotResolvePrefix
                   reason.startsWith(cannotDownloadPrefix) -> cannotDownloadPrefix
                   else -> null
                 } ?: return null
    val indexOfSuffix = reason.indexOf(" available for offline mode")
    val dependencyName = if (indexOfSuffix > 0) reason.substring(prefix.length, indexOfSuffix) else reason.substring(prefix.length)
    val unresolvedDependencyIssue = UnresolvedDependencyBuildIssue(dependencyName, description, indexOfSuffix > 0)
    return BuildIssueEventImpl(parentId, unresolvedDependencyIssue, MessageEvent.Kind.ERROR)
  }

  /**
   * Copy of GradleConsoleFilter in org.jetbrains.plugins.gradle.execution (revision
   * ff7e3e097c65956cd94769488b9f4bbb8b06ca6d), migrated to Kotlin.
   * TODO (b/362959090): submit to IJ and remove
   */
  private class GradleConsoleFilter(private val myProject: Project?) : Filter {
    var filteredFileName: String? = null
      private set

    var filteredLineNumber: Int = 0
      private set

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
      val filePrefixes: Array<String> =
        arrayOf(
          "Build file '",
          "build file '",
          "Settings file '",
          "settings file '",
          // Android Studio patch
          "Initialization script '",
          "Script '",
        )
      val linePrefixes: Array<String> =
        arrayOf(
          "' line: ",
          "': ",
          "' line: ",
          "': ",
          // Android Studio patch
          "' line: ",
          "' line: ",
        )
      var filePrefix: String? = null
      var linePrefix: String? = null
      for (i in filePrefixes.indices) {
        val filePrefixIndex = StringUtil.indexOf(line, filePrefixes[i])
        if (filePrefixIndex != -1) {
          filePrefix = filePrefixes[i]
          linePrefix = linePrefixes[i]
          break
        }
      }

      if (filePrefix == null) {
        return null
      }

      val filePrefixIndex = StringUtil.indexOf(line, filePrefix)

      val fileAndLineNumber = line.substring(filePrefix.length + filePrefixIndex)
      val linePrefixIndex = StringUtil.indexOf(fileAndLineNumber, linePrefix!!)

      if (linePrefixIndex == -1) {
        return null
      }

      val fileName = fileAndLineNumber.substring(0, linePrefixIndex)
      this.filteredFileName = fileName
      var lineNumberStr =
        fileAndLineNumber.substring(linePrefixIndex + linePrefix.length).trim { it <= ' ' }
      var lineNumberEndIndex = 0
      for (i in 0..<lineNumberStr.length) {
        if (Character.isDigit(lineNumberStr.get(i))) {
          lineNumberEndIndex = i
        } else {
          break
        }
      }

      if (lineNumberStr.isEmpty()) {
        return null
      }

      lineNumberStr = lineNumberStr.substring(0, lineNumberEndIndex + 1)
      val lineNumber: Int
      try {
        lineNumber = lineNumberStr.toInt()
        this.filteredLineNumber = lineNumber
      } catch (e: NumberFormatException) {
        return null
      }

      val file =
        LocalFileSystem.getInstance().findFileByPath(fileName.replace(File.separatorChar, '/'))
      if (file == null) {
        return null
      }

      val textStartOffset = entireLength - line.length + filePrefix.length + filePrefixIndex
      val highlightEndOffset = textStartOffset + fileName.length
      var info: OpenFileHyperlinkInfo? = null
      if (myProject != null) {
        var columnNumber = 0
        val lineAndColumn = StringUtil.substringAfterLast(line, " @ ")
        if (lineAndColumn != null) {
          val matcher = LINE_AND_COLUMN_PATTERN.matcher(lineAndColumn)
          if (matcher.find()) {
            columnNumber = matcher.group(2).toInt()
          }
        }
        info = OpenFileHyperlinkInfo(myProject, file, max(lineNumber - 1, 0), columnNumber)
      }
      val attributes = HYPERLINK_ATTRIBUTES.clone()
      if (
        myProject != null && !ProjectRootManager.getInstance(myProject).fileIndex.isInContent(file)
      ) {
        val color = NamedColorUtil.getInactiveTextColor()
        attributes.foregroundColor = color
        attributes.effectColor = color
      }
      return Filter.Result(textStartOffset, highlightEndOffset, info, attributes)
    }

    companion object {
      val LINE_AND_COLUMN_PATTERN: Pattern = Pattern.compile("line (\\d+), column (\\d+)\\.")

      private val HYPERLINK_ATTRIBUTES: TextAttributes =
        EditorColorsManager.getInstance()
          .globalScheme
          .getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES)
    }
  }
}