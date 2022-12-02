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

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DaggerEditorEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import java.util.Random

interface DaggerAnalyticsTracker {
  fun trackNavigation(
    context: DaggerEditorEvent.NavigationMetadata.NavigationContext,
    fromElement: DaggerEditorEvent.ElementType,
    toElement: DaggerEditorEvent.ElementType
  )

  /**
   * Returns true for the [percentage] of invocation.
   *
   * We use it for sampling logs for frequent events.
   */
  fun shouldLog(percentage: Int): Boolean
  fun trackFindUsagesNodeWasDisplayed(ownerElement: DaggerEditorEvent.ElementType, time: Long)
  fun trackGutterWasDisplayed(ownerElement: DaggerEditorEvent.ElementType, time: Long)
  fun trackClickOnGutter(ownerElement: DaggerEditorEvent.ElementType)
  fun trackOpenLinkFromError()
}

internal class DaggerAnalyticsTrackerImpl(private val project: Project) : DaggerAnalyticsTracker {
  companion object {
    private val random = Random()
    private const val PERCENTAGE_OF_FREQUENT_EVEN_WE_REPORT = 10
  }

  override fun trackNavigation(
    context: DaggerEditorEvent.NavigationMetadata.NavigationContext,
    fromElement: DaggerEditorEvent.ElementType,
    toElement: DaggerEditorEvent.ElementType
  ) {

    val daggerEventBuilder = DaggerEditorEvent.newBuilder()
      .setType(DaggerEditorEvent.Type.NAVIGATED)
      .setNavigationMetadata(
        DaggerEditorEvent.NavigationMetadata.newBuilder()
          .setContext(context)
          .setFromElement(fromElement)
          .setToElement(toElement)
      )

    track(daggerEventBuilder)
  }

  override fun shouldLog(percentage: Int): Boolean {
    return random.nextInt(100) >= 100 - percentage - 1
  }

  override fun trackFindUsagesNodeWasDisplayed(
    ownerElement: DaggerEditorEvent.ElementType,
    time: Long
  ) {

    val daggerEventBuilder = DaggerEditorEvent.newBuilder()
      .setType(DaggerEditorEvent.Type.FIND_USAGES_NODE_WAS_DISPLAYED)
      .setOwnerElementType(ownerElement)
      .setCalculationTimeMs(time)

    track(daggerEventBuilder)
  }

  override fun trackGutterWasDisplayed(
    ownerElement: DaggerEditorEvent.ElementType,
    time: Long
  ) {

    // Showing gutter is a quite a common event so we sample it.
    if (!shouldLog(PERCENTAGE_OF_FREQUENT_EVEN_WE_REPORT)) {
      return
    }

    val daggerEventBuilder = DaggerEditorEvent.newBuilder()
      .setType(DaggerEditorEvent.Type.GUTTER_WAS_DISPLAYED)
      .setOwnerElementType(ownerElement)
      .setCalculationTimeMs(time)

    track(daggerEventBuilder)
  }

  override fun trackClickOnGutter(
    ownerElement: DaggerEditorEvent.ElementType
  ) {

    val daggerEventBuilder = DaggerEditorEvent.newBuilder()
      .setType(DaggerEditorEvent.Type.CLICKED_ON_GUTTER)
      .setOwnerElementType(ownerElement)

    track(daggerEventBuilder)
  }

  override fun trackOpenLinkFromError() = track(DaggerEditorEvent.newBuilder().setType(DaggerEditorEvent.Type.OPENED_LINK_FROM_ERROR))

  private fun track(daggerEventBuilder: DaggerEditorEvent.Builder) {
    daggerEventBuilder.usingBuiltInAnnotationSearch = StudioFlags.DAGGER_BUILT_IN_SEARCH_ENABLED.get()
    val studioEvent: AndroidStudioEvent.Builder = AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.DAGGER_EDITOR)
      .setDaggerEditorEvent(daggerEventBuilder)

    // TODO(b/153270761): Use studioEvent.withProjectId instead of AnonymizerUtil.anonymizeUtf8(project.basePath!!),
    //  after code is moved out of monolithic core module
    studioEvent.projectId = AnonymizerUtil.anonymizeUtf8(project.basePath!!)
    UsageTracker.log(studioEvent)
  }
}

// TODO(b/157548167): Use correct types for isDaggerEntryPoint, isDaggerComponentMethod and isDaggerEntryPointMethod
internal fun getTypeForMetrics(element: PsiElement): DaggerEditorEvent.ElementType {
  return when {
    element.isDaggerConsumer -> DaggerEditorEvent.ElementType.CONSUMER
    element.isDaggerProvider -> DaggerEditorEvent.ElementType.PROVIDER
    element.isDaggerModule -> DaggerEditorEvent.ElementType.MODULE
    element.isDaggerComponent -> DaggerEditorEvent.ElementType.COMPONENT
    element.isDaggerEntryPoint -> DaggerEditorEvent.ElementType.ENTRY_POINT
    element.isDaggerSubcomponent -> DaggerEditorEvent.ElementType.SUBCOMPONENT
    element.isDaggerComponentInstantiationMethod -> DaggerEditorEvent.ElementType.COMPONENT_METHOD
    element.isDaggerEntryPointInstantiationMethod -> DaggerEditorEvent.ElementType.ENTRY_POINT_METHOD
    element.isAssistedInjectedConstructor -> DaggerEditorEvent.ElementType.ASSISTED_INJECTED_CONSTRUCTOR
    element.isAssistedFactoryMethod -> DaggerEditorEvent.ElementType.ASSISTED_FACTORY_METHOD
    else -> error("Invalid PsiElement for metrics")
  }
}