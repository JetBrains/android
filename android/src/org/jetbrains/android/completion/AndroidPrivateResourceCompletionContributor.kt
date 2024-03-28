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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiReferenceExpression
import org.jetbrains.android.facet.AndroidFacet

/** [CompletionContributor] that filters private resources from autocomplete. */
class AndroidPrivateResourceCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, resultSet: CompletionResultSet) {
    super.fillCompletionVariants(parameters, resultSet)
    val position = parameters.position
    val facet = AndroidFacet.getInstance(position) ?: return
    val filterPrivateResources = shouldFilterPrivateResources(position, facet)
    resultSet.runRemainingContributors(parameters) {
      if (!filterPrivateResources || !isForPrivateResource(it, facet)) resultSet.passResult(it)
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
