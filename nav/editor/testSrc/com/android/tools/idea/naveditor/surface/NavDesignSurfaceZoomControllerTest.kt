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
package com.android.tools.idea.naveditor.surface

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.testing.mockStatic
import com.intellij.testFramework.DisposableRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import java.awt.Dimension

class NavDesignSurfaceZoomControllerTest {

  @Test
  fun `test fit scale when no views are within the focus`() {
    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)

    // We have no content to show in the NavDesignSurface, the focusedSceneView is then null.
    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = null,
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { Dimension() })

    assertEquals(1.0, zoomController.getFitScale(), 0.01)
    assertFalse(zoomController.canZoomToFit())
  }

  @Test
  fun `test surface have the same fit scale value`() {
    // The content we want to show and the size of [NavDesignSurface] are the same.
    val contentDimension = Dimension(100, 500)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)

    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { contentDimension })

    assertEquals(1.0, zoomController.getFitScale(), 0.01)
  }

  @Test
  fun `test fit scale when surface is wider than the viewport`() {
    // The content we want to show is wider than the size of [NavDesignSurface].
    val contentDimension = Dimension(200, 500)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)

    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { contentDimension })

    assertEquals(0.5, zoomController.getFitScale(), 0.01)
  }

  @Test
  fun `test fit scale when surface is higher than the viewport`() {
    // The content we want to show is higher than the size of [NavDesignSurface].
    val contentDimension = Dimension(100, 1000)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)
    val navDesignSurfaceMock = mockNavDesignSurface(
      mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { contentDimension })

    assertEquals(0.5, zoomController.getFitScale(), 0.01)
  }

  @Test
  fun `test fit scale when surface is even higher than the viewport`() {
    // The content we want to show is a lot higher than the size of [NavDesignSurface].
    val contentDimension = Dimension(100, 100000)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)
    val navDesignSurfaceMock = mockNavDesignSurface(
      mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { contentDimension })

    assertEquals(0.005, zoomController.getFitScale(), 0.01)
  }

  @Test
  fun `test fit scale when surface is higher and wider than the viewport`() {

    // The content that we want to show into the NavDesignSurface is both
    // wider and higher than [NavDesignSurface].
    val contentDimension = Dimension(200, 1000)

    val surfaceDimension = Dimension(100, 500)
    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { contentDimension })

    assertEquals(0.5, zoomController.getFitScale(), 0.01)
  }

  @Test
  fun `change scale`() {
    // The content we want to show and the size of [NavDesignSurface] are the same.
    val contentDimension = Dimension(100, 500)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)

    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { contentDimension })
    zoomController.setScale(1.0)

    assertTrue(zoomController.setScale(0.5))
    assertEquals(0.5, zoomController.scale, 0.0)
    assertTrue(zoomController.setScale(3.0))
    assertEquals(3.0, zoomController.scale, 0.0)

    // We can't change the scale less than the minimum scale
    assertTrue(zoomController.setScale(-10.0))
    assertEquals(zoomController.minScale, zoomController.scale, 0.0)

    // We can't change the scale more than the maximum scale
    assertTrue(zoomController.setScale(20.0))
    assertEquals(zoomController.maxScale, zoomController.scale, 0.0)

    zoomController.setScale(2.0)
    // We can't change the scale if scale is the same
    assertFalse(zoomController.setScale(2.0))

    // We can't change the scale if the scale is similar into a tolerance value
    assertFalse(zoomController.setScale(2.005))
    assertFalse(zoomController.setScale(1.995))
    assertEquals(2.0, zoomController.scale, 0.0)
  }

  @Test
  fun `can change scale with coordinates`() {
    // The content we want to show and the size of [NavDesignSurface] are the same.
    val contentDimension = Dimension(100, 500)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)

    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { contentDimension })
    zoomController.setScale(1.0)

    assertTrue(zoomController.setScale(0.5, 2, 4))
    assertEquals(zoomController.scale, 0.5, 0.0)
    assertTrue(zoomController.setScale(3.0, 2, 1))
    assertEquals(3.0, zoomController.scale, 0.0)

    // We can't change the scale less than the minimum scale
    assertTrue(zoomController.setScale(-10.0, 3, 5))
    assertEquals(zoomController.minScale, zoomController.scale, 0.0)

    // We can't change the scale more than the maximum scale
    assertTrue(zoomController.setScale(20.0, 4, 2))
    assertEquals(zoomController.maxScale, zoomController.scale, 0.0)

    zoomController.setScale(2.0)
    // We can't change the scale if scale is the same
    assertFalse(zoomController.setScale(2.0, 1, 1))

    // We can't change the scale if the scale is similar into a tolerance value
    assertFalse(zoomController.setScale(2.005, 5, 3))
    assertFalse(zoomController.setScale(1.995, 2, 4))
    assertEquals(2.0, zoomController.scale, 0.0)
  }

  @Test
  fun `test zoom in`() {
    // The content we want to show and the size of [NavDesignSurface] are the same.
    val contentDimension = Dimension(100, 500)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)

    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { contentDimension })
    zoomController.setScale(1.0)
    do {
      assertTrue(zoomController.zoom(ZoomType.IN, 3, 3))
    }
    while (zoomController.canZoomIn())

    assertEquals(zoomController.maxScale, zoomController.scale, 0.0)
  }

  @Test
  fun `test zoom out`() {
    // The content we want to show and the size of [NavDesignSurface] are the same.
    val contentDimension = Dimension(100, 500)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)

    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { contentDimension })
    zoomController.setScale(10.0)
    do {
      assertTrue(zoomController.zoom(ZoomType.OUT, 2, 3))
    }
    while (zoomController.canZoomOut())

    assertEquals(zoomController.minScale, zoomController.scale, 0.0)
  }

  @Test
  fun `test zoom in and zoom out`() {
    // The content we want to show and the size of [NavDesignSurface] are the same.
    val contentDimension = Dimension(100, 500)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)

    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { contentDimension })
    val initialScale = 1.0
    zoomController.setScale(initialScale)

    // We can zoom in until we reach the minScale
    assertTrue(zoomController.canZoomOut())
    assertTrue(zoomController.zoom(ZoomType.OUT))
    assertTrue(zoomController.canZoomOut())
    assertTrue(zoomController.zoom(ZoomType.OUT))

    // We should now be able to zoom in
    assertTrue(zoomController.canZoomIn())
    assertTrue(zoomController.zoom(ZoomType.IN))
    assertTrue(zoomController.canZoomIn())
    assertTrue(zoomController.zoom(ZoomType.IN))

    // And we should still be able to zoom in and out
    assertTrue(zoomController.canZoomOut())
    assertTrue(zoomController.canZoomIn())

    // Zoom in and out we reach out the same value
    assertEquals(initialScale, zoomController.scale, 0.0)
  }

  @Test
  fun `can zoom to the actual sizes`() {
    // The content we want to show and the size of [NavDesignSurface] are the same.
    val contentDimension = Dimension(100, 500)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)

    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { contentDimension })
    repeat(5) { zoomController.zoom(ZoomType.IN) }

    val zoomInScale = zoomController.scale
    assertTrue(zoomController.canZoomToActual())
    assertTrue(zoomController.zoom(ZoomType.ACTUAL))

    assertTrue(zoomController.scale < zoomInScale)

    // We can't apply zoom to actual as we are already in the zoom to actual scale
    assertFalse(zoomController.canZoomToActual())
  }

  @Test
  fun `test can zoom to fit`() {
    // The content we want to show and the size of [NavDesignSurface] are the same.
    val contentDimension = Dimension(100, 500)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)

    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { contentDimension })

    repeat(4) { zoomController.zoom(ZoomType.OUT) }

    assertTrue(zoomController.canZoomToFit())
    assertTrue(zoomController.zoom(ZoomType.FIT))

    assertEquals(zoomController.scale, zoomController.getFitScale(), 0.0)

    // We can't apply zoom to fit as we are already in the zoom to actual scale.
    assertFalse(zoomController.canZoomToActual())

    // We now zoom in
    repeat(3) { zoomController.zoom(ZoomType.IN) }

    // We can apply zoom to fit again.
    assertTrue(zoomController.canZoomToFit())
    assertTrue(zoomController.zoom(ZoomType.FIT))
    assertFalse(zoomController.canZoomToFit())
  }

  @Test
  fun `can zoom to fit`() {
    // The content we want to show and the size of [NavDesignSurface] are the same.
    val contentDimension = Dimension(100, 500)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)

    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { contentDimension })

    repeat(5) { zoomController.zoom(ZoomType.IN) }

    val zoomInScale = zoomController.scale
    assertTrue(zoomController.canZoomToFit())
    assertTrue(zoomController.zoomToFit())

    assertTrue(zoomController.scale < zoomInScale)

    // We can't zoom to fit as we are already in the zoom to fit scale
    assertFalse(zoomController.canZoomToFit())
  }

  @Test
  fun `can track zoom`() {
    var zoomTypeToTrack: ZoomType? = null

    // The content we want to show and the size of [NavDesignSurface] are the same.
    val contentDimension = Dimension(100, 500)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)

    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock, { contentDimension }, { zoomTypeToTrack = it })

    zoomController.zoom(ZoomType.IN)
    assertEquals(zoomTypeToTrack, ZoomType.IN)

    zoomController.zoom(ZoomType.OUT)
    assertEquals(zoomTypeToTrack, ZoomType.OUT)

    zoomController.zoom(ZoomType.FIT)
    assertEquals(zoomTypeToTrack, ZoomType.FIT)

    zoomController.zoom(ZoomType.ACTUAL)
    assertEquals(zoomTypeToTrack, ZoomType.ACTUAL)
  }

  companion object {

    @JvmField @ClassRule
    val disposableRule = DisposableRule()

    private lateinit var coordinates: MockedStatic<Coordinates>

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      coordinates = mockStatic(disposableRule.disposable)
      coordinates.whenever<Any> {
        Coordinates.getAndroidXDip(any(), anyInt())
      }.thenReturn(0)

      coordinates.whenever<Any> {
        Coordinates.getAndroidYDip(any(), anyInt())
      }.thenReturn(0)

      coordinates.whenever<Any> {
        Coordinates.getSwingXDip(any(), anyInt())
      }.thenReturn(0)

      coordinates.whenever<Any> {
        Coordinates.getSwingYDip(any(), anyInt())
      }.thenReturn(0)
    }
  }
}
