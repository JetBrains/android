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

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.DuplicateMessageAware
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputParser
import org.jetbrains.plugins.gradle.execution.GradleConsoleFilter
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.UnresolvedDependencyBuildIssue
import java.io.File
import java.util.function.Consumer
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
    val filter: GradleConsoleFilter? get() {
      val locationLine = locationLine ?: return null
      return GradleConsoleFilter(null)
        .takeIf { it.applyFilter(locationLine, locationLine.length) != null }
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
    val fileFilter = parsedMessage.filter
    val reasonLine: String = parsedMessage.reasonLine
    val errorText: String = parsedMessage.description

    // compilation errors should be added by the respective compiler output parser.
    if (reasonLine.isCompilationFailureLine()) {
      handleCompilationFailure(parentId, errorText, messageConsumer)
      return false
    }

    val filePosition = if (fileFilter != null) {
      FilePosition(File(fileFilter.filteredFileName), fileFilter.filteredLineNumber - 1, 0)
    } else {
      null
    }

    for (failureHandler in failureHandlers) {
      if (failureHandler.consumeFailureMessage(parsedMessage, filePosition, parentId, messageConsumer)) {
        return true
      }
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
  ) = Unit

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

}