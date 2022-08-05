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
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import com.intellij.util.ThreeState
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlVisitor

val VERSION_REF_KEY_VALUE_PATTERN = psiElement(TomlKeyValue::class.java)
  .with(
    object : PatternCondition<TomlKeyValue>(null) {
      override fun accepts(t: TomlKeyValue, context: ProcessingContext?) =
        t.key.segments.map { it.name } == listOf("version", "ref")
    }
  )

val VERSION_KEY_VALUE_PATTERN = psiElement(TomlKeyValue::class.java)
  .with(
    object : PatternCondition<TomlKeyValue>(null) {
      override fun accepts(t: TomlKeyValue, context: ProcessingContext?) =
        t.key.segments.map { it.name } == listOf("version")
    }
  )

val REF_KEY_VALUE_PATTERN = psiElement(TomlKeyValue::class.java)
  .with(
    object : PatternCondition<TomlKeyValue>(null) {
      override fun accepts(t: TomlKeyValue, context: ProcessingContext?) =
        t.key.segments.map { it.name } == listOf("ref")
    }
  )

internal val TOML_PLUGINS_TABLE_PATTERN = psiElement(TomlTable::class.java).with(
  object : PatternCondition<TomlTable>(null) {
    override fun accepts(tomlTable: TomlTable, context: ProcessingContext?): Boolean =
      tomlTable.header.key?.segments?.map { it.name } == listOf("plugins")
  }
)

val TOML_VERSION_REF_PATTERN = psiElement()
  .withLanguage(TomlLanguage)
  .withParent(TomlLiteral::class.java)
  .inFile(INSIDE_VERSIONS_TOML_FILE)
  .andOr(
    psiElement().withSuperParent(2, VERSION_REF_KEY_VALUE_PATTERN),
    psiElement()
      .withSuperParent(2, REF_KEY_VALUE_PATTERN)
      .withSuperParent(4, VERSION_KEY_VALUE_PATTERN))
  .andOr(
    psiElement().inside(TOML_LIBRARIES_TABLE_PATTERN),
    psiElement().inside(TOML_PLUGINS_TABLE_PATTERN)
  )

class TomlVersionRefCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC,
           TOML_VERSION_REF_PATTERN,
            object : CompletionProvider<CompletionParameters>() {
              override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val originalFile = parameters.originalFile as? TomlFile ?: return
                result.addAllElements(findVersionKeys(originalFile).map { LookupElementBuilder.create(it) })
              }
            }
    )
  }

  private fun findVersionKeys(tomlFile: TomlFile): List<String> {
    val result = mutableListOf<String>()
    tomlFile.children.filter { it is TomlTable }.forEach {
      it.accept(object : TomlVisitor() {
        override fun visitTable(element: TomlTable) {
          if (element.header.key?.segments?.map { it.name } == listOf("versions")) {
            element.entries.forEach { kv ->
              kv.key.takeIf { it.segments.size == 1 }?.let { it.segments[0].name?.let { name -> result.add(name) } }
            }
          }
        }
      })
    }
    return result
  }
}

class EnableAutoPopupInLiteralForTomlVersionRefDependencyCompletion : CompletionConfidence() {
  override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState =
    if (TOML_VERSION_REF_PATTERN.accepts(contextElement)) ThreeState.NO else ThreeState.UNSURE
}