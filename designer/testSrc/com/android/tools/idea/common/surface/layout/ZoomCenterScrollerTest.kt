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
package com.android.tools.idea.common.surface.layout

import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import org.junit.Test
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import kotlin.test.assertEquals

class ZoomCenterScrollerTest {

  @Test
  fun testScrollAfterZoomIn1() {
    // Test case: viewport size: (500 x 500). View component size changes from (1000 x 1000) to (2000 x 2000).
    val viewRect = Rectangle(250, 250, 500, 500)

    val viewportComponent = MockitoKt.mock<Component>()
    whenever(viewportComponent.width).thenReturn(viewRect.width)
    whenever(viewportComponent.height).thenReturn(viewRect.height)
    val viewport = TestDesignSurfaceViewport(Dimension(2000, 2000), viewRect, viewportComponent = viewportComponent)

    val scroller = ZoomCenterScroller(Dimension(1000, 1000), Point(viewRect.location), Point(250, 250))
    scroller.scroll(viewport)
    assertEquals(Point(750, 750), viewport.viewPosition)
  }

  @Test
  fun testScrollAfterZoomIn2() {
    // Test case: viewport size: (500 x 500). View component size changes from (1000 x 1000) to (2000 x 2000).
    val viewRect = Rectangle(250, 250, 500, 500)

    val viewportComponent = MockitoKt.mock<Component>()
    whenever(viewportComponent.width).thenReturn(500)
    whenever(viewportComponent.height).thenReturn(500)
    val viewport = TestDesignSurfaceViewport(Dimension(2000, 2000), viewRect, viewportComponent = viewportComponent)

    val scroller = ZoomCenterScroller(Dimension(1000, 1000), viewRect.location, Point(200, 400))
    scroller.scroll(viewport)
    assertEquals(Point(700, 900), viewport.viewPosition)
  }

  @Test
  fun testScrollAfterZoomOut1() {
    // Test case: viewport size: (500 x 500). View component size changes from (2000 x 2000) to (1000 x 1000).
    val viewRect = Rectangle(500, 500, 500, 500)

    val viewportComponent = MockitoKt.mock<Component>()
    whenever(viewportComponent.width).thenReturn(500)
    whenever(viewportComponent.height).thenReturn(500)
    val viewport = TestDesignSurfaceViewport(Dimension(1000, 1000), viewRect, viewportComponent = viewportComponent)

    val scroller = ZoomCenterScroller(Dimension(2000, 2000), viewRect.location, Point(250, 250))
    scroller.scroll(viewport)
    assertEquals(Point(125, 125), viewport.viewPosition)
  }

  @Test
  fun testScrollAfterZoomOut2() {
    // Test case: viewport size: (500 x 500). View component size changes from (2000 x 2000) to (1000 x 1000).
    val viewRect = Rectangle(500, 500, 500, 500)

    val viewportComponent = MockitoKt.mock<Component>()
    whenever(viewportComponent.width).thenReturn(500)
    whenever(viewportComponent.height).thenReturn(500)
    val viewport = TestDesignSurfaceViewport(Dimension(1000, 1000), viewRect, viewportComponent = viewportComponent)

    val scroller = ZoomCenterScroller(Dimension(2000, 2000), viewRect.location, Point(200, 400))
    scroller.scroll(viewport)
    assertEquals(Point(150, 50), viewport.viewPosition)
  }
}
