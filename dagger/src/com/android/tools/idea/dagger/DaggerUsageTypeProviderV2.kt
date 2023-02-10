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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.psi.PsiElement
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.usages.impl.rules.UsageTypeProviderEx

/** [UsageTypeProvider] that labels Dagger-related PsiElements with the right description. */
class DaggerUsageTypeProviderV2 : UsageTypeProviderEx {
  override fun getUsageType(element: PsiElement?, targets: Array<out UsageTarget>): UsageType? {
    if (!StudioFlags.DAGGER_USING_INDEX_ENABLED.get()) return null

    // TODO(b/265846405): Implement
    return null
  }

  override fun getUsageType(element: PsiElement): UsageType? {
    // Not needed. Since this is a `UsageTypeProviderEx` instead of just a `UsageTypeProvider`, the platform will call the other overloaded
    // method instead of this one. See UsageTypeGroupingRule.java.
    throw UnsupportedOperationException()
  }
}
