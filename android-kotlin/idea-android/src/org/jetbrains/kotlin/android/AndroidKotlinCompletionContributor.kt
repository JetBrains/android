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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.psi.search.PsiSearchScopeUtil

/**
 * CompletionContributor for Android kotlin files. It provides:
 * <ul>
 *   <li>Removing class and member references from completion when the reference resolves to the other test scope.
 * </ul>
 **/
class AndroidKotlinCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    // Manually removing completion references from the incorrect test scope: b/121022032
    if (StudioFlags.KOTLIN_INCORRECT_SCOPE_CHECK_IN_TESTS.get()) {
      if (TestSourcesFilter.isTestSources(parameters.originalFile.virtualFile, parameters.originalFile.project)) {
        result.runRemainingContributors(parameters) {
          val element = it.lookupElement.psiElement
          if (element != null && !PsiSearchScopeUtil.isInScope(parameters.originalFile.resolveScope, element)) {
            // A completion suggestion has been found that points to a class or member from the other test scope. Ignore it instead of
            // passing it to the remaining contributors.
            return@runRemainingContributors
          }
          result.passResult(it)
        }
      }
    }
  }
}