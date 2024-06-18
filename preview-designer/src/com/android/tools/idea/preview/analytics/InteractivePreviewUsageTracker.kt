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
package com.android.tools.idea.preview.analytics

import com.android.tools.idea.common.analytics.DesignerUsageTrackerManager
import com.android.tools.idea.common.surface.DesignSurface
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.InteractivePreviewEvent
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.function.Consumer

/** Interface for usage tracking in the interactive preview. */
interface InteractivePreviewUsageTracker {
  /**
   * Logs interactive session info at the end of the session (when exiting interactive preview or
   * closing the tab), including frames per second, duration and number of user interactions.
   */
  fun logInteractiveSession(fps: Int, durationMs: Int, userInteractions: Int)

  /**
   * Logs startup time of an interactive session. A period from the time a user enables interactive
   * preview to the time the user can actually interact with the preview.
   */
  fun logStartupTime(timeMs: Int, peers: Int)

  companion object {
    private val NOP_TRACKER = InteractiveNopTracker()
    private val MANAGER =
      DesignerUsageTrackerManager<InteractivePreviewUsageTracker, DesignSurface<*>>(
        { executor, _, eventLogger -> InteractivePreviewUsageTrackerImpl(executor, eventLogger) },
        NOP_TRACKER,
      )

    fun getInstance(surface: DesignSurface<*>?) = MANAGER.getInstance(surface)
  }
}

class InteractiveNopTracker : InteractivePreviewUsageTracker {
  override fun logInteractiveSession(fps: Int, durationMs: Int, userInteractions: Int) {}

  override fun logStartupTime(timeMs: Int, peers: Int) {}
}

private val LOG: Logger
  get() = Logger.getInstance(InteractivePreviewUsageTrackerImpl::class.java)

/** Usage tracking implementation for interactive previews */
class InteractivePreviewUsageTrackerImpl(
  private val myExecutor: Executor,
  private val myEventLogger: Consumer<AndroidStudioEvent.Builder>,
) : InteractivePreviewUsageTracker {

  override fun logInteractiveSession(fps: Int, durationMs: Int, userInteractions: Int) {
    logInteractiveEvent(InteractivePreviewEvent.InteractivePreviewEventType.REPORT_FPS) {
      it.fps = fps
      it.durationMs = durationMs
      it.actions = userInteractions
    }
  }

  override fun logStartupTime(timeMs: Int, peers: Int) {
    logInteractiveEvent(InteractivePreviewEvent.InteractivePreviewEventType.REPORT_STARTUP_TIME) {
      it.startupTimeMs = timeMs
      it.peerPreviews = peers
    }
  }

  /**
   * A generic method to log any [InteractivePreviewEvent]. Accepts [type] of the event and a
   * [consumer] to customize the event fileds based on its [type].
   */
  private fun logInteractiveEvent(
    type: InteractivePreviewEvent.InteractivePreviewEventType,
    consumer: (InteractivePreviewEvent.Builder) -> Unit,
  ) {
    try {
      myExecutor.execute {
        val builder = InteractivePreviewEvent.newBuilder().setType(type)

        consumer(builder)

        val event =
          AndroidStudioEvent.newBuilder()
            .setKind(AndroidStudioEvent.EventKind.INTERACTIVE_PREVIEW_EVENT)
            .setInteractivePreviewEvent(builder.build())

        myEventLogger.accept(event)
      }
    } catch (e: RejectedExecutionException) {
      // We are hitting the throttling limit
      LOG.debug("Failed to report interactive preview metrics", e)
    }
  }
}
