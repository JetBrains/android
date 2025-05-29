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

import com.intellij.build.events.BuildEvent
import com.intellij.build.output.BuildOutputInstantReader
import java.util.function.Consumer

class GradleBuildMultipleFailuresParser(
  failureHandlers: List<FailureDetailsHandler>
) : GradleBuildFailureParser(
  failureHandlers = failureHandlers,
  // TODO (b/362959090): Issue checkers also should be adapted to analyze multi-exceptions to support this fully.
  //      For now it is better to leave them be and post everything that is parsed here.
  //      There might be duplications but it is better than missing errors.
  //      For now only allow issue checkers that are verified to generate messages from here
  //TODO (b/362959090): check for any already suitable issue checkers. Suitable are the ones that use the following call to generate
  //  events rather than to simply ignore events.
  knownIssuesCheckers = emptyList()
) {

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

      val parentId: Any = parsed.taskName ?: reader.parentEventId

      return@map processErrorMessage(
        parentId,
        parsed,
        messageConsumer
      )
    }

    return results.any { it == true }
  }

  private fun parseFailureDetails(id: Int, reader: BuildOutputInstantReader): ParsedFailureDetails? {
    if (reader.readNextNonEmptyLine() != "$id: Task failed with an exception.") return null
    if (reader.readLine() != "-----------") return null
    val result = ParsedFailureDetails()
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

  override fun handleCompilationFailure(parentId: Any,
                                        errorText: String,
                                        messageConsumer: Consumer<in BuildEvent>) {
    // In GradleBuildScriptErrorParser in case of single failure parsing is just aborted
    // giving chance to other compilation parsers to parse the following lines.
    // However here we can not do this, as compilation errors message is only one of the parsed failures.
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
}
