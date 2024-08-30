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

import com.android.tools.idea.gradle.project.sync.idea.issues.ErrorMessageAwareBuildIssue
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.psi.util.childrenOfType
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

class InvalidAliasHandler: TomlErrorHandler {
  private val TYPE_NAMING_PARSING = mapOf("bundle" to "bundles",
                                          "version" to "versions",
                                          "library" to "libraries",
                                          "plugin" to "plugins")
  private val PROBLEM_ALIAS_PATTERN: Regex = "  - Problem: In version catalog ([^ ]+), invalid ([^ ]+) alias '([^ ]+)'.".toRegex()

  override fun tryExtractMessage(reader: ResettableReader): List<BuildIssueEvent> {
    val firstDescriptionLine = reader.readLine() ?: return listOf()
    if (firstDescriptionLine.endsWith("Invalid catalog definition:")) {
      val description = StringBuilder().appendLine("Invalid catalog definition.")
      val problemLine = reader.readLine() ?: return listOf()
      description.appendLine(problemLine)
      PROBLEM_ALIAS_PATTERN.matchEntire(problemLine)?.let {
        val (catalog, type, alias) = it.destructured
        val tomlTableName = TYPE_NAMING_PARSING[type] ?: return listOf()
        val event = extractAliasInformation(
          catalog, tomlTableName, alias, description, reader
        ) ?: return listOf()
        return listOf(event)
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

    while (true) {
      val descriptionLine = reader.readLine() ?: return null
      if (descriptionLine.startsWith("> Invalid catalog definition")) break
      description.appendLine(descriptionLine)
    }

    val buildIssue = object : ErrorMessageAwareBuildIssue {
      override val description: String = description.toString().trimEnd()
      override val quickFixes: List<BuildIssueQuickFix> = emptyList()
      override val title: String = TomlErrorParser.BUILD_ISSUE_TITLE
      override val buildErrorMessage: BuildErrorMessage
        get() = BuildErrorMessage.newBuilder().apply {
          errorShownType = BuildErrorMessage.ErrorType.INVALID_TOML_DEFINITION
          fileLocationIncluded = true
          fileIncludedType = BuildErrorMessage.FileType.PROJECT_FILE
          lineLocationIncluded = true
        }.build()

      private fun computeNavigatable(project: Project, virtualFile: VirtualFile): OpenFileDescriptor {
        val fileDescriptor = OpenFileDescriptor(project, virtualFile)
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return fileDescriptor
        val element = psiFile.childrenOfType<TomlTable>()
                        .filter { it.header.key?.text == type }
                        .flatMap { table -> table.childrenOfType<TomlKeyValue>() }
                        .find { it.key.text == alias } ?: return fileDescriptor
        val (lineNumber, columnNumber) = getElementLineAndColumn(element) ?: return fileDescriptor
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

}