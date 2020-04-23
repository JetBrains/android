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

/**
 * [UsageTypeProvider] that labels Dagger providers and consumers with the right description.
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
      target.isDaggerSubcomponent && element.isDaggerComponent -> PARENT_COMPONENTS_USAGE_TYPE
      target.isDaggerComponent && element.isDaggerSubcomponent -> SUBCOMPONENTS_USAGE_TYPE
      else -> null
    }
  }

  override fun getUsageType(element: PsiElement?) = null
}

/**
 * Adds custom usages to a find usages window.
 *
 * For Dagger consumers adds providers.
 * For Dagger providers adds consumers.
 *
 * Currently doesn't work for Kotlin if "Search in text occurrences" is not selected.
 * See a bug https://youtrack.jetbrains.com/issue/KT-36657.
 */
class DaggerCustomUsageSearcher : CustomUsageSearcher() {
  @WorkerThread
  override fun processElementUsages(element: PsiElement, processor: Processor<Usage>, options: FindUsagesOptions) {
    runReadAction {
      when {
        !DAGGER_SUPPORT_ENABLED.get() -> return@runReadAction
        !element.project.service<DaggerDependencyChecker>().isDaggerPresent() -> return@runReadAction
        element.isDaggerConsumer -> processCustomUsagesForConsumers(element, processor)
        element.isDaggerProvider -> processCustomUsagesForProvider(element, processor)
        element.isDaggerModule -> processCustomUsagesForModule(element, processor)
        element.isDaggerComponent -> processCustomUsagesForComponent(element, processor)
        element.isDaggerSubcomponent -> processCustomUsagesForSubcomponent(element, processor)
        else -> return@runReadAction
      }
    }
  }

  /**
   * Adds Component that are parents to [subcomponent].
   */
  private fun processCustomUsagesForSubcomponent(subcomponent: PsiElement, processor: Processor<Usage>) {
    // subcomponent is always PsiClass or KtClass, see [isDaggerSubcomponent].
    getDaggerParentComponentsForSubcomponent(subcomponent.toPsiClass()!!)
      .forEach { processor.process(UsageInfo2UsageAdapter(UsageInfo(it))) }
  }

  /**
   *  Adds Components that use a [component] in "dependencies" attr.
   */
  private fun processCustomUsagesForComponent(component: PsiElement, processor: Processor<Usage>) {
    // component is always PsiClass or KtClass, see [isDaggerComponent].
    getDependantComponentsForComponent(component.toPsiClass()!!).forEach { processor.process(UsageInfo2UsageAdapter(UsageInfo(it))) }
    getSubcomponents(component.toPsiClass()!!).forEach { processor.process(UsageInfo2UsageAdapter(UsageInfo(it))) }
  }

  /**
   * Adds Components and Subcomponents that uses a [module] in "modules" attr.
   */
  @WorkerThread
  private fun processCustomUsagesForModule(module: PsiElement, processor: Processor<Usage>) {
    // [module] is always PsiClass or KtClass, see [isDaggerModule].
    getUsagesForDaggerModule(module.toPsiClass()!!).forEach { processor.process(UsageInfo2UsageAdapter(UsageInfo(it))) }
  }

  /**
   * Adds Dagger providers of [element] to [element]'s usages.
   */
  @WorkerThread
  private fun processCustomUsagesForConsumers(element: PsiElement, processor: Processor<Usage>) {
    getDaggerProvidersFor(element).forEach {
      val info = UsageInfo(it)
      processor.process(UsageInfo2UsageAdapter(info))
    }
  }

  /**
   * Adds Dagger consumers of [provider] to [provider]'s usages.
   * Adds Dagger component's methods associated with [provider].
   */
  @WorkerThread
  private fun processCustomUsagesForProvider(provider: PsiElement, processor: Processor<Usage>) {
    getDaggerConsumersFor(provider).forEach {
      val info = UsageInfo(it)
      processor.process(UsageInfo2UsageAdapter(info))
    }
    getDaggerComponentMethodsForProvider(provider).forEach {
      val info = UsageInfo(it)
      processor.process(UsageInfo2UsageAdapter(info))
    }
  }
}
