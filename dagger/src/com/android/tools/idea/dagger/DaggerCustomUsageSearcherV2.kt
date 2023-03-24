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
import com.android.tools.idea.dagger.concepts.DaggerElement
import com.android.tools.idea.dagger.concepts.getDaggerElement
import com.google.wireless.android.sdk.stats.DaggerEditorEvent
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
import kotlin.system.measureTimeMillis

/** Adds custom usages for Dagger-related classes to a find usages window. */
class DaggerCustomUsageSearcherV2 : CustomUsageSearcher() {
  @WorkerThread
  override fun processElementUsages(
    element: PsiElement,
    processor: Processor<in Usage>,
    options: FindUsagesOptions
  ) {
    if (!isDaggerWithIndexEnabled()) return

    val (metricsType, elapsedTimeMillis) =
      runReadAction { processElementUsagesInReadAction(element, processor) } ?: return

    element.project
      .service<DaggerAnalyticsTracker>()
      .trackFindUsagesNodeWasDisplayed(metricsType, elapsedTimeMillis)
  }

  private fun processElementUsagesInReadAction(
    element: PsiElement,
    processor: Processor<in Usage>
  ): Pair<DaggerEditorEvent.ElementType, Long>? {
    if (!element.project.service<DaggerDependencyChecker>().isDaggerPresent()) return null

    val metricsType: DaggerEditorEvent.ElementType?
    val elapsedTimeMillis = measureTimeMillis {
      val daggerElement = element.getDaggerElement() ?: return null

      val relatedDaggerUsages =
        daggerElement.getRelatedDaggerElements().map { (relatedItem, relationName) ->
          DaggerUsage(relatedItem, daggerElement, relationName)
        }

      relatedDaggerUsages.forEach { processor.process(it) }
      metricsType = if (relatedDaggerUsages.isNotEmpty()) daggerElement.toMetricsType() else null
    }

    return metricsType?.let { Pair(it, elapsedTimeMillis) }
  }

  class DaggerUsage(
    private val usageElement: DaggerElement,
    private val targetElement: DaggerElement,
    private val usageTypeName: String
  ) : UsageInfo2UsageAdapter(UsageInfo(usageElement.psiElement)), UsageWithType {

    override fun getUsageType(): UsageType? {
      return usageTypeMap.getOrPut(usageTypeName) { UsageType { usageTypeName } }
    }

    override fun navigate(focus: Boolean) {
      usageInfo.project
        .service<DaggerAnalyticsTracker>()
        .trackNavigation(
          DaggerEditorEvent.NavigationMetadata.NavigationContext.CONTEXT_USAGES,
          fromElement = targetElement.toMetricsType(),
          toElement = usageElement.toMetricsType()
        )
      super.navigate(focus)
    }

    companion object {
      private val usageTypeMap = ConcurrentHashMap<String, UsageType>()
    }
  }
}
