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
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.psi.util.childrenOfType
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import java.nio.file.Paths
import java.util.function.Consumer

class TomlErrorParser : BuildOutputParser {
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


    // Check if it is a TOML parse error.
    if (firstDescriptionLine.endsWith("Invalid TOML catalog definition:")) {
      val description = StringBuilder().appendLine("Invalid TOML catalog definition.")
      val problemLine = reader.readLine() ?: return false
      val catalogName = PROBLEM_LINE_PATTERN.matchEntire(problemLine)?.groupValues?.get(1) ?: return false
      description.appendLine(problemLine)
      val event = extractIssueInformation(catalogName, description, reader) ?: return false
      messageConsumer.accept(event)
      BuildOutputParserUtils.consumeRestOfOutput(reader)
      return true
    } else if (firstDescriptionLine.endsWith("Invalid catalog definition:")) {
      val description = StringBuilder().appendLine("Invalid catalog definition.")
      val problemLine = reader.readLine() ?: return false
      description.appendLine(problemLine)
      val (catalog, type, alias) = PROBLEM_ALIAS_PATTERN.matchEntire(problemLine)?.destructured ?: return false
      val tomlTableName = TYPE_NAMING_PARSING[type] ?: return false
      val event = extractAliasInformation(
        catalog, tomlTableName, alias, description, reader
      ) ?: return false
      messageConsumer.accept(event)
      BuildOutputParserUtils.consumeRestOfOutput(reader)
      return true
    }
    return false
  }

  private fun extractAliasInformation(catalog: String,
                                      type: String,
                                      alias: String,
                                      description: StringBuilder,
                                      reader: BuildOutputInstantReader):BuildIssueEventImpl? {

    while (true) {
      val descriptionLine = reader.readLine() ?: return null
      if (descriptionLine.startsWith("> Invalid catalog definition")) break
      description.appendLine(descriptionLine)
    }

    val buildIssue = object : BuildIssue {
      override val description: String = description.toString().trimEnd()
      override val quickFixes: List<BuildIssueQuickFix> = emptyList()
      override val title: String = BUILD_ISSUE_TITLE

      private fun computeNavigatable(project: Project, virtualFile: VirtualFile): OpenFileDescriptor {
        val fileDescriptor = OpenFileDescriptor(project, virtualFile)
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return fileDescriptor
        val element = psiFile.childrenOfType<TomlTable>()
                        .filter { it.header.key?.text == type }
                        .flatMap { table -> table.childrenOfType<TomlKeyValue>() }
                        .find { it.key.text == alias } ?: return fileDescriptor
        val document = psiFile.viewProvider.document ?: return fileDescriptor
        val lineNumber = document.getLineNumber(element.textOffset)
        val columnNumber = element.textOffset - document.getLineStartOffset(lineNumber)
        return OpenFileDescriptor(project, virtualFile, lineNumber, columnNumber)
      }

      override fun getNavigatable(project: Project): Navigatable? {
        val file = project.findCatalogFile(catalog) ?: return null
        return runReadAction {
          computeNavigatable(project, file)
        }
      }
    }
    return BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR)
  }
  private fun extractIssueInformation(catalog: String, description: StringBuilder, reader: BuildOutputInstantReader):BuildIssueEventImpl?{
    var reasonPosition: Pair<Int?, Int?>? = null
    var absolutePath: String? = null
    while (true) {
      val descriptionLine = reader.readLine() ?: return null
      if (descriptionLine.startsWith("> Invalid TOML catalog definition")) break
      if (reasonPosition == null) {
        REASON_POSITION_PATTERN.matchEntire(descriptionLine)?.run {
          val (line, column) = destructured
          reasonPosition = line.toIntOrNull() to column.toIntOrNull()
        }
        REASON_FILE_AND_POSITION_PATTERN.matchEntire(descriptionLine)?.let {
          val (file, line, column) = it.destructured
          reasonPosition = line.toIntOrNull() to column.toIntOrNull()
          absolutePath = file
        }
      }
      description.appendLine(descriptionLine)
    }

    val buildIssue = object : BuildIssue {
      override val description: String = description.toString().trimEnd()
      override val quickFixes: List<BuildIssueQuickFix> = emptyList()
      override val title: String = BUILD_ISSUE_TITLE

      override fun getNavigatable(project: Project): Navigatable? {
        val tomlFile = when {
                         absolutePath != null -> VfsUtil.findFile(Paths.get(absolutePath), false)
                         catalog != null -> project.findCatalogFile(catalog)
                         else -> null
                       } ?: return null
        return OpenFileDescriptor(project, tomlFile, reasonPosition?.first?.minus(1) ?: 0, reasonPosition?.second?.minus(1) ?: 0)
      }
    }
    return BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR)
  }

  fun Project.findCatalogFile(catalog: String): VirtualFile? =
    baseDir?.findChild("gradle")?.findChild("$catalog.versions.toml")

  companion object {
    const val BUILD_ISSUE_TITLE: String = "Invalid TOML catalog definition."
    val PROBLEM_LINE_PATTERN: Regex = "  - Problem: In version catalog ([^ ]+), parsing failed with [0-9]+ error(?:s)?.".toRegex()
    val PROBLEM_ALIAS_PATTERN: Regex =  "  - Problem: In version catalog ([^ ]+), invalid ([^ ]+) alias '([^ ]+)'.".toRegex()
    val REASON_POSITION_PATTERN: Regex = "\\s+Reason: At line ([0-9]+), column ([0-9]+):.*".toRegex()
    val REASON_FILE_AND_POSITION_PATTERN: Regex = "\\s+Reason: In file '([^']+)' at line ([0-9]+), column ([0-9]+):.*".toRegex()

    private val TYPE_NAMING_PARSING = mapOf("bundle" to "bundles",
                              "version" to "versions",
                              "library" to "libraries",
                              "plugin" to "plugins")
  }

}