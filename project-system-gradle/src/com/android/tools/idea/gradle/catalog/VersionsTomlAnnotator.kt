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
package com.android.tools.idea.gradle.catalog

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader

class VersionsTomlAnnotator : Annotator {

  val tables = listOf("plugins", "versions", "libraries", "bundles")

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!element.containingFile.name.endsWith("versions.toml"))
      return
    val grandParent = element.parent.parent

    if (element is TomlKey && element.parent is TomlKeyValue && grandParent is TomlTable && grandParent.parent is TomlFile) {
      val text = element.text?.let { it.removeSurrounding("\"") }
      if (text != null && !"[a-z]([a-zA-Z0-9_.\\-])+".toRegex().matches(text))
        holder.newAnnotation(HighlightSeverity.ERROR,
                               "Invalid alias `${text}`. It must start with a lower-case letter, contain at least 2 characters "+
                               "and be made up of letters, digits and the symbols '.', '-' and '_' only").create()
    }

    if (element is TomlKey
        && element.parent is TomlTableHeader
        && grandParent is TomlTable
        && grandParent.parent is TomlFile) {
      val text = element.text.removeSurrounding("\"")
      if (text !in tables)
        holder.newAnnotation(HighlightSeverity.ERROR,
                             "Invalid table name `${text}`. It must be one of: ${tables.joinToString(", ")}").create()
    }

  }
}