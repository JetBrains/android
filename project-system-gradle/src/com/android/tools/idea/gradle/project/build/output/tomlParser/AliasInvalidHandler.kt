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
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.psi.util.childrenOfType
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

class AliasInvalidHandler: TomlErrorHandler {
  private val PROBLEM_ALIAS_PATTERN: Regex = "  - Alias definition '([^ ]+)' is invalid".toRegex()

  override fun tryExtractMessage(reader: ResettableReader): List<BuildIssueEvent> {
    if (reader.readLine()?.endsWith("Invalid TOML catalog definition:") == true) {
      val problemLine = reader.readLine() ?: return listOf()
      PROBLEM_ALIAS_PATTERN.matchEntire(problemLine)?.let { match ->
        val description = StringBuilder().appendLine("Invalid alias catalog definition.")
        description.appendLine(problemLine)

        val (alias) = match.destructured
        return extractAliasInformation(
          alias, description, reader
        )?.let { listOf(it) } ?: listOf()
      }
    }
    return listOf()
  }

  private fun extractAliasInformation(alias: String,
                                      description: StringBuilder,
                                      reader: BuildOutputInstantReader
  ): BuildIssueEvent? {

    description.append(readUntilLine(reader, "> Invalid TOML catalog definition"))

    val buildIssue = object : TomlErrorMessageAwareIssue(description.toString()) {
      private fun computeNavigable(project: Project, virtualFile: VirtualFile): OpenFileDescriptor {
        val fileDescriptor = OpenFileDescriptor(project, virtualFile)
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return fileDescriptor
        val element = psiFile.childrenOfType<TomlTable>()
          .flatMap { table -> table.childrenOfType<TomlKeyValue>() }
          .find { it.key.text == alias } ?: return fileDescriptor
        val (lineNumber, columnNumber) = getElementLineAndColumn(element) ?: return fileDescriptor
        return OpenFileDescriptor(project, virtualFile, lineNumber, columnNumber)
      }
      override fun getNavigatable(project: Project): Navigatable? {
        //now it looks only in default catalog
        //TODO look through all available catalogs
        val file = project.findCatalogFile("libs") ?: return null
        return runReadAction {
          computeNavigable(project, file)
        }
      }
    }
    return BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR)
  }

}