/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.analytics

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.uibuilder.editor.AnimationToolbarAction
import com.android.tools.idea.uibuilder.editor.AnimationToolbarType
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AnimationPreviewEvent
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.jetbrains.annotations.VisibleForTesting

/** Handles analytics that are common across animation toolbar. */
class AnimationToolbarAnalyticsManager
@VisibleForTesting
constructor(
  private val logger: Consumer<AndroidStudioEvent.Builder>,
  private val asynchronous: Boolean,
) {
  constructor() : this(Consumer { eventBuilder -> UsageTracker.log(eventBuilder) }, true)

  companion object {
    private val EXECUTOR: ThreadPoolExecutor by lazy {
      ThreadPoolExecutor(0, 1, 1, TimeUnit.MINUTES, LinkedBlockingQueue(10))
    }
  }

  private val tracker: AnimationToolbarUsageTracker by lazy {
    val trackerImpl = AnimationToolbarUsageTrackerImpl(logger)
    if (asynchronous) AsyncTracker(EXECUTOR, trackerImpl) else trackerImpl
  }

  fun trackAction(type: AnimationToolbarType, action: AnimationToolbarAction) {
    val toolbarType =
      when (type) {
        AnimationToolbarType.LIMITED -> AnimationPreviewEvent.ToolbarType.LIMITED_ANIMATION
        AnimationToolbarType.UNLIMITED -> AnimationPreviewEvent.ToolbarType.UNLIMITED_ANIMATION
        AnimationToolbarType.ANIMATED_SELECTOR ->
          AnimationPreviewEvent.ToolbarType.ANIMATED_SELECTOR
      }

    val userAction =
      when (action) {
        AnimationToolbarAction.PLAY -> AnimationPreviewEvent.UserAction.PLAY
        AnimationToolbarAction.PAUSE -> AnimationPreviewEvent.UserAction.PAUSE
        AnimationToolbarAction.STOP -> AnimationPreviewEvent.UserAction.STOP
        AnimationToolbarAction.FRAME_FORWARD -> AnimationPreviewEvent.UserAction.FRAME_FORWARD
        AnimationToolbarAction.FRAME_BACKWARD -> AnimationPreviewEvent.UserAction.FRAME_BACKWARD
        AnimationToolbarAction.SELECT_ANIMATION -> AnimationPreviewEvent.UserAction.SELECT_ANIMATION
        AnimationToolbarAction.FRAME_CONTROL -> AnimationPreviewEvent.UserAction.FRAME_CONTROL
      }

    tracker.logToolbarEvent(toolbarType, userAction)
  }
}

@VisibleForTesting
interface AnimationToolbarUsageTracker {
  fun logToolbarEvent(
    toolbarType: AnimationPreviewEvent.ToolbarType,
    userAction: AnimationPreviewEvent.UserAction,
  )
}

private class AsyncTracker(
  private val executor: Executor,
  val delegator: AnimationToolbarUsageTracker,
) : AnimationToolbarUsageTracker {
  override fun logToolbarEvent(
    toolbarType: AnimationPreviewEvent.ToolbarType,
    userAction: AnimationPreviewEvent.UserAction,
  ) {
    try {
      executor.execute { delegator.logToolbarEvent(toolbarType, userAction) }
    } catch (e: RejectedExecutionException) {
      // We are hitting the throttling limit
    }
  }
}

private class AnimationToolbarUsageTrackerImpl(
  private val eventLogger: Consumer<AndroidStudioEvent.Builder>
) : AnimationToolbarUsageTracker {

  override fun logToolbarEvent(
    toolbarType: AnimationPreviewEvent.ToolbarType,
    userAction: AnimationPreviewEvent.UserAction,
  ) {
    val animationEvent =
      AnimationPreviewEvent.newBuilder()
        .setToolbarType(toolbarType)
        .setUserAction(userAction)
        .build()

    val layoutEditorEvent =
      LayoutEditorEvent.newBuilder()
        .setType(LayoutEditorEvent.LayoutEditorEventType.ANIMATION_PREVIEW)
        .setAnimationPreviewEvent(animationEvent)
        .build()

    val studioEvent: AndroidStudioEvent.Builder =
      AndroidStudioEvent.newBuilder()
        .setCategory(AndroidStudioEvent.EventCategory.LAYOUT_EDITOR)
        .setKind(AndroidStudioEvent.EventKind.LAYOUT_EDITOR_EVENT)
        .setLayoutEditorEvent(layoutEditorEvent)

    eventLogger.accept(studioEvent)
  }
}
