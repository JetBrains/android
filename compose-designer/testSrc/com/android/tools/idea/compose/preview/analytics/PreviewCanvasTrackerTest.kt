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
package com.android.tools.idea.compose.preview.analytics

import com.android.tools.idea.uibuilder.surface.layout.GroupedGridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.GroupedListSurfaceLayoutManager
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ComposePreviewCanvasEvent
import java.util.LinkedList
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewCanvasTrackerTest {
  private val trackedEvents = LinkedList<AndroidStudioEvent>()
  private val previewCanvasTracker: PreviewCanvasTracker =
    PreviewCanvasTrackerImpl({ command -> command.run() }) { event: AndroidStudioEvent.Builder ->
      trackedEvents.add(event.build())
    }

  @Test
  fun testSetLayout() {
    val groupedGridLayout =
      GroupedGridSurfaceLayoutManager(0, { 0 }) { content -> listOf(content.toList()) }
    previewCanvasTracker.logSwitchLayout(groupedGridLayout)
    val groupedListLayout =
      GroupedListSurfaceLayoutManager(0, { 0 }) { content -> listOf(content.toList()) }
    previewCanvasTracker.logSwitchLayout(groupedListLayout)

    assertEquals(2, trackedEvents.size)
    trackedEvents.poll().let { event ->
      assertEquals(event.kind, AndroidStudioEvent.EventKind.COMPOSE_PREVIEW_CANVAS_EVENT)
      val canvasEvent = event.composePreviewCanvasEvent
      assertEquals(canvasEvent.eventType, ComposePreviewCanvasEvent.EventType.SELECT_LAYOUT)
      assertEquals(canvasEvent.layoutName, ComposePreviewCanvasEvent.LayoutName.GROUPED_GRID)
    }
    trackedEvents.poll().let { event ->
      assertEquals(event.kind, AndroidStudioEvent.EventKind.COMPOSE_PREVIEW_CANVAS_EVENT)
      val canvasEvent = event.composePreviewCanvasEvent
      assertEquals(canvasEvent.eventType, ComposePreviewCanvasEvent.EventType.SELECT_LAYOUT)
      assertEquals(canvasEvent.layoutName, ComposePreviewCanvasEvent.LayoutName.GROUPED_LIST)
    }
  }
}
