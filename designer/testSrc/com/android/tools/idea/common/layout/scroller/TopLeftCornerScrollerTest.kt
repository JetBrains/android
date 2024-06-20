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
package com.android.tools.idea.common.layout.scroller

import com.android.testutils.MockitoKt
import com.android.tools.idea.common.surface.layout.TestDesignSurfaceViewport
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import org.junit.Assert
import org.junit.Test

class TopLeftCornerScrollerTest {
  @Test
  fun testKeepTopEdgeAfterZoomIn1() {
    val viewRect = Rectangle(0, 0, 500, 500)
    val oldViewSize = Dimension(1000, 1000)
    val newViewSize = Dimension(2000, 2000)

    val viewComponent = MockitoKt.mock<Component>()
    MockitoKt.whenever(viewComponent.preferredSize).thenReturn(newViewSize)
    val viewport = TestDesignSurfaceViewport(oldViewSize, viewRect, viewComponent = viewComponent)

    val scroller =
      TopLeftCornerScroller(oldViewSize, viewRect.location, oldScale = 1.0, newScale = 2.0)
    scroller.scroll(viewport)
    Assert.assertEquals(Point(0, 0), viewport.viewPosition)
  }

  @Test
  fun testKeepTopEdgeAfterZoomIn2() {
    val viewRect = Rectangle(250, 250, 500, 500)
    val oldViewSize = Dimension(1000, 1000)
    val newViewSize = Dimension(2000, 2000)

    val viewComponent = MockitoKt.mock<Component>()
    MockitoKt.whenever(viewComponent.preferredSize).thenReturn(newViewSize)
    val viewport = TestDesignSurfaceViewport(oldViewSize, viewRect, viewComponent = viewComponent)

    val scroller =
      TopLeftCornerScroller(oldViewSize, viewRect.location, oldScale = 1.0, newScale = 2.0)
    scroller.scroll(viewport)
    Assert.assertEquals(Point(500, 500), viewport.viewPosition)
  }

  @Test
  fun testKeepTopEdgeAfterZoomOut1() {
    val viewRect = Rectangle(0, 600, 500, 500)
    val oldViewSize = Dimension(2000, 2000)
    val newViewSize = Dimension(1000, 1000)

    val viewComponent = MockitoKt.mock<Component>()
    MockitoKt.whenever(viewComponent.preferredSize).thenReturn(newViewSize)
    val viewport = TestDesignSurfaceViewport(oldViewSize, viewRect, viewComponent = viewComponent)

    val scroller =
      TopLeftCornerScroller(oldViewSize, viewRect.location, oldScale = 2.0, newScale = 1.0)
    scroller.scroll(viewport)
    Assert.assertEquals(Point(0, 300), viewport.viewPosition)
  }

  @Test
  fun testKeepTopEdgeAfterZoomOut2() {
    val viewRect = Rectangle(750, 1000, 500, 500)
    val oldViewSize = Dimension(2000, 2000)
    val newViewSize = Dimension(1000, 1000)

    val viewComponent = MockitoKt.mock<Component>()
    MockitoKt.whenever(viewComponent.preferredSize).thenReturn(newViewSize)
    val viewport = TestDesignSurfaceViewport(oldViewSize, viewRect, viewComponent = viewComponent)

    val scroller =
      TopLeftCornerScroller(oldViewSize, viewRect.location, oldScale = 2.0, newScale = 1.0)
    scroller.scroll(viewport)
    Assert.assertEquals(Point(375, 500), viewport.viewPosition)
  }
}
