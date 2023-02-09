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

import com.android.tools.idea.dagger.concepts.DaggerElement
import com.android.tools.idea.dagger.concepts.getDaggerElement
import com.android.tools.idea.dagger.localization.DaggerBundle
import com.intellij.psi.PsiElement
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.usages.impl.rules.UsageTypeProviderEx
import org.jetbrains.annotations.VisibleForTesting

/** [UsageTypeProvider] that labels Dagger-related PsiElements with the right description. */
class DaggerUsageTypeProviderV2 : UsageTypeProviderEx {
  override fun getUsageType(element: PsiElement?, targets: Array<out UsageTarget>): UsageType? {
    if (element?.isDaggerWithIndexEnabled() != true) return null

    // element: the item appearing in the "Find Usages" results
    // targets: the item(s) on which "Find Usages" was invoked
    val elementDaggerType = element.getDaggerElement()?.daggerType ?: return null
    val targetDaggerType =
      targets
        .asSequence()
        .filterIsInstance<PsiElementUsageTarget>()
        .map { it.element.getDaggerElement() }
        .firstOrNull()
        ?.daggerType
        ?: return null

    return getUsageType(elementDaggerType, targetDaggerType)
  }

  override fun getUsageType(element: PsiElement): UsageType? {
    // Not needed. Since this is a `UsageTypeProviderEx` instead of just a `UsageTypeProvider`, the
    // platform will call the other overloaded
    // method instead of this one. See UsageTypeGroupingRule.java.
    throw UnsupportedOperationException()
  }

  companion object {
    private val PROVIDERS_USAGE_TYPE = UsageType { DaggerBundle.message("providers") }
    private val CONSUMERS_USAGE_TYPE = UsageType { DaggerBundle.message("consumers") }

    @VisibleForTesting
    internal fun getUsageType(
      elementDaggerType: DaggerElement.Type,
      targetDaggerType: DaggerElement.Type
    ) =
      when {
        elementDaggerType == DaggerElement.Type.PROVIDER &&
          targetDaggerType == DaggerElement.Type.CONSUMER -> PROVIDERS_USAGE_TYPE
        elementDaggerType == DaggerElement.Type.CONSUMER &&
          targetDaggerType == DaggerElement.Type.PROVIDER -> CONSUMERS_USAGE_TYPE
        else -> null
      }
  }
}
