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

import com.android.resources.ResourceVisibility
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import org.jetbrains.android.augment.ResourceLightField


/**
 * CompletionContributor for Android kotlin files. It provides:
 *  * Filtering out private resources when completing R.type.name expressions, if any (similar to
 *   [org.jetbrains.android.AndroidJavaCompletionContributor]).
 **/
class AndroidKotlinCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    result.runRemainingContributors(parameters) { completionResult ->
      val elem = completionResult.lookupElement.psiElement
      if (!(elem is ResourceLightField && elem.resourceVisibility == ResourceVisibility.PRIVATE)) {
        result.passResult(completionResult)
      }
    }
  }
}
