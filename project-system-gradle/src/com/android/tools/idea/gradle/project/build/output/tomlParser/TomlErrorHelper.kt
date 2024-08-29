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
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.toml.lang.psi.TomlInlineTable

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