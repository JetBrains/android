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
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

class UnexpectedElementHandler : TomlErrorHandler {
  private val PROBLEM_ALIAS_PATTERN: Regex = "[a-zA-Z.:]+ On ([^ ]+) declaration '([^ ]+)' expected to find any of [a-z', ]+ but found unexpected key '([^ ]+)'.".toRegex()

  override fun tryExtractMessage(reader: ResettableReader): List<BuildIssueEvent> {
    val problemLine = reader.readLine() ?: return listOf()
    PROBLEM_ALIAS_PATTERN.matchEntire(problemLine)?.let { match ->
      val stopString = problemLine.substringAfter(": ")
      val description = StringBuilder().appendLine(BUILD_ISSUE_TITLE)
      description.appendLine(stopString)

      val (type, alias, property) = match.destructured
      val tomlTableName = TYPE_NAMING_PARSING[type] ?: return listOf()
      return extractAliasInformation(
        tomlTableName, alias, property, stopString, description, reader
      )?.let { listOf(it) } ?: listOf()
    }
    return listOf()
  }

  private fun extractAliasInformation(tomlTableName: String,
                                      alias: String,
                                      property: String,
                                      stopString: String,
                                      description: StringBuilder,
                                      reader: BuildOutputInstantReader
  ): BuildIssueEvent? {

    description.append(readUntilLine(reader, "> $stopString"))

    val buildIssue = object : TomlErrorMessageAwareIssue(description.toString()) {

      private fun getNavigable(project: Project, file: VirtualFile): OpenFileDescriptor {
        val fileDescriptor = OpenFileDescriptor(project, file)
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return fileDescriptor

        val result = psiFile.childrenOfType<TomlTable>().filter { it.header.key?.text == tomlTableName }
          .flatMap { table -> table.childrenOfType<TomlKeyValue>() }
          .find { it.key.text == alias }
        return result?.value?.childrenOfType<TomlKeyValue>()?.find { it.key.text == property }?.let { getDescriptor(it, project, file) }
               ?: fileDescriptor
      }

      override fun getNavigatable(project: Project): Navigatable? {
        for (file in project.findAllCatalogFiles()) {
          val descriptor = runReadAction { getNavigable(project, file) }
          if (descriptor.offset >= 0) return descriptor
        }
        return null
      }
    }
    return BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR)
  }

}

