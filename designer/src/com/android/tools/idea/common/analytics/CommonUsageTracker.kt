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
package com.android.tools.idea.common.analytics

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.GradleApplicationIdProvider
import com.android.tools.idea.run.NonGradleApplicationIdProvider
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult
import org.jetbrains.android.facet.AndroidFacet
import java.util.function.Consumer

/**
 * Interface for usage tracking in the design tools. Note that implementations of these methods should aim to return immediately.
 */
interface CommonUsageTracker {

  /**
   * Logs a design tools event in the usage tracker. Note that rendering actions should be logged through the [logRenderResult] method so it
   * contains additional information about the render result.
   */
  fun logAction(eventType: LayoutEditorEvent.LayoutEditorEventType)

  /**
   * Logs a render action.
   *
   * @param trigger The event that triggered the render action or null if not known.
   */
  fun logRenderResult(trigger: LayoutEditorRenderResult.Trigger?, result: RenderResult, totalRenderTimeMs: Long, wasInflated: Boolean)

  /**
   * Logs the given design tools event. This method will return immediately.
   *
   * @param eventType The event type to log
   * @param consumer  An optional [Consumer] used to add additional information to a [LayoutEditorEvent.Builder]
   * about the given event
   */
  fun logStudioEvent(eventType: LayoutEditorEvent.LayoutEditorEventType, consumer: Consumer<LayoutEditorEvent.Builder>?)

  companion object {

    val NOP_TRACKER = CommonNopTracker()
    private val MANAGER = DesignerUsageTrackerManager<CommonUsageTracker, DesignSurface>(
      { executor, surface, eventLogger -> CommonUsageTrackerImpl(executor, surface, eventLogger) }, NOP_TRACKER
    )

    fun getInstance(surface: DesignSurface?): CommonUsageTracker {
      return MANAGER.getInstance(surface)
    }
  }
}

fun AndroidStudioEvent.Builder.setApplicationId(facet: AndroidFacet): AndroidStudioEvent.Builder {
  val appId = getApplicationId(facet)
  return setRawProjectId(appId).setProjectId(AnonymizerUtil.anonymizeUtf8(appId))
}

private fun getApplicationId(facet: AndroidFacet): String  = getApplicationIdProvider(facet).packageName

private fun getApplicationIdProvider(facet: AndroidFacet): ApplicationIdProvider =
  if (AndroidModel.get(facet) is AndroidModuleModel)
    GradleApplicationIdProvider(facet)
  else NonGradleApplicationIdProvider(facet)