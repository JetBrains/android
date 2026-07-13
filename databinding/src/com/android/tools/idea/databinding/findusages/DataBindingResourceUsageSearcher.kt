/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.databinding.findusages

import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.Processor

/**
 * Adding relevant DataBinding classes and fields to usages of Android Resources.
 *
 * @see org.jetbrains.android.AndroidResourcesFindUsagesHandlerFactory
 */
class DataBindingResourceUsageSearcher : CustomUsageSearcher() {
  override fun processElementUsages(element: PsiElement, processor: Processor<in Usage>, options: FindUsagesOptions) {
    runReadActionBlocking {
      val targets = getDataBindingSearchTargets(element)
      for (target in targets) {
        // We don't care about the result of `allMatch`, but are rather using the API to ensure
        // that we stop processing results as soon as `processor.process` results false.
        @Suppress("Noop")
        ReferencesSearch.search(target, options.searchScope).allMatch { reference ->
          processor.process(UsageInfo2UsageAdapter(UsageInfo(reference)))
        }
      }
    }
  }
}
