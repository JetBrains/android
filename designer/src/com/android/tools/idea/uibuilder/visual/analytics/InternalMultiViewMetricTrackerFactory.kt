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
package com.android.tools.idea.uibuilder.visual.analytics

import com.android.tools.idea.common.analytics.DesignerUsageTrackerManager
import com.android.tools.idea.common.analytics.setApplicationId
import com.android.tools.idea.common.editor.DesignToolsSplitEditor
import com.android.tools.idea.common.surface.DesignSurface
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.MultiViewEvent
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.function.Consumer

/**
 * Internal multi view usage tracker.
 */
internal interface InternalMultiViewMetricTracker {
  /**
   * Track the changes of category in Layout Validation Tool.
   */
  fun trackSetCategory(eventType: MultiViewEvent.MultiViewEventType)

  /**
   * Track open and close issue panel from Layout Validation Tool.
   */
  fun trackToggleIssuePanel(event: MultiViewEvent.ToggleIssuePanel)
}

/**
 * Factory for creating the tracker that shares thread pool, user agreeing log opt-in policy, event drop-policy etc.
 */
internal class InternalMultiViewMetricTrackerFactory {

   companion object {
     private val MANAGER = DesignerUsageTrackerManager<InternalMultiViewMetricTracker, DesignSurface<*>>(
       { executor, surface, eventLogger -> MultiViewMetricTrackerImpl(executor, surface, eventLogger) }, MultiViewNopTracker)

     /**
      * Gets a shared instance of the tracker.
      * @param surface - used as a key for session-info in tracker. If null, [MultiViewNopTracker] will be used.
      */
     fun getInstance(surface: DesignSurface<*>?) = MANAGER.getInstance(surface)
   }
}

/**
 * Default impl.
 *
 * @param myExecutor - shared thread pool for all layout editor events (some exception)
 * @param surface - key used by [UsageTracker] for session info
 * @param myConsumer - Consumer that eventually calls [UsageTracker.log]
 */
private class MultiViewMetricTrackerImpl internal constructor(
  private val myExecutor: Executor,
  private val surface: DesignSurface<*>?,
  private val myConsumer: Consumer<AndroidStudioEvent.Builder>) : InternalMultiViewMetricTracker {

  /**
   * Returns the [MultiViewEvent.AssociatedSplitEditorMode] for the associated editor to track it.
   */
  private fun getAssociatedEditorMode(): MultiViewEvent.AssociatedSplitEditorMode =
    when ((surface?.fileEditorDelegate as? DesignToolsSplitEditor)?.layout) {
      TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW -> MultiViewEvent.AssociatedSplitEditorMode.SPLIT_MODE
      TextEditorWithPreview.Layout.SHOW_EDITOR -> MultiViewEvent.AssociatedSplitEditorMode.TEXT_MODE
      TextEditorWithPreview.Layout.SHOW_PREVIEW -> MultiViewEvent.AssociatedSplitEditorMode.VISUAL_MODE
      else -> MultiViewEvent.AssociatedSplitEditorMode.UNKNOWN_MODE
  }

  override fun trackSetCategory(eventType: MultiViewEvent.MultiViewEventType) {
    try {
      myExecutor.execute {
        val event = AndroidStudioEvent.newBuilder()
          .setKind(AndroidStudioEvent.EventKind.MULTI_VIEW_EVENT)
          .setMultiViewEvent(MultiViewEvent.newBuilder()
                               .setType(eventType)
                               .setAssociatedSplitEditorMode(getAssociatedEditorMode())
                               .build())
        surface?.model?.let { event.setApplicationId(surface.model!!.facet) }

        myConsumer.accept(event)
      }
    }
    catch (e: RejectedExecutionException) {
      // We are hitting the throttling limit
    }
  }

  override fun trackToggleIssuePanel(event: MultiViewEvent.ToggleIssuePanel) {
    try {
      myExecutor.execute {
        val event = AndroidStudioEvent.newBuilder()
          .setKind(AndroidStudioEvent.EventKind.MULTI_VIEW_EVENT)
          .setMultiViewEvent(MultiViewEvent.newBuilder()
                               .setAssociatedSplitEditorMode(getAssociatedEditorMode())
                               .setToggleIssuePanel(event)
                               .build())
        surface?.model?.let { event.setApplicationId(surface.model!!.facet) }

        myConsumer.accept(event)
      }
    }
    catch (e: RejectedExecutionException) {
      // We are hitting the throttling limit
    }
  }
}

/**
 * NO-op impl for when user opts out or for tests.
 */
object MultiViewNopTracker : InternalMultiViewMetricTracker {
  override fun trackSetCategory(eventType: MultiViewEvent.MultiViewEventType) = Unit

  override fun trackToggleIssuePanel(event: MultiViewEvent.ToggleIssuePanel) = Unit
}