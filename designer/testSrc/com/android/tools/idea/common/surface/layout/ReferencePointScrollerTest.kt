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
import com.android.tools.idea.common.surface.SceneView
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import org.junit.Assert
import org.junit.Test

class ReferencePointScrollerTest {
  @Test
  fun testScrollAfterZoomIn_defaultMove() {
    // Test case: viewport size: (500 x 500). View component size changes from (1000 x 1000) to
    // (2000 x 2000).
    // And the scene view position changes according to the "theoretical" change
    val viewRect = Rectangle(0, 0, 500, 500)
    val oldViewSize = Dimension(1000, 1000)
    val newViewSize = Dimension(2000, 2000)

    val viewComponent = MockitoKt.mock<Component>()
    MockitoKt.whenever(viewComponent.preferredSize).thenReturn(newViewSize)
    val viewport = TestDesignSurfaceViewport(oldViewSize, viewRect, viewComponent = viewComponent)

    val sceneViewMock = MockitoKt.mock<SceneView>()
    val oldSceneViewRectangle = Rectangle(200, 200, 200, 200)
    val newSceneViewRectangle = Rectangle(400, 400, 400, 400)
    val oldMousePosition = Point(300, 300)
    val scroller =
      ReferencePointScroller(
        oldViewSize,
        viewRect.location,
        oldMousePosition,
        oldScale = 1.0,
        newScale = 2.0,
        mapOf(sceneViewMock to oldSceneViewRectangle),
      ) {
        newSceneViewRectangle
      }
    scroller.scroll(viewport)
    Assert.assertEquals(Point(300, 300), viewport.viewPosition)
  }

  @Test
  fun testScrollAfterZoomIn_nullView() {
    // Test case: viewport size: (500 x 500). View component size changes from (1000 x 1000) to
    // (2000 x 2000).
    // And the scene view rectangle is null
    val viewRect = Rectangle(0, 0, 500, 500)
    val oldViewSize = Dimension(1000, 1000)
    val newViewSize = Dimension(2000, 2000)

    val viewComponent = MockitoKt.mock<Component>()
    MockitoKt.whenever(viewComponent.preferredSize).thenReturn(newViewSize)
    val viewport = TestDesignSurfaceViewport(oldViewSize, viewRect, viewComponent = viewComponent)

    val sceneViewMock = MockitoKt.mock<SceneView>()
    val oldMousePosition = Point(300, 300)
    val scroller =
      ReferencePointScroller(
        oldViewSize,
        viewRect.location,
        oldMousePosition,
        oldScale = 1.0,
        newScale = 2.0,
        mapOf(sceneViewMock to null),
      ) {
        null
      }
    scroller.scroll(viewport)
    Assert.assertEquals(Point(300, 300), viewport.viewPosition)
  }

  @Test
  fun testScrollAfterZoomIn_nonDefaultMove() {
    // Test case: viewport size: (500 x 500). View component size changes from (1000 x 1000) to
    // (2000 x 2000).
    // And the scene view position change differs from the "theoretical" change
    val viewRect = Rectangle(0, 0, 500, 500)
    val oldViewSize = Dimension(1000, 1000)
    val newViewSize = Dimension(2000, 2000)

    val viewComponent = MockitoKt.mock<Component>()
    MockitoKt.whenever(viewComponent.preferredSize).thenReturn(newViewSize)
    val viewport = TestDesignSurfaceViewport(oldViewSize, viewRect, viewComponent = viewComponent)

    val sceneViewMock = MockitoKt.mock<SceneView>()
    val oldSceneViewRectangle = Rectangle(200, 200, 200, 200)
    val newSceneViewRectangle = Rectangle(450, 450, 400, 400)
    val oldMousePosition = Point(300, 300)
    val scroller =
      ReferencePointScroller(
        oldViewSize,
        viewRect.location,
        oldMousePosition,
        oldScale = 1.0,
        newScale = 2.0,
        mapOf(sceneViewMock to oldSceneViewRectangle),
      ) {
        newSceneViewRectangle
      }
    scroller.scroll(viewport)
    Assert.assertEquals(Point(350, 350), viewport.viewPosition)
  }

  @Test
  fun testScrollAfterZoomOut_defaultMove() {
    // Test case: viewport size: (500 x 500). View component size changes from (2000 x 2000) to
    // (1000 x 1000).
    // And the scene view position changes according to the "theoretical" change
    val viewRect = Rectangle(300, 300, 500, 500)
    val oldViewSize = Dimension(2000, 2000)
    val newViewSize = Dimension(1000, 1000)

    val viewComponent = MockitoKt.mock<Component>()
    MockitoKt.whenever(viewComponent.preferredSize).thenReturn(newViewSize)
    val viewport = TestDesignSurfaceViewport(oldViewSize, viewRect, viewComponent = viewComponent)

    val sceneViewMock = MockitoKt.mock<SceneView>()
    val oldSceneViewRectangle = Rectangle(400, 400, 400, 400)
    val newSceneViewRectangle = Rectangle(200, 200, 200, 200)
    val oldMousePosition = Point(500, 500)
    val scroller =
      ReferencePointScroller(
        oldViewSize,
        viewRect.location,
        oldMousePosition,
        oldScale = 2.0,
        newScale = 1.0,
        mapOf(sceneViewMock to oldSceneViewRectangle),
      ) {
        newSceneViewRectangle
      }
    scroller.scroll(viewport)
    Assert.assertEquals(Point(50, 50), viewport.viewPosition)
  }

  @Test
  fun testScrollAfterZoomOut_nullView() {
    // Test case: viewport size: (500 x 500). View component size changes from (2000 x 2000) to
    // (1000 x 1000).
    // And the scene view rectangle is null
    val viewRect = Rectangle(300, 300, 500, 500)
    val oldViewSize = Dimension(2000, 2000)
    val newViewSize = Dimension(1000, 1000)

    val viewComponent = MockitoKt.mock<Component>()
    MockitoKt.whenever(viewComponent.preferredSize).thenReturn(newViewSize)
    val viewport = TestDesignSurfaceViewport(oldViewSize, viewRect, viewComponent = viewComponent)

    val sceneViewMock = MockitoKt.mock<SceneView>()
    val oldMousePosition = Point(500, 500)
    val scroller =
      ReferencePointScroller(
        oldViewSize,
        viewRect.location,
        oldMousePosition,
        oldScale = 2.0,
        newScale = 1.0,
        mapOf(sceneViewMock to null),
      ) {
        null
      }
    scroller.scroll(viewport)
    Assert.assertEquals(Point(50, 50), viewport.viewPosition)
  }

  @Test
  fun testScrollAfterZoomOut_nonDefaultMove() {
    // Test case: viewport size: (500 x 500). View component size changes from (2000 x 2000) to
    // (1000 x 1000).
    // And the scene view position change differs from the "theoretical" change
    val viewRect = Rectangle(300, 300, 500, 500)
    val oldViewSize = Dimension(2000, 2000)
    val newViewSize = Dimension(1000, 1000)

    val viewComponent = MockitoKt.mock<Component>()
    MockitoKt.whenever(viewComponent.preferredSize).thenReturn(newViewSize)
    val viewport = TestDesignSurfaceViewport(oldViewSize, viewRect, viewComponent = viewComponent)

    val sceneViewMock = MockitoKt.mock<SceneView>()
    val oldSceneViewRectangle = Rectangle(400, 400, 400, 400)
    val newSceneViewRectangle = Rectangle(250, 250, 200, 200)
    val oldMousePosition = Point(500, 500)
    val scroller =
      ReferencePointScroller(
        oldViewSize,
        viewRect.location,
        oldMousePosition,
        oldScale = 2.0,
        newScale = 1.0,
        mapOf(sceneViewMock to oldSceneViewRectangle),
      ) {
        newSceneViewRectangle
      }
    scroller.scroll(viewport)
    Assert.assertEquals(Point(100, 100), viewport.viewPosition)
  }
}
