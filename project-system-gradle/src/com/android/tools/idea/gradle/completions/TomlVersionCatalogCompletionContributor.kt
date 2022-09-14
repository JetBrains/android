/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.completions

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader
import org.toml.lang.psi.TomlVisitor


internal val TOML_VERSIONS_TABLE_PATTERN = psiElement(TomlTable::class.java).with(
  object : PatternCondition<TomlTable>(null) {
    override fun accepts(tomlTable: TomlTable, context: ProcessingContext?): Boolean =
      tomlTable.header.key?.segments?.map { it.name }?.joinToString(".") == "versions"
  }
)


internal val TEST = psiElement(PsiElement::class.java).with(
  object : PatternCondition<PsiElement>(null) {
    override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean =
      t.text == "versions"
  }
)

val TOML = psiElement()
  .inFile(INSIDE_VERSIONS_TOML_FILE)
  .withLanguage(TomlLanguage)

val IN_LIBRARIES_OR_PLUGINS = psiElement()
  .andOr(
    psiElement().inside(TOML_LIBRARIES_TABLE_PATTERN),
    psiElement().inside(TOML_PLUGINS_TABLE_PATTERN)
  )

val TOML_VERSIONS_TABLE_SYNTAX_PATTERN = psiElement()
  .withParent(TomlKeySegment::class.java)
  .withSuperParent(6, TOML_VERSIONS_TABLE_PATTERN)
  .and(TOML)
  .andNot(psiElement().afterLeaf("."))

val TOML_VERSIONS_SYNTAX_PATTERN = psiElement()
  .withParent(TomlKeySegment::class.java)
  .withSuperParent(5, VERSION_KEY_VALUE_PATTERN)
  .and(TOML)
  .andNot(psiElement().afterLeaf("."))

val TOML_LIBRARIES_TABLE_SYNTAX_PATTERN = psiElement()
  .withParent(TomlKeySegment::class.java)
  .withSuperParent(6, TOML_LIBRARIES_TABLE_PATTERN)
  .and(TOML)
  .andNot(psiElement().afterLeaf("."))

val TOML_VERSION_DOT_SYNTAX_PATTERN_BEFORE = psiElement()
  .withText(".")
  .afterLeaf(psiElement().withText("version"))
  .and(TOML)
  .and(IN_LIBRARIES_OR_PLUGINS)

val TOML_VERSION_DOT_SYNTAX_PATTERN_AFTER = psiElement()
  .afterLeaf(psiElement().withText("."))
  .afterLeaf(psiElement().afterLeaf(psiElement().withText("version")))
  .and(TOML)
  .and(IN_LIBRARIES_OR_PLUGINS)

val TOML_PLUGINS_TABLE_SYNTAX_PATTERN = psiElement()
  .withParent(TomlKeySegment::class.java)
  .inFile(INSIDE_VERSIONS_TOML_FILE)
  .withSuperParent(6, TOML_PLUGINS_TABLE_PATTERN)
  .and(TOML)
  .andNot(psiElement().afterLeaf("."))


val TOML_TABLE_SYNTAX_PATTERN = psiElement()
  .withSuperParent(2, psiElement().afterLeaf(psiElement().withText("[")))
  .withSuperParent(3, TomlTableHeader::class.java)
  .and(TOML)

val VERSION_TABLE_ATTRIBUTES = listOf("prefer", "reject", "rejectAll", "require", "strictly")
val VERSION_ATTRIBUTES = VERSION_TABLE_ATTRIBUTES + "ref"
val LIBRARY_ATTRIBUTES = listOf("group", "name", "module", "version")
val PLUGIN_ATTRIBUTES = listOf("id", "version")
val MUTUALLY_EXCLUSIVE_MAP = OneToManyBiMap(mapOf(
  "module" to setOf("group","name"),
  "ref" to setOf("prefer", "reject", "rejectAll", "require", "strictly"),
  "require" to setOf("reject", "rejectAll")
))
val TABLES = listOf("versions", "libraries", "bundles", "plugins")

class OneToManyBiMap<T> constructor(input: Map<T, Set<T>>) {
  private val direct = input
  private val reverse = mutableMapOf<T, T>()

  init {
    reverse.putAll(direct.flatMap { kv -> kv.value.map { it to kv.key } })
  }

  fun getRelated(keys: Set<T>): Set<T> {
    val result = mutableSetOf<T>()
    keys.forEach { key ->
      direct[key]?.let { result.addAll(it) }
      reverse[key]?.let { result.add(it) }
    }
    return result
  }
}

class TomlVersionCatalogCompletionContributor  : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, TOML_PLUGINS_TABLE_SYNTAX_PATTERN, createKeySuggestionCompletionProvider(PLUGIN_ATTRIBUTES))
    extend(CompletionType.BASIC, TOML_LIBRARIES_TABLE_SYNTAX_PATTERN, createKeySuggestionCompletionProvider(LIBRARY_ATTRIBUTES))
    extend(CompletionType.BASIC, TOML_VERSIONS_TABLE_SYNTAX_PATTERN, createKeySuggestionCompletionProvider(VERSION_TABLE_ATTRIBUTES))
    extend(CompletionType.BASIC, TOML_VERSIONS_SYNTAX_PATTERN, createKeySuggestionCompletionProvider(VERSION_ATTRIBUTES))
    extend(CompletionType.BASIC, TOML_VERSION_DOT_SYNTAX_PATTERN_AFTER, createKeySuggestionCompletionProvider(VERSION_ATTRIBUTES))
    extend(CompletionType.BASIC, TOML_TABLE_SYNTAX_PATTERN, createTableSuggestionCompletionProvider(TABLES))
  }

  private fun createTableSuggestionCompletionProvider(list:List<String>): CompletionProvider<CompletionParameters> {
    return object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val originalFile = parameters.originalFile as? TomlFile ?: return
        val existingElements = findTableHeaders(originalFile)
        result.addAllElements(ContainerUtil.map(list - existingElements) { s: String ->
          PrioritizedLookupElement.withPriority(LookupElementBuilder.create(s), 1.0)
        })
      }
    }
  }
  private fun findTableHeaders(tomlFile: TomlFile): Set<String> {
    val result = mutableSetOf<String>()
    tomlFile.children.filterIsInstance<TomlTable>().forEach {
      it.accept(object : TomlVisitor() {
        override fun visitTable(element: TomlTable) {
          element.header.key?.segments?.map { it.name }?.forEach{ it?.let { name -> result.add(name) } }
          }
        })
    }
    return result
  }

  private fun createKeySuggestionCompletionProvider(list:List<String>): CompletionProvider<CompletionParameters> {
    return object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        var parent: PsiElement? = parameters.position
        while (parent != null && parent !is TomlInlineTable) {
          parent = parent.parent
        }
        val existingElements = if (parent != null) SearchForKeysVisitor(parent as TomlInlineTable, 2).search() else setOf()

        val calculatedCompletionOptions = list - existingElements - MUTUALLY_EXCLUSIVE_MAP.getRelated(existingElements)

        result.addAllElements(ContainerUtil.map(calculatedCompletionOptions) { s: String ->
          PrioritizedLookupElement.withPriority(LookupElementBuilder.create(s), 1.0)
        })
      }
    }
  }
}

class EnableAutoPopupInTomlVersionCatalogCompletion : CompletionConfidence() {
  override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {

    return if (TOML_PLUGINS_TABLE_SYNTAX_PATTERN.accepts(contextElement) ||
               TOML_LIBRARIES_TABLE_SYNTAX_PATTERN.accepts(contextElement) ||
               TOML_VERSIONS_TABLE_SYNTAX_PATTERN.accepts(contextElement) ||
               TOML_VERSIONS_SYNTAX_PATTERN.accepts(contextElement) ||
               TOML_VERSION_DOT_SYNTAX_PATTERN_BEFORE.accepts(contextElement) ||
               TOML_TABLE_SYNTAX_PATTERN.accepts(contextElement) ) ThreeState.NO
    else ThreeState.UNSURE
  }
}

class SearchForKeysVisitor(val start: TomlInlineTable, private val checkDepth: Int) {
  val set = mutableSetOf<String>()
  fun search(): Set<String> {
    set.clear()
    visitInlineTable(start, 0)
    return set.toSet()
  }

  private fun visitKey(key: TomlKey) {
    key.segments.forEach { it.name?.let { name -> set.add(name) } }
  }

  private fun visitInlineTable(table: TomlInlineTable, level: Int) {
    if (level >= checkDepth) return
    table.entries.forEach {
      visitKey(it.key)
      it.value?.let { v -> if (v is TomlInlineTable) visitInlineTable(v, level + 1) }
    }

  }
}