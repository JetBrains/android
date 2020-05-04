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
package com.android.tools.idea.dagger

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.flags.StudioFlags.DAGGER_SUPPORT_ENABLED
import com.google.wireless.android.sdk.stats.DaggerEditorEvent
import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.usages.impl.rules.UsageTypeProviderEx
import com.intellij.util.Processor

private val PROVIDERS_USAGE_TYPE = UsageType(UIStrings.PROVIDERS)
private val CONSUMERS_USAGE_TYPE = UsageType(UIStrings.CONSUMERS)
private val EXPOSED_BY_COMPONENTS_USAGE_TYPE = UsageType(UIStrings.EXPOSED_BY_COMPONENTS)
private val PARENT_COMPONENTS_USAGE_TYPE = UsageType(UIStrings.PARENT_COMPONENTS)
private val SUBCOMPONENTS_USAGE_TYPE = UsageType(UIStrings.SUBCOMPONENTS)
private val INCLUDED_IN_COMPONENTS_USAGE_TYPE = UsageType(UIStrings.INCLUDED_IN_COMPONENTS)
private val INCLUDED_IN_MODULES_USAGE_TYPE = UsageType(UIStrings.INCLUDED_IN_MODULES)
private val MODULES_USAGE_TYPE = UsageType(UIStrings.MODULES_INCLUDED)

/**
 * [UsageTypeProvider] that labels Dagger related PsiElements with the right description.
 */
class DaggerUsageTypeProvider : UsageTypeProviderEx {
  override fun getUsageType(element: PsiElement?, targets: Array<UsageTarget>): UsageType? {
    val target = (targets.firstOrNull() as? PsiElementUsageTarget)?.element ?: return null
    return when {
      !DAGGER_SUPPORT_ENABLED.get() -> null
      element?.project?.service<DaggerDependencyChecker>()?.isDaggerPresent() != true -> null
      target.isDaggerConsumer && element.isDaggerProvider -> PROVIDERS_USAGE_TYPE
      target.isDaggerProvider && element.isDaggerConsumer -> CONSUMERS_USAGE_TYPE
      target.isDaggerProvider && element.isDaggerComponentMethod -> EXPOSED_BY_COMPONENTS_USAGE_TYPE
      target.isDaggerModule && element.isDaggerComponent -> INCLUDED_IN_COMPONENTS_USAGE_TYPE
      target.isDaggerModule && element.isDaggerModule -> INCLUDED_IN_MODULES_USAGE_TYPE
      target.isDaggerComponent && element.isDaggerComponent -> PARENT_COMPONENTS_USAGE_TYPE
      target.isDaggerComponent && element.isDaggerModule -> MODULES_USAGE_TYPE
      target.isDaggerComponent && element.isDaggerSubcomponent -> SUBCOMPONENTS_USAGE_TYPE
      target.isDaggerSubcomponent && element.isDaggerComponent -> PARENT_COMPONENTS_USAGE_TYPE
      target.isDaggerSubcomponent && element.isDaggerModule -> MODULES_USAGE_TYPE
      target.isDaggerSubcomponent && element.isDaggerSubcomponent -> {
        if (target.toPsiClass()!!.isParentOf(element.toPsiClass()!!)) SUBCOMPONENTS_USAGE_TYPE else PARENT_COMPONENTS_USAGE_TYPE
      }
      else -> null
    }
  }

  override fun getUsageType(element: PsiElement?) = null
}

/**
 * Adds custom usages for Dagger related classes to a find usages window.
 */
class DaggerCustomUsageSearcher : CustomUsageSearcher() {
  private class UsageWithAnalyticsTracking(
    usageElement: PsiElement,
    targetElement: PsiElement
  ) : UsageInfo2UsageAdapter(UsageInfo(usageElement)) {
    private val targetType = getTypeForMetrics(targetElement)
    private val usageType = getTypeForMetrics(usageElement)

    override fun navigate(focus: Boolean) {
      element.project.service<DaggerAnalyticsTracker>()
        .trackNavigation(
          DaggerEditorEvent.NavigationMetadata.NavigationContext.CONTEXT_USAGES,
          fromElement = targetType,
          toElement = usageType
        )
      super.navigate(focus)
    }
  }

  @WorkerThread
  override fun processElementUsages(element: PsiElement, processor: Processor<Usage>, options: FindUsagesOptions) {
    runReadAction {
      val usages: Collection<PsiElement> = when {
        !DAGGER_SUPPORT_ENABLED.get() -> return@runReadAction
        !element.project.service<DaggerDependencyChecker>().isDaggerPresent() -> return@runReadAction
        element.isDaggerConsumer -> getCustomUsagesForConsumers(element)
        element.isDaggerProvider -> getCustomUsagesForProvider(element)
        element.isDaggerModule -> getCustomUsagesForModule(element)
        element.isDaggerComponent -> getCustomUsagesForComponent(element)
        element.isDaggerSubcomponent -> getCustomUsagesForSubcomponent(element)
        else -> return@runReadAction
      }
      if (usages.isNotEmpty()) {
        usages.forEach { processor.process(UsageWithAnalyticsTracking(it, element)) }
        element.project.service<DaggerAnalyticsTracker>().trackFindUsagesNodeWasDisplayed(getTypeForMetrics(element))
      }
    }
  }

  private fun getCustomUsagesForSubcomponent(subcomponent: PsiElement): Collection<PsiElement> {
    // [subcomponent] is always PsiClass or KtClass or KtObjectDeclaration, see [isDaggerSubcomponent].
    val asPsiClass = subcomponent.toPsiClass()!!
    return getDaggerParentComponentsForSubcomponent(asPsiClass) +
           getModulesForComponent(asPsiClass) +
           getSubcomponents(asPsiClass)
  }

  private fun getCustomUsagesForComponent(component: PsiElement): Collection<PsiElement> {
    // [component] is always PsiClass or KtClass or KtObjectDeclaration, see [isDaggerComponent].
    val asPsiClass = component.toPsiClass()!!
    return getDependantComponentsForComponent(asPsiClass) +
           getSubcomponents(asPsiClass) +
           getModulesForComponent(asPsiClass)
  }

  @WorkerThread
  // [module] is always PsiClass or KtClass, see [isDaggerModule].
  private fun getCustomUsagesForModule(module: PsiElement) = getUsagesForDaggerModule(module.toPsiClass()!!)

  @WorkerThread
  private fun getCustomUsagesForConsumers(consumer: PsiElement) = getDaggerProvidersFor(consumer)

  @WorkerThread
  private fun getCustomUsagesForProvider(provider: PsiElement): Collection<PsiElement> = getDaggerConsumersFor(provider) +
                                                                                         getDaggerComponentMethodsForProvider(provider)
}
