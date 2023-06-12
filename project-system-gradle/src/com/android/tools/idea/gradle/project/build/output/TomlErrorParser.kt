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

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.Navigatable
import java.nio.file.Paths
import java.util.function.Consumer
import java.util.regex.Pattern

class TomlErrorParser : BuildOutputParser {
  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    if (!line.startsWith("FAILURE: Build failed with an exception.")) return false

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
    // Check if it is a TOML parse error.
    if (!firstDescriptionLine.endsWith("Invalid TOML catalog definition:")) return false
    val description = StringBuilder().appendln("Invalid TOML catalog definition.")

    val problemLine = reader.readLine() ?: return false
    val problemLineMatcher = PROBLEM_LINE_PATTERN.matcher(problemLine)
    if (!problemLineMatcher.matches()) return false
    val catalog = problemLineMatcher.group(1)
    description.appendln(problemLine)

    var reasonPosition: Pair<Int?, Int?>? = null
    var absolutePath: String? = null
    while (true) {
      val descriptionLine = reader.readLine() ?: return false
      if (descriptionLine.startsWith("> Invalid TOML catalog definition")) break
      if (reasonPosition == null) {
        val reasonPositionLineMatcher = REASON_POSITION_PATTERN.matcher(descriptionLine)
        if (reasonPositionLineMatcher.matches()) {
          reasonPosition = reasonPositionLineMatcher.group(1).toIntOrNull() to reasonPositionLineMatcher.group(2).toIntOrNull()
        }
        val reasonFileAndPositionLineMatcher = REASON_FILE_AND_POSITION_PATTERN.matcher(descriptionLine)
        if (reasonFileAndPositionLineMatcher.matches()) {
          reasonPosition = reasonFileAndPositionLineMatcher.group(2).toIntOrNull() to reasonFileAndPositionLineMatcher.group(3).toIntOrNull()
          absolutePath = reasonFileAndPositionLineMatcher.group(1)
        }
      }
      description.appendln(descriptionLine)
    }

    val buildIssue = object : BuildIssue {
      override val description: String = description.toString().trimEnd()
      override val quickFixes: List<BuildIssueQuickFix> = emptyList()
      override val title: String = "Invalid TOML catalog definition."

      override fun getNavigatable(project: Project): Navigatable? {
        val tomlFile = when {
          absolutePath != null -> VfsUtil.findFile(Paths.get(absolutePath), false)
          catalog != null -> project.baseDir?.findChild("gradle")?.findChild("$catalog.versions.toml")
          else -> null
        } ?: return null
        return OpenFileDescriptor(project, tomlFile, reasonPosition?.first?.minus(1) ?: 0, reasonPosition?.second?.minus(1) ?: 0)
      }
    }
    messageConsumer.accept(BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR))
    return true
  }

  companion object {
    val PROBLEM_LINE_PATTERN: Pattern = Pattern.compile("  - Problem: In version catalog ([^ ]+), parsing failed with [0-9]+ error(?:s)?.")
    val REASON_POSITION_PATTERN: Pattern = Pattern.compile("\\s+Reason: At line ([0-9]+), column ([0-9]+):.*")
    val REASON_FILE_AND_POSITION_PATTERN: Pattern = Pattern.compile("\\s+Reason: In file '([^']+)' at line ([0-9]+), column ([0-9]+):.*")
  }
}