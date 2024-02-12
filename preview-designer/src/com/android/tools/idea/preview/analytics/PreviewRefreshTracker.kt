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
package com.android.tools.idea.preview.analytics

import com.android.tools.idea.common.analytics.DesignerUsageTrackerManager
import com.android.tools.idea.common.analytics.setApplicationId
import com.android.tools.idea.common.surface.DesignSurface
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.PreviewRefreshEvent
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import org.jetbrains.annotations.TestOnly

/** Tracker implementation for the refresh process of a preview tool. */
interface PreviewRefreshTracker {
  fun logEvent(event: PreviewRefreshEvent): AndroidStudioEvent.Builder

  companion object {
    private val MANAGER =
      DesignerUsageTrackerManager(::PreviewRefreshDefaultTracker, PreviewRefreshNopTracker)

    fun getInstance(surface: DesignSurface<*>?) = MANAGER.getInstance(surface)

    /** Sets the corresponding usage tracker for a [DesignSurface] in tests. */
    @TestOnly
    fun setInstanceForTest(surface: DesignSurface<*>, instance: PreviewRefreshTracker) {
      MANAGER.setInstanceForTest(surface, instance)
    }

    /** Clears the cached instances to clean state in tests. */
    @TestOnly
    fun cleanAfterTesting(surface: DesignSurface<*>) {
      MANAGER.cleanAfterTesting(surface)
    }
  }
}

/** Creates and returns an [AndroidStudioEvent.Builder] from a [PreviewRefreshEvent]. */
private fun PreviewRefreshEvent.createAndroidStudioEvent(): AndroidStudioEvent.Builder {
  return AndroidStudioEvent.newBuilder()
    .setKind(AndroidStudioEvent.EventKind.PREVIEW_REFRESH_EVENT)
    .setPreviewRefreshEvent(this)
}

/** Empty [PreviewRefreshTracker] implementation, used when the user is not opt-in or in tests. */
private val PreviewRefreshNopTracker =
  object : PreviewRefreshTracker {
    override fun logEvent(event: PreviewRefreshEvent): AndroidStudioEvent.Builder {
      return event.createAndroidStudioEvent()
    }
  }

@TestOnly
class PreviewRefreshTrackerForTest(private val onLogEvent: (PreviewRefreshEvent) -> Unit) :
  PreviewRefreshTracker {
  override fun logEvent(event: PreviewRefreshEvent): AndroidStudioEvent.Builder {
    onLogEvent(event)
    return event.createAndroidStudioEvent()
  }
}

/**
 * Default [PreviewRefreshTracker] implementation that sends the event to the analytics backend
 * through the [studioEventTracker].
 */
private class PreviewRefreshDefaultTracker(
  private val executor: Executor,
  private val surface: DesignSurface<*>?,
  private val studioEventTracker: Consumer<AndroidStudioEvent.Builder>,
) : PreviewRefreshTracker {
  override fun logEvent(event: PreviewRefreshEvent): AndroidStudioEvent.Builder {
    event.createAndroidStudioEvent().setApplicationId(surface).let {
      executor.execute { studioEventTracker.accept(it) }
      return it
    }
  }
}

class PreviewRefreshEventBuilder(
  type: PreviewRefreshEvent.PreviewType,
  private val tracker: PreviewRefreshTracker,
  private val logger: Logger = Logger.getInstance(PreviewRefreshEventBuilder::class.java),
) {
  private val eventBuilder = PreviewRefreshEvent.newBuilder().setType(type)
  private var enqueueMs: Long? = null
  private var startMs: Long? = null
  private val tracked = AtomicBoolean(false)

  fun withPreviewsToRefresh(count: Int) {
    eventBuilder.setPreviewsToRefresh(count)
  }

  fun withPreviewsCount(count: Int) {
    eventBuilder.setPreviewsCount(count)
  }

  fun addPreviewRenderDetails(
    renderError: Boolean,
    inflate: Boolean,
    renderQuality: Float,
    renderTimeMillis: Long,
  ) {
    if (renderQuality < 0f || 1f < renderQuality) {
      logger.warn("Attempted to log a render with not valid quality: $renderQuality")
    }
    eventBuilder.addPreviewRenders(
      PreviewRefreshEvent.SinglePreviewRender.newBuilder()
        .setResult(
          if (renderError) PreviewRefreshEvent.SinglePreviewRender.RenderResult.ERROR
          else PreviewRefreshEvent.SinglePreviewRender.RenderResult.SUCCESS
        )
        .setInflate(inflate)
        .setRenderQuality(renderQuality.coerceIn(0f, 1f))
        .setRenderTimeMillis(renderTimeMillis.toInt())
    )
  }

  /** To indicate that the request entered the refresh queue */
  fun onRequestEnqueued() {
    enqueueMs = System.currentTimeMillis()
  }

  /**
   * To indicate that the request is skipped, possibly without even being enqueued.
   *
   * This method is terminal, meaning that calling any method after this one won't have any effect
   * in the tracked data.
   */
  fun onRequestSkipped() {
    eventBuilder.setInQueueTimeMillis(
      (enqueueMs?.let { System.currentTimeMillis() - it } ?: 0).toInt()
    )
    eventBuilder.setResult(PreviewRefreshEvent.RefreshResult.SKIPPED)
    track()
  }

  /** To indicate that the request is taken out of the refresh queue and started to be processed. */
  fun onRefreshStarted() {
    startMs = System.currentTimeMillis()
    if (enqueueMs == null) logger.warn("Expected not null enqueueMs")
    else eventBuilder.setInQueueTimeMillis((startMs!! - enqueueMs!!).toInt())
  }

  /**
   * To indicate that the request finished executing with the given [result].
   *
   * This method is terminal, meaning that calling any method after this one won't have any effect
   * in the tracked data.
   */
  fun onRefreshCompleted(result: PreviewRefreshEvent.RefreshResult) {
    if (startMs == null) logger.warn("Expected not null startMs")
    else eventBuilder.setRefreshTimeMillis((System.currentTimeMillis() - startMs!!).toInt())
    eventBuilder.setResult(result)
    track()
  }

  private fun track() {
    if (!tracked.getAndSet(true)) tracker.logEvent(eventBuilder.build())
    else {
      logger.warn("Attempted to log the same refresh event more than once")
    }
  }
}
