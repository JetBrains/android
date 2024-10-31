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

import com.android.tools.idea.gradle.project.build.output.tomlParser.TomlErrorParser.Companion.BUILD_ISSUE_START
import com.android.tools.idea.gradle.project.build.output.tomlParser.TomlErrorParser.Companion.BUILD_ISSUE_STOP_LINE
import com.android.tools.idea.gradle.project.build.output.tomlParser.TomlErrorParser.Companion.BUILD_ISSUE_TITLE
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.psi.util.childrenOfType
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

class ReferenceIssueHandler : TomlErrorHandler {
  private val PROBLEM_REFERENCE_PATTERN: Regex = "  - Problem: In version catalog ([^ ]+), version reference '([^']+)' doesn't exist.".toRegex()
  private val REASON_REFERENCE_PATTERN: Regex = "\\s+Reason: Dependency '([^']+)' references version '([^']+)' which doesn't exist.".toRegex()
  private val REASON_PLUGIN_REFERENCE_PATTERN: Regex = "\\s+Reason: Plugin '([^']+)' references version '([^']+)' which doesn't exist.".toRegex()
  override fun tryExtractMessage(reader: ResettableReader): List<BuildIssueEvent> {
    if (reader.readLine()?.endsWith(BUILD_ISSUE_START) == true) {
      val description = StringBuilder().appendLine(BUILD_ISSUE_TITLE)
      val problemLine = reader.readLine() ?: return listOf()
      description.appendLine(problemLine)
      PROBLEM_REFERENCE_PATTERN.matchEntire(problemLine)?.let {
        val (catalog, reference) = it.destructured
        val event = extractReferenceInformation(
          catalog, reference, description, reader
        ) ?: return listOf()
        return listOf(event)
      }
    }
    return listOf()
  }

  private fun extractReferenceInformation(catalog: String,
                                          reference: String,
                                          description: StringBuilder,
                                          reader: BuildOutputInstantReader
  ): BuildIssueEvent? {

    var dependency: Pair<String, ReferenceSource>? = null

    description.append(readUntilLine(reader, BUILD_ISSUE_STOP_LINE) { descriptionLine ->
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
    })

    if (dependency == null) return null
    val dependencyName = dependency!!.first
    val buildIssue = object : TomlErrorMessageAwareIssue(description.toString()) {
      private fun computeNavigable(project: Project,
                                   virtualFile: VirtualFile,
                                   tableHeader: String,
                                   predicate: (TomlKeyValue) -> Boolean): OpenFileDescriptor {
        val fileDescriptor = OpenFileDescriptor(project, virtualFile)
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return fileDescriptor
        val element = psiFile.childrenOfType<TomlTable>()
                        .filter { it.header.key?.text == tableHeader }
                        .flatMap { table -> table.childrenOfType<TomlKeyValue>() }
                        .find(predicate) ?: return fileDescriptor
        return getDescriptor(element, project, virtualFile) ?: fileDescriptor
      }

      fun isLibraryAliasDeclaration(element: TomlKeyValue): Boolean {
        val content = element.value
        val (group, name) = dependencyName.split(":")
        return if (content is TomlInlineTable) {
          (
            content.findKeyValue("module", dependencyName) ||
            (content.findKeyValue("group", group) && content.findKeyValue("name", name))
          ) && content.findKeyValue("version.ref", reference)
        }
        else
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
          when (dependency!!.second) {
            ReferenceSource.LIBRARY -> computeNavigable(project, file, "libraries", ::isLibraryAliasDeclaration)
            ReferenceSource.PLUGIN -> computeNavigable(project, file, "plugins", ::isPluginAliasDeclaration)
          }
        }
      }
    }
    return BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR)
  }
}