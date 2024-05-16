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

  val dependencyTables = listOf("plugins", "libraries", "bundles")
  val tables = dependencyTables + "versions"

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!element.containingFile.name.endsWith("versions.toml"))
      return

    if(element.isFirstElement()){
      initFileStatusFlag(element.containingFile, holder)
    }

    val grandParent = element.parent?.parent ?: return
    val greatGrandParent = grandParent.parent ?: return

    // for aliases
    if (element is TomlKey
        && element.parent is TomlKeyValue
        && grandParent is TomlTable
        && greatGrandParent is TomlFile) {
      checkDependencyAliases(element, grandParent, holder)
    }

    // table
    if (element is TomlKey
        && element.parent is TomlTableHeader
        && grandParent is TomlTable
        && greatGrandParent is TomlFile) {
      checkTableAliases(element, grandParent, holder)
    }
  }

  private fun checkDependencyAliases(element: TomlKey, table:TomlTable, holder: AnnotationHolder){
      val text = element.firstSegmentNormalizedText() ?: return

      if (!"[a-z]([a-zA-Z0-9_\\-])+".toRegex().matches(text)) {
        holder.newAnnotation(HighlightSeverity.ERROR,
                             "Invalid alias `${text}`. It must start with a lower-case letter, contain at least 2 characters "+
                             "and be made up of letters, digits and the symbols '-' or '_' only").create()
      }
      else if (".+[_\\-][0-9]".toRegex().find(text) != null) {
        holder.newAnnotation(if (table.header.key?.text in dependencyTables) HighlightSeverity.ERROR else HighlightSeverity.WARNING,
                             "Invalid alias `${text}`. There must be letter after '-' or '_ delimiter.").create()
      }
      else if ((text.endsWith("_") || text.endsWith("-"))) {
        holder.newAnnotation(HighlightSeverity.ERROR,
                             "Invalid alias `${text}`. It cannot end with '-' or '_'").create()
      } else if ("[_\\-]{2,}".toRegex().find(text) != null) {
        holder.newAnnotation(HighlightSeverity.ERROR,
                             "Invalid alias `${text}`. Cannot have more than one consecutive '-' or '_'").create()
      } else if (isInLibrariesTable(table) && "^((plugins)|(bundles)|(versions))[_\\-]?".toRegex().find(text) != null) {
        holder.newAnnotation(HighlightSeverity.ERROR,
                             "Invalid alias `${text}`. It cannot start with 'plugins', 'bundles' or 'versions' as will interfere " +
                             "with gradle naming").create()
      }
      else {
        checkAliasDuplication(element, holder)
      }
  }

  private fun isInLibrariesTable(element: TomlTable):Boolean =
    element.header.key?.segments?.firstOrNull()?.name == "libraries"

  private fun checkTableAliases(key: TomlKey, table:TomlTable, holder: AnnotationHolder){
    val text = key.segments.map { it.name }.joinToString ( "." )
    if (text !in tables) {
      holder.newAnnotation(HighlightSeverity.ERROR,
                           "Invalid table name `${text}`. It must be one of: ${tables.joinToString(", ")}").create()
    }
    else if (holder.currentAnnotationSession.getUserData(FILE_IS_GOOD_FOR_LONG_CHECKS) == true) {
      checkTableDuplication(table, holder)
    }
  }

  private fun checkAliasDuplication(element: TomlKey, holder: AnnotationHolder) {
    val parent = element.getParentOfType<TomlTable>(true) ?: return
    if (element.segments.size > 1) return
    val elementName = element.firstSegmentNormalizedText() ?: return
    val same = parent.entries.map { it.key }
      .filter { it != element && it.segments.size == 1 }
      .mapNotNull { it.firstSegmentNormalizedText() }
      .filter { sameAliases(elementName, it) }
    if (same.isNotEmpty()) {
      val suffix = if (same.size > 2) " etc." else "."
      holder.newAnnotation(HighlightSeverity.ERROR,
                           "Duplicated alias name. Effectively same as ${same.take(2).joinToString(", ")}" + suffix).create()
    }
  }

  private fun sameAliases(alias1: String, alias2: String): Boolean {
    if (alias1.length != alias2.length) return false
    for (i in alias1.indices) {
      val char1 = alias1[i].normalize()
      val char2 = alias2[i].normalize()
      if (i > 0 && alias1[i-1].normalize() == '_' && alias2[i-1].normalize() == '_') {
        if (!char1.equals(char2, true)) return false
      }
      else
        if (char1 != char2) return false
    }
    return true
  }

  private fun Char.normalize(): Char = if (this == '-' || this == '.') '_' else this

  private fun checkTableDuplication(element: TomlTable, holder: AnnotationHolder) {
    val name = element.header.key?.firstSegmentNormalizedText() ?: return
    val hasDuplicates = element.parent.children.asSequence()
      .filterIsInstance<TomlTable>()
      .filter { it != element }
      .any {
        it.header.key?.firstSegmentNormalizedText() == name
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

  // We takes only first segment and normalized it. Gives string without \u and ""
  private fun TomlKey.firstSegmentNormalizedText() = this.segments.firstOrNull()?.name
}