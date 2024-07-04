/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.adapters

import com.android.tools.idea.common.analytics.DesignerUsageTrackerManager
import com.android.tools.idea.common.analytics.setApplicationId
import com.android.tools.idea.common.surface.DesignSurface
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.MotionLayoutEditorEvent
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.function.Consumer

/** Internal motion usage tracker. */
internal interface InternalMotionTracker {
  fun track(eventType: MotionLayoutEditorEvent.MotionLayoutEditorEventType)
}

/**
 * Factory for creating the tracker that shares thread pool, user agreeing log opt-in policy, event
 * drop-policy etc.
 */
internal class InternalMotionTrackerFactory {

  companion object {
    private val NOP_TRACKER = MotionNopTracker()
    private val MANAGER =
      DesignerUsageTrackerManager<InternalMotionTracker, DesignSurface<*>>(
        { executor, surface, eventLogger ->
          MotionUsageTrackerImpl(executor, surface, eventLogger)
        },
        NOP_TRACKER,
      )

    /**
     * Gets a shared instance of the tracker.
     *
     * @param surface - used as a key for session-info in tracker.
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
private class MotionUsageTrackerImpl
internal constructor(
  private val myExecutor: Executor,
  private val surface: DesignSurface<*>?,
  private val myConsumer: Consumer<AndroidStudioEvent.Builder>,
) : InternalMotionTracker {

  override fun track(eventType: MotionLayoutEditorEvent.MotionLayoutEditorEventType) {
    try {
      myExecutor.execute {
        val event =
          AndroidStudioEvent.newBuilder()
            .setKind(AndroidStudioEvent.EventKind.MOTION_LAYOUT_EDITOR_EVENT)
            .setMotionLayoutEditorEvent(
              MotionLayoutEditorEvent.newBuilder().setType(eventType).build()
            )
        surface?.model?.let { event.setApplicationId(surface.model!!.facet) }

        myConsumer.accept(event)
      }
    } catch (e: RejectedExecutionException) {
      // We are hitting the throttling limit
    }
  }
}

/** No-op impl. */
private class MotionNopTracker : InternalMotionTracker {
  override fun track(eventType: MotionLayoutEditorEvent.MotionLayoutEditorEventType) {
    // No op
  }
}
