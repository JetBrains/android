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
package com.android.tools.idea.compose.preview.analytics

import com.android.tools.idea.common.analytics.DesignerUsageTrackerManager
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.GroupedGridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.GroupedListSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.SingleDirectionLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManager
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
  fun logSwitchLayout(layout: SurfaceLayoutManager)

  companion object {
    private val NOP_TRACKER = PreviewCanvasNopTracker
    private val MANAGER =
      DesignerUsageTrackerManager<PreviewCanvasTracker, Disposable>(
        { executor, _, eventBuilder -> PreviewCanvasTrackerImpl(executor, eventBuilder) },
        NOP_TRACKER
      )

    // This tracker is shared between all compose preview. No key is needed.
    fun getInstance() = MANAGER.getInstance(null)
  }
}

class PreviewCanvasTrackerImpl(
  private val myExecutor: Executor,
  private val myEventLogger: Consumer<AndroidStudioEvent.Builder>
) : PreviewCanvasTracker {
  override fun logSwitchLayout(layout: SurfaceLayoutManager) {
    try {
      val layoutName =
        when (layout) {
          is SingleDirectionLayoutManager -> ComposePreviewCanvasEvent.LayoutName.LIST
          is GridSurfaceLayoutManager -> ComposePreviewCanvasEvent.LayoutName.GRID
          is GroupedListSurfaceLayoutManager -> ComposePreviewCanvasEvent.LayoutName.GROUPED_LIST
          is GroupedGridSurfaceLayoutManager -> ComposePreviewCanvasEvent.LayoutName.GROUPED_GRID
          else -> ComposePreviewCanvasEvent.LayoutName.UNKNOWN_LAYOUT_NAME
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
  override fun logSwitchLayout(layout: SurfaceLayoutManager) = Unit
}
