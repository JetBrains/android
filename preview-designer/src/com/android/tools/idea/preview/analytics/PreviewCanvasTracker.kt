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
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.common.surface.DesignSurface
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ComposePreviewCanvasEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.function.Consumer

private val LOG: Logger
  get() = Logger.getInstance("PreviewCanvasTracker.kt")

/** Interface for usage tracking in the compose preview canvas event. */
interface PreviewCanvasTracker {
  /** Logs the selection of layout in compose preview. */
  fun logSwitchLayout(layoutType: SurfaceLayoutOption.LayoutType)

  companion object {
    private val NOP_TRACKER = PreviewCanvasNopTracker
    private val MANAGER =
      DesignerUsageTrackerManager<PreviewCanvasTracker, Disposable>(
        { executor, _, eventBuilder -> PreviewCanvasTrackerImpl(executor, eventBuilder) },
        NOP_TRACKER,
      )

    fun getInstance(surface: DesignSurface<*>) = MANAGER.getInstance(surface)
  }
}

class PreviewCanvasTrackerImpl(
  private val myExecutor: Executor,
  private val myEventLogger: Consumer<AndroidStudioEvent.Builder>,
) : PreviewCanvasTracker {
  override fun logSwitchLayout(layoutType: SurfaceLayoutOption.LayoutType) {
    try {
      val layoutName =
        when (layoutType) {
          SurfaceLayoutOption.LayoutType.Gallery -> ComposePreviewCanvasEvent.LayoutName.GALLERY
          SurfaceLayoutOption.LayoutType.SingleDirection ->
            ComposePreviewCanvasEvent.LayoutName.LIST
          SurfaceLayoutOption.LayoutType.OrganizationGrid ->
            ComposePreviewCanvasEvent.LayoutName.ORGANIZATION_GRID
          SurfaceLayoutOption.LayoutType.Default ->
            ComposePreviewCanvasEvent.LayoutName.UNKNOWN_LAYOUT_NAME
        }
      myExecutor.execute {
        val event =
          ComposePreviewCanvasEvent.newBuilder()
            .setEventType(ComposePreviewCanvasEvent.EventType.SELECT_LAYOUT)
            .setLayoutName(layoutName)
            .build()

        val studioEvent =
          AndroidStudioEvent.newBuilder()
            .setKind(AndroidStudioEvent.EventKind.COMPOSE_PREVIEW_CANVAS_EVENT)
            .setComposePreviewCanvasEvent(event)

        myEventLogger.accept(studioEvent)
      }
    } catch (e: RejectedExecutionException) {
      // We are hitting the throttling limit
      LOG.debug("Failed to report compose preview canvas metrics", e)
    }
  }
}

object PreviewCanvasNopTracker : PreviewCanvasTracker {
  override fun logSwitchLayout(layoutType: SurfaceLayoutOption.LayoutType) = Unit
}
