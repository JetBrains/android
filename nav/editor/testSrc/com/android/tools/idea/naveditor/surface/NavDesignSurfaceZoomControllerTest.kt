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
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewport
import com.android.tools.idea.naveditor.scene.NavSceneManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
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
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock) { Dimension() }

    assertEquals(1.0, zoomController.getFitScale(), 0.01)
    assertFalse(zoomController.canZoomToFit())
  }

  @Test
  fun `test surface have the same fit scale value`() {
    // The content we want to show and the size of [NavDesignSurface] are the same.
    val contentDimension = Dimension(100,500)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)

    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock){ contentDimension }

    assertEquals(1.0, zoomController.getFitScale(), 0.01)
  }

  @Test
  fun `test fit scale when surface is wider than the viewport`() {
    // The content we want to show is wider than the size of [NavDesignSurface].
    val contentDimension = Dimension(200,500)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)

    val navDesignSurfaceMock = this.mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock){ contentDimension }

    assertEquals(0.5, zoomController.getFitScale(), 0.01)
  }

  @Test
  fun `test fit scale when surface is higher than the viewport`() {
    // The content we want to show is higher than the size of [NavDesignSurface].
    val contentDimension = Dimension(100,1000)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)
    val navDesignSurfaceMock = this.mockNavDesignSurface(
      mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock){ contentDimension }

    assertEquals(0.5, zoomController.getFitScale(), 0.01)
  }

  @Test
  fun `test fit scale when surface is even higher than the viewport`() {
    // The content we want to show is a lot higher than the size of [NavDesignSurface].
    val contentDimension = Dimension(100,100000)

    // The dimension of NavDesignSurface.
    val surfaceDimension = Dimension(100, 500)
    val navDesignSurfaceMock = this.mockNavDesignSurface(
      mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock){ contentDimension }

    assertEquals(0.005, zoomController.getFitScale(), 0.01)
  }

  @Test
  fun `test fit scale when surface is higher and wider than the viewport`() {

    // The content that we want to show into the NavDesignSurface is both
    // wider and higher than [NavDesignSurface].
    val contentDimension = Dimension(200,1000)

    val surfaceDimension = Dimension(100, 500)
    val navDesignSurfaceMock = mockNavDesignSurface(
      focusedSceneView = mock(),
      surfaceSize = surfaceDimension,
    )

    // Create the [ZoomController].
    val zoomController = createNavDesignSurfaceZoomController(navDesignSurfaceMock){ contentDimension }

    assertEquals(0.5, zoomController.getFitScale(), 0.01)
  }

  private fun createNavDesignSurfaceZoomController(navDesignSurface: NavDesignSurface, dimension: (SceneView) -> Dimension): NavDesignSurfaceZoomController {
    return NavDesignSurfaceZoomController(
      navSelectionModel = null,
      viewPort = navDesignSurface.viewport,
      sceneManager = { navDesignSurface.sceneManager },
      sceneViewDimensionProvider = dimension,
      analyticsManager = null,
      scenesOwner = navDesignSurface,
      surfaceSize = navDesignSurface.size
    )
  }

  private fun mockNavDesignSurface(
    focusedSceneView: SceneView?,
    surfaceSize: Dimension = Dimension(),
  ): NavDesignSurface {
    val sceneManager = mock(NavSceneManager::class.java).apply {
      whenever(this.isEmpty).thenReturn(true)
    }

    val viewPort = mock<DesignSurfaceViewport>().apply {
      whenever(this.extentSize).thenReturn(surfaceSize)
    }

    val navDesignSurface = mock<NavDesignSurface>().apply {
      whenever(this.viewport).thenReturn(viewPort)
      whenever(this.size).thenReturn(Dimension(1,1))
      whenever(this.focusedSceneView).thenReturn(focusedSceneView)
      whenever(this.sceneManager).thenReturn(sceneManager)
    }

    return navDesignSurface
  }
}
