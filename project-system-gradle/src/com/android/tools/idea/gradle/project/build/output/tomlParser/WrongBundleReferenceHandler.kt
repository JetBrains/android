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
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

//- Problem: In version catalog libs, a bundle with name 'bundle' declares a dependency on 'aaa' which doesn't exist.
class WrongBundleReferenceHandler : TomlErrorHandler {
  private val PROBLEM_ALIAS_PATTERN: Regex = "\\s+- Problem: In version catalog ([^ ]+), a bundle with name '([^ ]+)' declares a dependency on '([^ ]+)' which doesn't exist\\.".toRegex()

  override fun tryExtractMessage(reader: ResettableReader): List<BuildIssueEvent> {
    if (reader.readLine()?.endsWith(BUILD_ISSUE_START) == true) {
      val problemLine = reader.readLine() ?: return listOf()
      PROBLEM_ALIAS_PATTERN.matchEntire(problemLine)?.let { match ->
        val description = StringBuilder().appendLine(BUILD_ISSUE_TITLE)
        description.appendLine(problemLine)

        val (catalog, bundle, reference) = match.destructured
        return extractBundleReference(
          catalog, bundle, reference, description, reader
        )?.let { listOf(it) } ?: listOf()
      }
    }
    return listOf()
  }

  private fun extractBundleReference(
    catalog: String,
    bundle: String,
    reference: String,
    description: StringBuilder,
    reader: BuildOutputInstantReader
  ): BuildIssueEvent? {

    description.append(readUntilLine(reader, BUILD_ISSUE_STOP_LINE))

    val buildIssue = object : TomlErrorMessageAwareIssue(description.toString()) {
      private fun computeNavigable(project: Project,
                                   virtualFile: VirtualFile): OpenFileDescriptor {
        val fileDescriptor = OpenFileDescriptor(project, virtualFile)
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return fileDescriptor
        val element = psiFile.childrenOfType<TomlTable>()
                        .filter { it.header.key?.text == "bundles" }
                        .flatMap { table -> table.childrenOfType<TomlKeyValue>() }
                        .find { it.key.text == bundle }
        return (element?.value as? TomlArray)?.elements?.find { it.text == "\"$reference\"" }?.let{
          getDescriptor(it, project, virtualFile)
        } ?: fileDescriptor
      }
      override fun getNavigatable(project: Project): Navigatable? {
        val file = project.findCatalogFile(catalog) ?: return null
        return runReadAction {
          computeNavigable(project, file)
        }
      }
    }
    return BuildIssueEventImpl(reader.parentEventId, buildIssue, MessageEvent.Kind.ERROR)
  }
}