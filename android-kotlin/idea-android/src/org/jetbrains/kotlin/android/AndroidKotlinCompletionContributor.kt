/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.kotlin.android

import com.android.SdkConstants.R_CLASS
import com.android.resources.ResourceType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.getPreviousInQualifiedChain
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.psi.PsiElement
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.util.Consumer
import org.jetbrains.android.augment.AndroidLightField
import org.jetbrains.android.dom.manifest.getPackageName
import org.jetbrains.android.dom.manifest.getTestPackageName
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.resolve.bindingContextUtil.getReferenceTargets
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import java.util.function.Predicate


/**
 * CompletionContributor for Android kotlin files. It provides:
 *  * Removing class and member references from completion when the reference resolves to the other test scope.
 *  * Filtering out private resources when completing R.type.name expressions, if any (similar to
 *   [org.jetbrains.android.AndroidJavaCompletionContributor]).
 **/
class AndroidKotlinCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val completionFilters = mutableListOf<Predicate<CompletionResult>>()

    // Manually removing completion references from the incorrect test scope: b/121022032
    if (StudioFlags.KOTLIN_INCORRECT_SCOPE_CHECK_IN_TESTS.get()) {
      if (TestSourcesFilter.isTestSources(parameters.originalFile.virtualFile, parameters.originalFile.project)) {
        completionFilters += Predicate {
          val element = it.lookupElement.psiElement
          // If a completion suggestion has been found that points to a class or member from the other test scope, ignore it instead of
          // passing it to the remaining contributors.
          element != null && !PsiSearchScopeUtil.isInScope(parameters.originalFile.resolveScope, element)
        }
      }
    }

    val position = parameters.position
    val facet = position.androidFacet
    if (facet != null && shouldFilterPrivateResources(position, facet)) {
      val lookup = ResourceRepositoryManager.getInstance(facet).resourceVisibility
      if (!lookup.isEmpty) {
        completionFilters += Predicate {
          val elem = it.lookupElement.psiElement
          if (elem is AndroidLightField) {
            if (R_CLASS == elem.containingClass?.containingClass?.name) {
              val name = elem.containingClass?.name ?: return@Predicate false
              val type = ResourceType.fromClassName(name)
              return@Predicate type != null && lookup.isPrivate(type, elem.name)
            }
          }
          false
        }
      }
    }

    result.runRemainingContributors(parameters, Consumer { completionResult ->
      if (completionFilters.none { it.test(completionResult) }) {
        result.passResult(completionResult)
      }
    })
  }

  private fun shouldFilterPrivateResources(position: PsiElement, facet: AndroidFacet): Boolean {
    val expression = position.parent as? KtSimpleNameExpression ?: return false
    val resClassReference = expression.getPreviousInQualifiedChain() as? KtSimpleNameExpression ?: return false
    val bindingContext = resClassReference.getResolutionFacade().analyze(resClassReference, BodyResolveMode.PARTIAL)
    return resClassReference.getReferenceTargets(bindingContext)
      .filterIsInstance<ClassDescriptor>()
      .any {
        val fqName = (it.containingDeclaration as? ClassDescriptor)?.importableFqName ?: return@any false
        val packageName = fqName.parent().asString()

        fqName.shortName().asString() == R_CLASS && (packageName == getPackageName(facet) ||
                                                     packageName == getTestPackageName(facet))
      }
  }
}
