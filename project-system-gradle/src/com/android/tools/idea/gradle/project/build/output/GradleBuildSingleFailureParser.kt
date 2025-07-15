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

import com.android.tools.idea.gradle.project.build.output.BuildOutputParserUtils.isEndOfBuildOutputLine
import com.intellij.build.events.BuildEvent
import com.intellij.build.output.BuildOutputInstantReader
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import java.util.function.Consumer

class GradleBuildSingleFailureParser(failureHandlers: List<FailureDetailsHandler>) : GradleBuildFailureParser(
  failureHandlers = failureHandlers,
  knownIssuesCheckers = GradleIssueChecker.Companion.getKnownIssuesCheckList()
) {

  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    if (!line.startsWith("FAILURE: Build failed with an exception.")) return false
    if (!reader.readLine().isNullOrBlank()) return false

    val parsed = parseFailureDetails(reader) ?: return false

    if (parsed.whatWentWrongSectionLines.isEmpty()) return false

    val parentId: Any = parsed.taskName ?: reader.parentEventId

    processErrorMessage(
      parentId,
      parsed,
      messageConsumer
    )
    return true
  }

  private fun parseFailureDetails(reader: BuildOutputInstantReader): ParsedFailureDetails? {
    val result = ParsedFailureDetails()
    var currentBuilder: MutableList<String>? = null
    while (true) {
      val line = reader.readLine() ?: break
      if (line.isEndOfBuildOutputLine()) break
      if (result.headerToSection.containsKey(line)) {
        currentBuilder = result.headerToSection[line]
      }
      else {
        currentBuilder?.add(line)
      }
    }
    return result
  }
}