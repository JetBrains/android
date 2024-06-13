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
package org.jetbrains.android.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import org.jetbrains.android.inspections.AndroidDeprecationInspection

/** [CompletionContributor] that removes strikeout from deprecated items that are not deprecated in the current context. */
class AndroidDeprecationPresentationCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    result.runRemainingContributors(parameters) {
     result.passResult(fixDeprecationPresentation(it, parameters))
    }
  }
}

/** Decorator around a [LookupElement] that overrides the text strikeout. */
private class OverrideStrikeoutDecorator(delegate: LookupElement, private val isStrikeout: Boolean) : LookupElementDecorator<LookupElement?>(delegate) {
  override fun renderElement(presentation: LookupElementPresentation) {
    super.renderElement(presentation)
    presentation.isStrikeout = isStrikeout
  }
}

/**
 * Removes the deprecation strikeout if the result is not actually deprecated at the specific location, e.g. when we are in a code
 * branch specific to an old SDK where a given [PsiElement] was not yet deprecated.
 *
 * @see AndroidDeprecationInspection.DeprecationFilter
 */
private fun fixDeprecationPresentation(
  result: CompletionResult,
  parameters: CompletionParameters
): CompletionResult {
  val deprecatedObj = (result.lookupElement.psiElement as? PsiDocCommentOwner)?.takeIf { it.isDeprecated } ?: return result
  // If any filters say we shouldn't consider this deprecated at this position, remove the text strikeout.
  // Note: This does not currently work for Kotlin as the AndroidDeprecationInspection filters don't seem to work for Kotlin.
  if (AndroidDeprecationInspection.getFilters().any { it.isExcluded(deprecatedObj, parameters.position, null) }) {
    return result.withLookupElement(OverrideStrikeoutDecorator(result.lookupElement, false))
  }
  return result
}
