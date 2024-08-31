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

import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable

class InvalidAliasHandler: TomlErrorHandler {
  private val PROBLEM_ALIAS_PATTERN: Regex = "  - Problem: In version catalog ([^ ]+), invalid ([^ ]+) alias '([^ ]+)'.".toRegex()

  override fun tryExtractMessage(reader: ResettableReader): List<BuildIssueEvent> {
    if (reader.readLine()?.endsWith("Invalid catalog definition:") == true) {
      val problemLine = reader.readLine() ?: return listOf()
      PROBLEM_ALIAS_PATTERN.matchEntire(problemLine)?.let { match ->
        val description = StringBuilder().appendLine("Invalid catalog definition.")
        description.appendLine(problemLine)

        val (catalog, type, alias) = match.destructured
        val tomlTableName = TYPE_NAMING_PARSING[type] ?: return listOf()
        return extractAliasInformation(
          catalog, tomlTableName, alias, description, reader
        )?.let { listOf(it) } ?: listOf()
      }
    }
    return listOf()
  }

  private fun extractAliasInformation(catalog: String,
                                      type: String,
                                      alias: String,
                                      description: StringBuilder,
                                      reader: BuildOutputInstantReader
  ): BuildIssueEvent? {

    description.append(readUntilLine(reader, "> Invalid catalog definition"))

    val buildIssue = object : TomlErrorMessageAwareIssue(description.toString()) {
      override fun getNavigatable(project: Project): Navigatable? {
        val file = project.findCatalogFile(catalog) ?: return null
        return runReadAction {
          findFirstElement(project,file,"$type/$alias")
        }
      }
    }
    return BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR)
  }

}