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
import com.android.tools.idea.dagger.localization.DaggerBundle
import com.android.tools.idea.flags.StudioFlags
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.psi.PsiElement
import icons.StudioIcons
import javax.swing.Icon

/**
 * Provides [RelatedItemLineMarkerInfo] for Dagger elements.
 *
 * Wrapper around [DaggerRelatedItemLineMarkerProviderV1] and
 * [DaggerRelatedItemLineMarkerProviderV2]. By having both providers wrapped, it becomes possible
 * for them to share the user's enablement settings.
 */
class DaggerRelatedItemLineMarkerProvider : RelatedItemLineMarkerProvider() {

  /**
   * Interface for wrapped providers. [collectNavigationMarkers] is protected in
   * [RelatedItemLineMarkerProvider], so having a separate interface allows this class to call into
   * the method.
   */
  interface DaggerWrappedRelatedItemLineMarkerProvider {
    fun collectNavigationMarkers(
      element: PsiElement,
      result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    )

    fun isEnabledByDefault(): Boolean
  }

  // Both providers are instantiated and the decision of which to use is delayed until usage is
  // required. This allows the flag to be changed for debugging at runtime without requiring a
  // Studio restart. The perf impact is negligible since both objects have no state and do nothing
  // on construction.
  private val providerV1 = DaggerRelatedItemLineMarkerProviderV1()
  private val providerV2 = DaggerRelatedItemLineMarkerProviderV2()

  private fun getProvider(): DaggerWrappedRelatedItemLineMarkerProvider =
    if (StudioFlags.DAGGER_USING_INDEX_ENABLED.get()) providerV2 else providerV1

  override fun getName(): String = DaggerBundle.message("dagger.related.items")

  override fun getId(): String =
    // A custom ID is required for isEnabledByDefault to be called
    "com.android.tools.idea.dagger.DaggerRelatedItemLineMarkerProvider"

  override fun getIcon(): Icon = StudioIcons.Misc.DEPENDENCY_CONSUMER

  override fun isEnabledByDefault(): Boolean = getProvider().isEnabledByDefault()

  @WorkerThread
  override fun collectNavigationMarkers(
    element: PsiElement,
    result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
  ) = getProvider().collectNavigationMarkers(element, result)
}
