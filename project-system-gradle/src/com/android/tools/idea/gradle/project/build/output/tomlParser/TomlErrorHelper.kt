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

import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.project.sync.idea.issues.ErrorMessageAwareBuildIssue
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.childrenOfType
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

internal fun getElementLineAndColumn(element: PsiElement): Pair<Int, Int>? {
  val document = element.containingFile.viewProvider.document ?: return null
  val lineNumber = document.getLineNumber(element.textOffset)
  val columnNumber = element.textOffset - document.getLineStartOffset(lineNumber)
  return lineNumber to columnNumber
}

internal fun Project.findCatalogFile(catalog: String): VirtualFile? =
  VfsUtil.findFile(Projects.getBaseDirPath(this).toPath(), true)?.findChild("gradle")?.findChild("$catalog.versions.toml")

internal fun TomlInlineTable.findKeyValue(key: String, value: String):Boolean =
  entries.any { it.key.text == key && it.value?.text == "\"$value\"" }

internal enum class ReferenceSource { LIBRARY, PLUGIN }

internal fun readUntilLine(reader: BuildOutputInstantReader, stopLine: String, lineCallback: (String) -> Unit = {}): String {
  val result = StringBuffer()
  while (true) {
    val descriptionLine = reader.readLine()
    if (descriptionLine == null || descriptionLine.startsWith(stopLine)) break
    lineCallback(descriptionLine)
    result.appendLine(descriptionLine)
  }
  return result.toString()
}

abstract class TomlErrorMessageAwareIssue(_description: String) : ErrorMessageAwareBuildIssue {
  override val description: String = _description.trimEnd()
  override val quickFixes: List<BuildIssueQuickFix> = emptyList()
  override val title: String = TomlErrorParser.BUILD_ISSUE_TOML_TITLE
  override val buildErrorMessage: BuildErrorMessage
    get() = BuildErrorMessage.newBuilder().apply {
      errorShownType = BuildErrorMessage.ErrorType.INVALID_TOML_DEFINITION
      fileLocationIncluded = true
      fileIncludedType = BuildErrorMessage.FileType.PROJECT_FILE
      lineLocationIncluded = true
    }.build()
}

/**
 * Path contains at most three elements tableName/aliasName
 * Any elements can be represented as * - means all
 */
internal fun findFirstElement(project: Project, virtualFile: VirtualFile, path:String): OpenFileDescriptor {
  val fileDescriptor = OpenFileDescriptor(project, virtualFile)
  val paths = path.split("/")
  val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return fileDescriptor
  if(paths.isEmpty()) return fileDescriptor

  val tablePredicate = { table:TomlTable -> if (paths[0] != "*") table.header.key?.text == paths[0] else true }

  if(paths.size == 1){
    val result = psiFile.childrenOfType<TomlTable>().find (tablePredicate)?.let {
      getDescriptor(it, project, virtualFile)
    }
    return result ?: fileDescriptor
  }

  val aliasName = paths[1]

  val tables = psiFile.childrenOfType<TomlTable>()
    .filter (tablePredicate)
  val alias = tables.flatMap { table -> table.childrenOfType<TomlKeyValue>() }
    .find { if (aliasName != "*") it.key.text == aliasName else true }?.let { getDescriptor(it, project, virtualFile) }

  return alias ?: fileDescriptor
}

fun getDescriptor(psiElement: PsiElement, project: Project, virtualFile: VirtualFile): OpenFileDescriptor? {
  val (lineNumber, columnNumber) = getElementLineAndColumn(psiElement) ?: return null
  return OpenFileDescriptor(project, virtualFile, lineNumber, columnNumber)
}

internal val TYPE_NAMING_PARSING = mapOf("bundle" to "bundles",
                                        "version" to "versions",
                                        "library" to "libraries",
                                        "plugin" to "plugins")