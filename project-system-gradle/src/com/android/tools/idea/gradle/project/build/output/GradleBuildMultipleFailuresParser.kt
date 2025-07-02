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

import com.android.tools.idea.gradle.project.build.errors.DeprecatedJavaLanguageLevelIssueChecker
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.DuplicateMessageAware
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import org.jetbrains.plugins.gradle.execution.GradleConsoleFilter
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.UnresolvedDependencyBuildIssue
import java.io.File
import java.util.function.Consumer
import kotlin.collections.joinToString
import kotlin.collections.mutableListOf
import kotlin.text.StringBuilder

class GradleBuildMultipleFailuresParser() : BuildOutputParser {

  /**
   * See org.gradle.internal.buildevents.BuildExceptionReporter.renderMultipleBuildExceptions
   * The output pattern is:
   *
   * ```
   * FAILURE: Build completed with N failures.
   *
   * i: Task failed with an exception.
   * -----------
   * * Where:
   * ...
   * * What went wrong:
   * ...
   * * Try:
   * ...
   * * Exception is:
   * ...
   * ==============================================================================
   * Repeat...
   * ```
   */
  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    if (!line.startsWith("FAILURE: Build completed with ")) return false
    if (!reader.readLine().isNullOrBlank()) return false

    val failuresNumber = Regex("FAILURE: Build completed with (\\d+) failures.").matchEntire(line)?.let {
      it.groups.get(1)?.value?.toIntOrNull()
    }
    if (failuresNumber == null) return false

    val parsedFailures = (1..failuresNumber).map {
      parseFailureDetails(it, reader)
    }

    val results: List<Boolean> = parsedFailures.filterNotNull().map { parsed ->
      if (parsed.whatWentWrongSectionLines.isEmpty()) return@map false

      val locationLine = parsed.whereSectionLines.firstOrNull()
      val filter = GradleConsoleFilter(null)
        .takeIf { locationLine != null && it.applyFilter(locationLine, locationLine.length) != null }

      val description = buildString {
        if (!locationLine.isNullOrBlank()) {
          appendLine(locationLine).appendLine()
        }
        append(parsed.whatWentWrongSectionText)
      }

      val taskName = parsed.whatWentWrongSectionLines.firstOrNull { it.startsWith("Execution failed for task '") }
        ?.substringAfter("Execution failed for task '")
        ?.substringBefore("'.")
      val parentId: Any = taskName ?: reader.parentEventId

      val reasonLine = parsed.whatWentWrongSectionLines
        .lastOrNull { it.trimStart().startsWith("> ") }
        ?.substringAfter("> ")?.trimEnd('.')
                   ?: parsed.whatWentWrongSectionLines.first()

      processErrorMessage(
        parentId,
        reasonLine,
        description,
        parsed.trySectionText,
        parsed.exceptionSectionText,
        filter,
        messageConsumer
      )
      return@map true
    }

    return results.any { it == true }
  }

  /*
   * i: Task failed with an exception.
   * -----------
   * * Where:
   * ...
   * * What went wrong:
   * ...
   * * Try:
   * ...
   * * Exception is:
   * ...
   * ==============================================================================
   */
  private class ParsedDetails {
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
  }

  private fun parseFailureDetails(id: Int, reader: BuildOutputInstantReader): ParsedDetails? {
    if (reader.readNextNonEmptyLine() != "$id: Task failed with an exception.") return null
    if (reader.readLine() != "-----------") return null
    val result = ParsedDetails()
    var currentBuilder: MutableList<String>? = null
    while (true) {
      val line = reader.readLine()
      if (line == null || line == "==============================================================================") break
      if (result.headerToSection.containsKey(line)) {
        currentBuilder = result.headerToSection[line]
      }
      else {
        currentBuilder?.add(line)
      }
    }
    return result
  }

  private fun BuildOutputInstantReader.readNextNonEmptyLine(): String? {
    while (true) {
      val line = readLine()
      if (line == null || line.isNotBlank()) return line
    }
  }

  private fun processErrorMessage(
    parentId: Any,
    reasonLine: String,
    errorText: String,
    trySuggestions: String,
    exception: String,
    fileFilter: GradleConsoleFilter?,
    messageConsumer: Consumer<in BuildEvent>
  ) {
    // compilation errors should be added by the respective compiler output parser.
    // In GradleBuildScriptErrorParser in case of single failure parsing is just aborted
    // giving chance to other compilation parsers to parse the following lines.
    // However here we can not do this, as compilation errors message is only one of the parsed failures.
    // Instead, take this message text and run compilation parsers on it giving them the chance.
    if (reasonLine.isCompilationFailureLine()) {
      parseCompilationFailure(parentId, errorText, messageConsumer)
      return
    }

    val filePosition: FilePosition?
    if (fileFilter != null) {
      filePosition = FilePosition(File(fileFilter.filteredFileName), fileFilter.filteredLineNumber - 1, 0)
    }
    else {
      filePosition = null
    }

    //TODO (b/362959090): The following parsers need to be refactored to be able to parse messages from here.
    //  Might need to convert them to issue checkers as below. They are not currently implemented like that because of faults in
    //  parsing in GradleBuildScriptErrorParser, but it can be fixed.
    //  ConfigurationCacheErrorParser.kt
    //  DeclarativeErrorParser.kt
    //  FilteringCompilationParsers.kt
    //  GradleBuildOutputParser
    //  TomlErrorParser.kt

    // TODO (b/362959090): Issue checkers also should be adapted to analyze multi-exceptions to support this fully.
    //      For now it is better to leave them be and post everything that is parsed here.
    //      There might be duplications but it is better than missing errors.
    //      For now only allow issue checkers that are verified to generate messages from here
    val filteredIssuesCheckList = GradleIssueChecker.getKnownIssuesCheckList().filter {
      //TODO (b/362959090): check for other already suitable issue checkers. Suitable are the ones that use the following call to generate
      //  events rather than to simply ignore events.
      it is DeprecatedJavaLanguageLevelIssueChecker
    }
    for (issueChecker in filteredIssuesCheckList) {
      if (issueChecker.consumeBuildOutputFailureMessage(errorText, reasonLine, exception, filePosition, parentId, messageConsumer)) {
        return
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

  private fun String.isCompilationFailureLine(): Boolean =
    this.startsWith("Compilation failed") ||
    this == "Compilation error. See log for more details" ||
    this == "Script compilation error:" ||
    this.contains("compiler failed")

  private fun parseCompilationFailure(
    parentId: Any,
    errorText: String,
    messageConsumer: Consumer<in BuildEvent>
  ): Boolean {
    // For now there seems to be one parser that is supposed to parse messages from failures.
    // If there is a need to add more, this function should become more like BuildOutputInstantReaderImpl
    // runnable iterating over all parsers.
    val parser by lazy { FilteringGradleCompilationReportParser() }
    val reader = LinesBuildOutputInstantReader(errorText, parentId)
    while (true) {
      val line = reader.readLine() ?: return false
      if(parser.parse(line, reader, messageConsumer)) return true
    }
    return false
  }
}
