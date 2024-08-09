/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import java.util.function.Consumer

class ConfigurationCacheErrorParser : BuildOutputParser {
  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    if (!line.startsWith(BuildOutputParserUtils.BUILD_FAILED_WITH_EXCEPTION_LINE)) return false
    // First skip to what went wrong line.
    if (!reader.readLine().isNullOrBlank()) return false

    var whereOrWhatLine = reader.readLine()
    if (whereOrWhatLine == "* Where:") {
      // Should be location line followed with blank
      if (reader.readLine().isNullOrBlank()) return false
      if (!reader.readLine().isNullOrBlank()) return false
      whereOrWhatLine = reader.readLine()
    }

    if (whereOrWhatLine != "* What went wrong:") return false
    val firstDescriptionLine = reader.readLine() ?: return false

    // Check if it is a configuration cache error.
    if (firstDescriptionLine != "Configuration cache problems found in this build.") return false
    val description = StringBuilder().appendLine(firstDescriptionLine)
    // All lines up to '* Try:' block should be a description we want to show.
    while (true) {
      val descriptionLine = reader.readLine() ?: return false
      if (descriptionLine == "* Try:") break
      description.appendLine(descriptionLine)
    }

    BuildOutputParserUtils.consumeRestOfOutput(reader)

    val buildIssue = object : ErrorMessageAwareBuildIssue {
      override val description: String = description.toString().trimEnd()
      override val quickFixes: List<BuildIssueQuickFix> = emptyList()
      override val title: String = BUILD_ISSUE_TITLE
      override val buildErrorMessage: BuildErrorMessage
        get() = BuildErrorMessage.newBuilder().setErrorShownType(BuildErrorMessage.ErrorType.CONFIGURATION_CACHE).build()

      override fun getNavigatable(project: Project): Navigatable? = null

    }
    messageConsumer.accept(BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR))
    return true
  }

  companion object {
    const val BUILD_ISSUE_TITLE: String = "Configuration cache problems found in this build."
  }
}