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

import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.isAccessible
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiReferenceExpression
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.inspections.AndroidDeprecationInspection

/**
 * [CompletionContributor] that currently does two things: removing deprecation warnings in code where the element was not yet
 * deprecated (e.g. limited by SDK level) and filtering out private resources.
 *
 * TODO(b/292553413): See if we can unify this private resource logic with the similar code in `AndroidKotlinCompletionContributor`.
 */
class AndroidJavaCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, resultSet: CompletionResultSet) {
    super.fillCompletionVariants(parameters, resultSet)
    val position = parameters.position
    val facet = AndroidFacet.getInstance(position) ?: return
    val filterPrivateResources = shouldFilterPrivateResources(position, facet)
    resultSet.runRemainingContributors(parameters) {
      if (!filterPrivateResources || !isForPrivateResource(it, facet)) resultSet.passResult(fixDeprecationPresentation(it, parameters))
    }
  }

  /** Wrapper around a [LookupElement] that removes the text strikeout. */
  private class RemoveStrikeoutDecorator(delegate: LookupElement) : LookupElementDecorator<LookupElement?>(delegate) {
    override fun renderElement(presentation: LookupElementPresentation) {
      super.renderElement(presentation)
      presentation.isStrikeout = false
    }
  }

  companion object {
    private fun shouldFilterPrivateResources(position: PsiElement, facet: AndroidFacet): Boolean {
      // Filter out private resources when completing R.type.name expressions, if any.
      val r = (position.parent as? PsiReferenceExpression)
                ?.qualifierReferenceExpression
                ?.qualifierReferenceExpression
                ?.takeIf { it.referenceName == SdkConstants.R_CLASS}
              ?: return false
      // We do the filtering only on the R class of this module, users who explicitly reference other R classes are assumed to know
      // what they're doing. So if R is unqualified or is qualified by the package or test package name, then filter.
      val rQualifier = r.qualifierExpression ?: return true
      val rQualifierName = (rQualifier as? PsiReferenceExpression)?.qualifiedName ?: return false
      return facet.getModuleSystem().let { it.getPackageName() == rQualifierName || it.getTestPackageName() == rQualifierName }
    }

    private val PsiReferenceExpression.qualifierReferenceExpression : PsiReferenceExpression?
      get() = qualifierExpression as? PsiReferenceExpression

    /**
     * Removes the deprecation strikeout if the result is not actually deprecated at the specific location, e.g. when we are in a code
     * branch specific to an old SDK where a given [PsiElement] was not yet deprecated.
     *
     * @see AndroidDeprecationInspection.DeprecationFilter
     */
    fun fixDeprecationPresentation(
      result: CompletionResult,
      parameters: CompletionParameters
    ): CompletionResult {
      val deprecatedObj = (result.lookupElement.getObject() as? PsiDocCommentOwner)?.takeIf { it.isDeprecated } ?: return result
      // If any filters say we shouldn't consider this deprecated at this position, remove the text strikeout.
      if (AndroidDeprecationInspection.getFilters().any { it.isExcluded(deprecatedObj, parameters.position, null) }) {
        return result.withLookupElement(RemoveStrikeoutDecorator(result.lookupElement))
      }
      return result
    }

    /** Returns true iff this result is the `bar` in something of the form `R.foo.bar` and this resource is private. */
    fun isForPrivateResource(result: CompletionResult, facet: AndroidFacet): Boolean {
      // First get the field itself, e.g. R.foo.bar
      val psiField = result.lookupElement.getObject() as? PsiField ?: return false
      // Now extract the type "foo", provided it is under "R".
      val resourceType = psiField.containingClass
                           ?.takeIf { it.containingClass?.name == SdkConstants.R_CLASS } // Don't bother if it's not under R.
                           ?.name
                           ?.let { ResourceType.fromClassName(it) }
                         ?: return false  // If there's no type, we can't look it up, so return false.
      val namespace = StudioResourceRepositoryManager.getInstance(facet).namespace
      return !isAccessible(namespace, resourceType, psiField.name, facet)
    }
  }
}
