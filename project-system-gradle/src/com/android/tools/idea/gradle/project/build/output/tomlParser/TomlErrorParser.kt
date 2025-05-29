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

import com.android.tools.idea.gradle.project.build.output.GradleBuildFailureParser
import com.android.tools.idea.gradle.project.build.output.GradleBuildFailureParser.FailureDetailsHandler
import com.android.tools.idea.gradle.project.build.output.LinesBuildOutputInstantReader
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import java.util.function.Consumer

class TomlErrorParser : FailureDetailsHandler {

  override fun consumeFailureMessage(
    failure: GradleBuildFailureParser.ParsedFailureDetails,
    location: FilePosition?,
    parentEventId: Any,
    messageConsumer: Consumer<in BuildEvent>
  ): Boolean {
    val handlers = listOf(
      UnknownTopLevelElementHandler(),
      InvalidAliasHandler(),
      ReferenceIssueHandler(),
      IssueAtPositionHandler(),
      AliasInvalidHandler(),
      UnexpectedElementHandler(),
      WrongBundleReferenceHandler()
    )

    handlers.forEach { handler ->
      val newReader = LinesBuildOutputInstantReader(failure.whatWentWrongSectionLines, parentEventId)
      val result = handler.tryExtractMessage(newReader)
      if (result.isNotEmpty()) {
        result.forEach { messageConsumer.accept(it) }
        return true
      }
    }
    return false
  }

  companion object {
    private const val tomlDefinition = "Invalid TOML catalog definition"
    private const val definition = "Invalid catalog definition"

    const val BUILD_ISSUE_TOML_START: String = "${tomlDefinition}:"
    const val BUILD_ISSUE_START: String = "${definition}:"

    const val BUILD_ISSUE_TITLE: String = "${definition}."
    const val BUILD_ISSUE_TOML_TITLE: String = "${tomlDefinition}."


    const val BUILD_ISSUE_TOML_STOP_LINE: String = "> $tomlDefinition"
    const val BUILD_ISSUE_STOP_LINE: String = "> $definition"

    fun Throwable.isTomlError(): Boolean {
      return message?.let {
        it.startsWith(BUILD_ISSUE_TOML_START) || it.startsWith(BUILD_ISSUE_START)
      } ?: false
    }
  }

}