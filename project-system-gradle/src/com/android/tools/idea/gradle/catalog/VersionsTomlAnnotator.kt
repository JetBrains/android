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
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.editor.fixers.range
import org.jetbrains.kotlin.idea.editor.fixers.start
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader

class VersionsTomlAnnotator : Annotator {
  companion object {
    private val FILE_IS_GOOD_FOR_LONG_CHECKS = Key.create<Boolean>("FILE_IS_GOOD_FOR_LONG_CHECKS")
  }

  val tables = listOf("plugins", "versions", "libraries", "bundles")

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!element.containingFile.name.endsWith("versions.toml"))
      return
    val grandParent = element.parent.parent

    if(element.isFirstElement()){
      initFileStatusFlag(element.containingFile, holder)
    }

    if (element is TomlKey && element.parent is TomlKeyValue && grandParent is TomlTable && grandParent.parent is TomlFile) {
      val text = element.text?.let { it.removeSurrounding("\"") }
      if (text != null && !"[a-z]([a-zA-Z0-9_.\\-])+".toRegex().matches(text)) {
        holder.newAnnotation(HighlightSeverity.ERROR,
                               "Invalid alias `${text}`. It must start with a lower-case letter, contain at least 2 characters "+
                               "and be made up of letters, digits and the symbols '.', '-' and '_' only").create()
      }
      else {
        checkAliasDuplication(element, holder)
      }
    }

    if (element is TomlKey
        && element.parent is TomlTableHeader
        && grandParent is TomlTable
        && grandParent.parent is TomlFile) {
      val text = element.text.removeSurrounding("\"")
      if (text !in tables) {
        holder.newAnnotation(HighlightSeverity.ERROR,
                             "Invalid table name `${text}`. It must be one of: ${tables.joinToString(", ")}").create()
      }
      else if (holder.currentAnnotationSession.getUserData(FILE_IS_GOOD_FOR_LONG_CHECKS) == true) {
        checkTableDuplication(grandParent, holder)
      }
    }
  }

  private fun checkAliasDuplication(element: TomlKey, holder: AnnotationHolder) {
    val parent = element.getParentOfType<TomlTable>(true) ?: return
    val elementName = element.text.removeSurrounding("\"")
    val same = parent.entries.map { it.key }
      .filter { it != element }
      .map { it.text.removeSurrounding("\"") }
      .filter { compareAliases(elementName, it) }
    if (same.isNotEmpty()) {
      val suffix = if (same.size > 2) " etc." else "."
      holder.newAnnotation(HighlightSeverity.ERROR,
                           "Duplicated alias name. Effectively same as ${same.take(2).joinToString(", ")}" + suffix).create()
    }
  }

  private fun compareAliases(alias1: String, alias2: String): Boolean {
    if (alias1.length != alias2.length) return false
    return alias1.zip(alias2).all { (a, b) -> a.normalize() == b.normalize() }
  }

  private fun Char.normalize(): Char = if (this == '-' || this == '.') '_' else this

  private fun checkTableDuplication(element: TomlTable, holder: AnnotationHolder) {
    val name = element.header.key?.text?.removeSurrounding("\"") ?: return
    val hasDuplicates = element.parent.children.asSequence()
      .filterIsInstance<TomlTable>()
      .filter { it != element }
      .any {
        it.header.key?.text?.removeSurrounding("\"") == name
      }
    if (hasDuplicates) {
      holder.newAnnotation(HighlightSeverity.ERROR, "Duplicated table name.").create()
    }
  }

  private fun PsiElement.isFirstElement(): Boolean {
    var currentElement: PsiElement = containingFile

    while (currentElement.firstChild != null)
      currentElement = currentElement.firstChild

    return this == currentElement
  }

  private fun initFileStatusFlag(psiFile: PsiFile, holder: AnnotationHolder) {
    val tables = psiFile.children.filterIsInstance<TomlTable>().count()
    // make it false if file is very wrong
    holder.currentAnnotationSession.putUserData(FILE_IS_GOOD_FOR_LONG_CHECKS, tables < 1000)
  }
}