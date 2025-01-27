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
import com.intellij.psi.util.findParentOfType
import org.gradle.internal.impldep.com.google.common.collect.ImmutableSet
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlArrayTable
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader
import com.android.tools.idea.gradle.dsl.utils.EXT_VERSIONS_TOML

class VersionsTomlAnnotator : Annotator {
  companion object {
    private val FILE_IS_GOOD_FOR_LONG_CHECKS = Key.create<Boolean>("FILE_IS_GOOD_FOR_LONG_CHECKS")
  }

  private val dependencyTables = listOf("plugins", "libraries", "bundles")
  private val tables = dependencyTables + "versions" + "metadata"
  private val reservedNames = listOf("extensions", "convention")

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!element.containingFile.name.endsWith(EXT_VERSIONS_TOML))
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

    // alias with literal
    if(element is TomlLiteral
       && element.parent is TomlKeyValue
       && grandParent is TomlTable
       && greatGrandParent is TomlFile) {

      // exit if corner case syntax
      // plugin_alias.id = ""
      // plugin_alias.version = ""
      if ((element.parent as TomlKeyValue).key.segments.size > 1) return

      checkDependencyLiteral(element, holder)
    }

    // lib dependency with module attribute
    if(element is TomlLiteral
       && element.parent is TomlKeyValue
       && (element.parent as TomlKeyValue).key.text == "module"
       && grandParent is TomlInlineTable) {
      checkModuleLiteral(element, holder)
    }

    // library reference in bundle
    if(element is TomlLiteral
      && element.parent is TomlArray
      && grandParent is TomlKeyValue
      && greatGrandParent is TomlTable
      && greatGrandParent.header.key?.text == "bundles") {
      checkBundleDuplications(element, element.parent as TomlArray, holder)
    }
  }

  private fun checkBundleDuplications(element: TomlLiteral, array: TomlArray,  holder: AnnotationHolder){
    array.elements.forEach { ref ->
      if(ref == element) return
      if(sameAliases(ref.text, element.text)){
        holder.newAnnotation(HighlightSeverity.WARNING,
                             "Duplicate reference to dependency").create()
      }
    }
  }

  private fun checkModuleLiteral(element: TomlLiteral, holder: AnnotationHolder) {
    val table = element.findParentOfType<TomlTable>() ?: return
    val name = table.header.key?.segments?.firstOrNull()?.name ?: return
    if (name == "libraries" && element.text.split(":").size != 2)
      holder.newAnnotation(HighlightSeverity.ERROR,
                           "Make sure that the module coordinates consist of 2 parts separated by colons, eg: my.group:artifact").create()
  }

  private fun checkDependencyLiteral(element: TomlLiteral, holder: AnnotationHolder) {
    val table = element.findParentOfType<TomlTable>() ?: return
    val name = table.header.key?.segments?.firstOrNull()?.name ?: return
    when (name) {
      "plugins" -> if (element.text.split(":").size != 2)
        holder.newAnnotation(HighlightSeverity.ERROR,
                             "Make sure that the coordinates consist of 2 parts separated by colons, eg: my_plugin:1.2").create()

      "libraries" -> if (element.text.split(":").size < 2)
        holder.newAnnotation(HighlightSeverity.ERROR,
                             "Make sure that the coordinates consist of 2 parts with BOM and 3 without BOM that are separated by colons.").create()

      else -> return
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
    else if (reservedNames.contains(text)) {
      holder.newAnnotation(HighlightSeverity.ERROR,
                           "Invalid alias `${text}`. Aliases '${
                             reservedNames.joinToString(",")
                           }' are reserved names in Gradle which prevents generation of accessors.").create()
    }
    else if (text.split('_').contains("class")) {
      holder.newAnnotation(HighlightSeverity.ERROR,
                           "Invalid alias `${text}`. Alias 'class' is a reserved name in Gradle which prevents generation of accessors.").create()
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