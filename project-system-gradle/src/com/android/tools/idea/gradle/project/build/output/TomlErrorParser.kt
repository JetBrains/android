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

import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.project.sync.idea.issues.ErrorMessageAwareBuildIssue
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.childrenOfType
import org.toml.lang.psi.TomlInlineTable
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
      val description = StringBuilder().appendLine(BUILD_ISSUE_TITLE)
      val problemLine = reader.readLine() ?: return false

      PROBLEM_TOP_LEVEL_PATTERN.matchEntire(problemLine)?.let {
        val (catalog, tableName) = it.destructured
        description.appendLine(problemLine)
        while (true) {
          val descriptionLine = reader.readLine()
          if (descriptionLine == null || descriptionLine.startsWith("> Invalid TOML catalog definition")) break
          description.appendLine(descriptionLine)
        }
        messageConsumer.accept(extractTopLevelAlias(catalog, tableName, description, reader))
        BuildOutputParserUtils.consumeRestOfOutput(reader)
        return true
      }
      PROBLEM_LINE_PATTERN.matchEntire(problemLine)?.let {
        val catalogName = it.groupValues[1]
        description.appendLine(problemLine)
        val events = extractIssueInformation(catalogName, description, reader)
        events.forEach { messageConsumer.accept(it) }
        BuildOutputParserUtils.consumeRestOfOutput(reader)
        return events.isNotEmpty()
      }
    }
    else if (firstDescriptionLine.endsWith("Invalid catalog definition:")) {
      val description = StringBuilder().appendLine("Invalid catalog definition.")
      val problemLine = reader.readLine() ?: return false
      description.appendLine(problemLine)
      PROBLEM_ALIAS_PATTERN.matchEntire(problemLine)?.let {
        val (catalog, type, alias) = it.destructured
        val tomlTableName = TYPE_NAMING_PARSING[type] ?: return false
        val event = extractAliasInformation(
          catalog, tomlTableName, alias, description, reader
        ) ?: return false
        messageConsumer.accept(event)
        BuildOutputParserUtils.consumeRestOfOutput(reader)
        return true
      }
      PROBLEM_REFERENCE_PATTERN.matchEntire(problemLine)?.let{
        val (catalog, reference) = it.destructured
        val event = extractReferenceInformation(
          catalog, reference, description, reader
        ) ?: return false
        messageConsumer.accept(event)
        BuildOutputParserUtils.consumeRestOfOutput(reader)
        return true
      }
    }
    return false
  }

  private fun extractTopLevelAlias(catalog: String,
                                   alias: String,
                                   description: StringBuilder,
                                   reader: BuildOutputInstantReader): BuildIssueEventImpl {
    val buildIssue = object : ErrorMessageAwareBuildIssue {
      override val description: String = description.toString().trimEnd()
      override val quickFixes: List<BuildIssueQuickFix> = emptyList()
      override val title: String = BUILD_ISSUE_TITLE
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
                        .filter { it.header.key?.text == alias }.firstOrNull() ?: return fileDescriptor
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

  private fun getElementLineAndColumn(element: PsiElement): Pair<Int, Int>? {
    val document = element.containingFile.viewProvider.document ?: return null
    val lineNumber = document.getLineNumber(element.textOffset)
    val columnNumber = element.textOffset - document.getLineStartOffset(lineNumber)
    return lineNumber to columnNumber
  }

  private fun extractAliasInformation(catalog: String,
                                      type: String,
                                      alias: String,
                                      description: StringBuilder,
                                      reader: BuildOutputInstantReader): BuildIssueEventImpl? {

    while (true) {
      val descriptionLine = reader.readLine() ?: return null
      if (descriptionLine.startsWith("> Invalid catalog definition")) break
      description.appendLine(descriptionLine)
    }

    val buildIssue = object : ErrorMessageAwareBuildIssue {
      override val description: String = description.toString().trimEnd()
      override val quickFixes: List<BuildIssueQuickFix> = emptyList()
      override val title: String = BUILD_ISSUE_TITLE
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

  private enum class ReferenceSource { LIBRARY, PLUGIN }

  private fun extractReferenceInformation(catalog: String,
                                      reference: String,
                                      description: StringBuilder,
                                      reader: BuildOutputInstantReader): BuildIssueEventImpl? {

    var dependency: Pair<String, ReferenceSource>? = null
    while (true) {
      val descriptionLine = reader.readLine() ?: return null
      if (descriptionLine.startsWith("> Invalid catalog definition")) break
      if (dependency == null) {
        REASON_REFERENCE_PATTERN.matchEntire(descriptionLine)?.run {
          val (dep, _) = destructured
          dependency = dep to ReferenceSource.LIBRARY
        }
        REASON_PLUGIN_REFERENCE_PATTERN.matchEntire(descriptionLine)?.run {
          val (dep, _) = destructured
          dependency = dep to ReferenceSource.PLUGIN
        }
      }
      description.appendLine(descriptionLine)
    }
    if(dependency == null) return null
    val dependencyName = dependency!!.first
    val buildIssue = object : ErrorMessageAwareBuildIssue {
      override val description: String = description.toString().trimEnd()
      override val quickFixes: List<BuildIssueQuickFix> = emptyList()
      override val title: String = BUILD_ISSUE_TITLE
      override val buildErrorMessage: BuildErrorMessage
        get() = BuildErrorMessage.newBuilder().apply {
          errorShownType = BuildErrorMessage.ErrorType.INVALID_TOML_DEFINITION
          fileLocationIncluded = true
          fileIncludedType = BuildErrorMessage.FileType.PROJECT_FILE
          lineLocationIncluded = true
        }.build()

      private fun computeNavigable(project: Project,
                                   virtualFile: VirtualFile,
                                   tableHeader: String,
                                   predicate: (TomlKeyValue) -> Boolean): OpenFileDescriptor {
        val fileDescriptor = OpenFileDescriptor(project, virtualFile)
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return fileDescriptor
        val element = psiFile.childrenOfType<TomlTable>()
                        .filter { it.header.key?.text == tableHeader }
                        .flatMap { table -> table.childrenOfType<TomlKeyValue>() }
                        .find ( predicate ) ?: return fileDescriptor
        val (lineNumber, columnNumber) = getElementLineAndColumn(element) ?: return fileDescriptor
        return OpenFileDescriptor(project, virtualFile, lineNumber, columnNumber)
      }

      fun isLibraryAliasDeclaration(element: TomlKeyValue):Boolean{
        val content = element.value
        val (group, name) = dependencyName.split(":")
        return if (content is TomlInlineTable) {
          (
            content.findKeyValue("module", dependencyName) ||
            (content.findKeyValue("group", group) && content.findKeyValue("name", name))
          ) && content.findKeyValue("version.ref", reference)
        } else
          false
      }

      fun isPluginAliasDeclaration(element: TomlKeyValue): Boolean {
        val content = element.value
        return if (content is TomlInlineTable) {
          content.findKeyValue("id", dependencyName) && content.findKeyValue("version.ref", reference)
        }
        else
          false
      }

      override fun getNavigatable(project: Project): Navigatable? {
        val file = project.findCatalogFile(catalog) ?: return null
        return runReadAction {
          when(dependency!!.second) {
            ReferenceSource.LIBRARY -> computeNavigable(project, file, "libraries", ::isLibraryAliasDeclaration)
            ReferenceSource.PLUGIN -> computeNavigable(project, file, "plugins", ::isPluginAliasDeclaration)
          }
        }
      }
    }
    return BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR)
  }

  private fun TomlInlineTable.findKeyValue(key: String, value: String):Boolean =
    entries.any { it.key.text == key && it.value?.text == "\"$value\"" }

  private data class ErrorDescription(
    val absolutePath: String?,
    val line: Int?,
    val column: Int?
  )

  private fun extractIssueInformation(catalog: String,
                                      description: StringBuilder,
                                      reader: BuildOutputInstantReader): List<BuildIssueEventImpl> {
    val errorDescriptions = mutableListOf<ErrorDescription>()
    while (true) {
      val descriptionLine = reader.readLine() ?: return listOf()
      if (descriptionLine.startsWith("> Invalid TOML catalog definition")) break
      if (errorDescriptions.isEmpty()) {
        REASON_POSITION_PATTERN.matchEntire(descriptionLine)?.run {
          val (line, column) = destructured
          errorDescriptions.add(ErrorDescription(null, line.toIntOrNull(), column.toIntOrNull()))
        }
        REASON_FILE_AND_POSITION_PATTERN.matchEntire(descriptionLine)?.let {
          val (file, line, column) = it.destructured
          errorDescriptions.add(ErrorDescription(file, line.toIntOrNull(), column.toIntOrNull()))
        }
      }
      else {
        REASON_FILE_AND_POSITION_PATTERN_CONTINUATION.matchEntire(descriptionLine)?.let {
          val (file, line, column) = it.destructured
          errorDescriptions.add(ErrorDescription(file, line.toIntOrNull(), column.toIntOrNull()))
        }
      }
      description.appendLine(descriptionLine)
    }

    return errorDescriptions.map { error ->
      val buildIssue = object : ErrorMessageAwareBuildIssue {
        override val description: String = description.toString().trimEnd()
        override val quickFixes: List<BuildIssueQuickFix> = emptyList()
        override val title: String = BUILD_ISSUE_TITLE
        override val buildErrorMessage: BuildErrorMessage
          get() = BuildErrorMessage.newBuilder().apply {
            errorShownType = BuildErrorMessage.ErrorType.INVALID_TOML_DEFINITION
            fileLocationIncluded = true
            fileIncludedType = BuildErrorMessage.FileType.PROJECT_FILE
            lineLocationIncluded = error.line != null
          }.build()

        override fun getNavigatable(project: Project): Navigatable? {
          val tomlFile = when {
                           error.absolutePath != null -> VfsUtil.findFile(Paths.get(error.absolutePath), false)
                           catalog != null -> project.findCatalogFile(catalog)
                           else -> null
                         } ?: return null
          return OpenFileDescriptor(project, tomlFile, error.line?.minus(1) ?: 0, error.column?.minus(1) ?: 0)
        }
      }
      BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR)
    }
  }

  fun Project.findCatalogFile(catalog: String): VirtualFile? =
    VfsUtil.findFile(Projects.getBaseDirPath(this).toPath(), true)?.findChild("gradle")?.findChild("$catalog.versions.toml")

  companion object {
    const val BUILD_ISSUE_TITLE: String = "Invalid TOML catalog definition."
    val PROBLEM_LINE_PATTERN: Regex = "  - Problem: In version catalog ([^ ]+),.*".toRegex()
    val PROBLEM_ALIAS_PATTERN: Regex = "  - Problem: In version catalog ([^ ]+), invalid ([^ ]+) alias '([^ ]+)'.".toRegex()
    val PROBLEM_REFERENCE_PATTERN: Regex = "  - Problem: In version catalog ([^ ]+), version reference '([^']+)' doesn't exist.".toRegex()
    val PROBLEM_TOP_LEVEL_PATTERN: Regex = "\\s+- Problem: In version catalog ([^ ]+), unknown top level elements \\[([^ ]+)\\].*".toRegex()
    val REASON_POSITION_PATTERN: Regex = "\\s+Reason: At line ([0-9]+), column ([0-9]+):.*".toRegex()
    val REASON_FILE_AND_POSITION_PATTERN: Regex = "\\s+Reason: In file '([^']+)' at line ([0-9]+), column ([0-9]+):.*".toRegex()
    val REASON_REFERENCE_PATTERN: Regex = "\\s+Reason: Dependency '([^']+)' references version '([^']+)' which doesn't exist.".toRegex()
    val REASON_PLUGIN_REFERENCE_PATTERN: Regex = "\\s+Reason: Plugin '([^']+)' references version '([^']+)' which doesn't exist.".toRegex()
    val REASON_FILE_AND_POSITION_PATTERN_CONTINUATION: Regex = "\\s+In file '([^']+)' at line ([0-9]+), column ([0-9]+):.*".toRegex()

    private val TYPE_NAMING_PARSING = mapOf("bundle" to "bundles",
                                            "version" to "versions",
                                            "library" to "libraries",
                                            "plugin" to "plugins")
  }

}