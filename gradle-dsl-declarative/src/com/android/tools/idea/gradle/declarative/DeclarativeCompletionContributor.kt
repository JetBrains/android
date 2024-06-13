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
package com.android.tools.idea.gradle.declarative

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.declarative.psi.DeclarativeBlock
import com.android.tools.idea.gradle.declarative.psi.DeclarativeFile
import com.android.tools.idea.gradle.declarative.psi.DeclarativeBlockGroup
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.module.ModuleUtil
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import com.intellij.util.ThreeState

private val declarativeFlag = object : PatternCondition<PsiElement>(null) {
  override fun accepts(element: PsiElement, context: ProcessingContext?): Boolean =
    StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.get()
}

private val DECLARATIVE_IN_BLOCK_SYNTAX_PATTERN: PsiElementPattern.Capture<PsiElement> = psiElement()
  .with(declarativeFlag)
  .andOr(
    psiElement().withParent(DeclarativeBlockGroup::class.java),
    psiElement().withParent(DeclarativeFile::class.java),
  )

class DeclarativeCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, DECLARATIVE_IN_BLOCK_SYNTAX_PATTERN, createCompletionProvider())
  }

  private fun createCompletionProvider(): CompletionProvider<CompletionParameters> {
    return object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = parameters.originalFile.project
        val service = DeclarativeService.getInstance(project)

        val module = ModuleUtil.findModuleForPsiElement(parameters.originalFile) ?: return
        val schema = service.getSchema(module) ?: return
        result.addAllElements(getSuggestionList(parameters.position.parent, schema).map { s: String ->
          PrioritizedLookupElement.withPriority(LookupElementBuilder.create(s), 1.0)
        })
      }
    }
  }

  private fun getSuggestionList(parent: PsiElement, schema: DeclarativeSchema): List<String> {
    val path = getPath(parent)

    if (path.isEmpty()) return schema.getRootMemberFunctions().map { it.simpleName }
    var index = 0
    var currentName = getTopLevelReceiverByName(path[index], schema)
    while (index < path.size - 1) {
      index += 1
      val dataClass = schema.getDataClassesByFqName()[currentName] ?: return emptyList()
      currentName = getReceiverByName(path[index], dataClass.memberFunctions)
    }
    val element = schema.getDataClassesByFqName()[currentName]  ?: return emptyList()
    return element.properties.map { it.name } + element.memberFunctions.map { it.simpleName }
  }

  // create path - list of identifiers from root element to parent
  private fun getPath(parent: PsiElement): List<String> {
    if (parent is DeclarativeFile) return listOf()
    val result = mutableListOf<String>()
    var current = parent.parent
    // TODO need to make this iteration Identifier oriented
    // to go bubble up through all elements with name
    while (current != null && current.parent != null && current is DeclarativeBlock) {
      current.identifier?.name?.let { result.add(it) }
      current = current.parent.parent
    }
    return result.reversed()
  }

}

class EnableAutoPopupInDeclarativeCompletion : CompletionConfidence() {
  override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
    return if (DECLARATIVE_IN_BLOCK_SYNTAX_PATTERN.accepts(contextElement)) ThreeState.NO
    else ThreeState.UNSURE
  }
}