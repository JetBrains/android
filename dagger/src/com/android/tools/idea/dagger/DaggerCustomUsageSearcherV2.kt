/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.dagger

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.dagger.concepts.getDaggerElement
import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageWithType
import com.intellij.util.Processor
import java.util.concurrent.ConcurrentHashMap

/** Adds custom usages for Dagger-related classes to a find usages window. */
class DaggerCustomUsageSearcherV2 : CustomUsageSearcher() {
  @WorkerThread
  override fun processElementUsages(
    element: PsiElement,
    processor: Processor<in Usage>,
    options: FindUsagesOptions
  ) {
    if (!isDaggerWithIndexEnabled()) return

    val relatedDaggerUsages = runReadAction {
      if (element.project.service<DaggerDependencyChecker>().isDaggerPresent()) {
        element.getDaggerElement()?.getRelatedDaggerElements()?.map { (relatedItem, relationName) ->
          DaggerUsage(relatedItem.psiElement, relationName)
        }
      } else {
        null
      }
    }
    relatedDaggerUsages?.forEach { processor.process(it) }
  }

  class DaggerUsage(psiElement: PsiElement, private val usageTypeName: String) :
    UsageInfo2UsageAdapter(UsageInfo(psiElement)), UsageWithType {
    override fun getUsageType(): UsageType? {
      return usageTypeMap.getOrPut(usageTypeName) { UsageType { usageTypeName } }
    }

    companion object {
      private val usageTypeMap = ConcurrentHashMap<String, UsageType>()
    }
  }
}
