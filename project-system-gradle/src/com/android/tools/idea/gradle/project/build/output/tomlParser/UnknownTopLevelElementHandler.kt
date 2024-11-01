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

import com.android.tools.idea.gradle.project.build.output.tomlParser.TomlErrorParser.Companion.BUILD_ISSUE_TOML_START
import com.android.tools.idea.gradle.project.build.output.tomlParser.TomlErrorParser.Companion.BUILD_ISSUE_TOML_STOP_LINE
import com.android.tools.idea.gradle.project.build.output.tomlParser.TomlErrorParser.Companion.BUILD_ISSUE_TOML_TITLE
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable

class UnknownTopLevelElementHandler : TomlErrorHandler {
  private val PROBLEM_TOP_LEVEL_PATTERN: Regex = "\\s+- Problem: In version catalog ([^ ]+), unknown top level elements \\[([^ ]+)\\].*".toRegex()

  override fun tryExtractMessage(reader: ResettableReader): List<BuildIssueEvent> {
    if (reader.readLine()?.endsWith(BUILD_ISSUE_TOML_START) == true) {
      val problemLine = reader.readLine() ?: return listOf()
      PROBLEM_TOP_LEVEL_PATTERN.matchEntire(problemLine)?.let {
        val description = StringBuilder().appendLine(BUILD_ISSUE_TOML_TITLE)

        val (catalog, tableName) = it.destructured
        description.appendLine(problemLine)
        description.append(
          readUntilLine(reader, BUILD_ISSUE_TOML_STOP_LINE)
        )
        return listOf(extractTopLevelAlias(catalog, tableName, description, reader))
      }
    }
    return listOf()
  }

  private fun extractTopLevelAlias(catalog: String,
                                   alias: String,
                                   description: StringBuilder,
                                   reader: BuildOutputInstantReader
  ): BuildIssueEvent {
    val buildIssue = object : TomlErrorMessageAwareIssue(description.toString()) {

      override fun getNavigatable(project: Project): Navigatable? {
        val file = project.findCatalogFile(catalog) ?: return null
        return runReadAction {
          findFirstElement(project, file, alias)
        }
      }
    }
    return BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR)
  }
}