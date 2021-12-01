/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.compose.code.completion.constraintlayout

import com.android.tools.compose.code.completion.constraintlayout.provider.ConstraintSetFieldsProvider
import com.android.tools.compose.code.completion.constraintlayout.provider.ConstraintSetNamesProvider
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.json.JsonElementTypes
import com.intellij.json.JsonLanguage
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonReferenceExpression
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement

/**
 * [CompletionContributor] for the JSON5 format supported in ConstraintLayout-Compose (and MotionLayout).
 *
 * See the official wiki in [GitHub](https://github.com/androidx/constraintlayout/wiki/ConstraintSet-JSON5-syntax) to learn more about the
 * supported JSON5 syntax.
 */
class ConstraintLayoutJsonCompletionContributor : CompletionContributor() {
  init {
    extend(
      CompletionType.BASIC,
      // Complete field names in ConstraintSets
      jsonPropertyName().withConstraintSetsParentAtLevel(6),
      ConstraintSetFieldsProvider
    )
    extend(
      CompletionType.BASIC,
      // Complete ConstraintSet names in Extends keyword
      jsonPropertyStringValue()
        .withPropertyParentAtLevel(2, KeyWords.Extends),
      ConstraintSetNamesProvider
    )
  }

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (!StudioFlags.COMPOSE_CONSTRAINTLAYOUT_COMPLETION.get() ||
        parameters.position.getModuleSystem()?.usesCompose != true ||
        parameters.position.language != JsonLanguage.INSTANCE) {
      // TODO(b/207030860): Allow in other contexts once the syntax is supported outside Compose
      return
    }
    super.fillCompletionVariants(parameters, result)
  }
}

// region ConstraintLayout Pattern Helpers
private fun jsonPropertyName() = PlatformPatterns.psiElement(JsonElementTypes.IDENTIFIER)

private fun jsonPropertyStringValue() =
  PlatformPatterns.psiElement(JsonElementTypes.SINGLE_QUOTED_STRING).withParent<JsonStringLiteral>()

private fun PsiElementPattern<*, *>.withConstraintSetsParentAtLevel(level: Int) = withPropertyParentAtLevel(level, KeyWords.ConstraintSets)
// endregion

// region Kotlin Syntax Helpers
private inline fun <reified T : PsiElement> psiElement() = PlatformPatterns.psiElement(T::class.java)

private inline fun <reified T : PsiElement> PsiElementPattern<*, *>.withParent() = this.withParent(T::class.java)

private fun PsiElementPattern<*, *>.withPropertyParentAtLevel(level: Int, name: String) =
  this.withSuperParent(level, psiElement<JsonProperty>().withChild(psiElement<JsonReferenceExpression>().withText(name)).save(name))
// endregion