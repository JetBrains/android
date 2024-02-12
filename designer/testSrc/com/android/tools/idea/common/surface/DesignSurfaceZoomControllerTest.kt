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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.common.analytics.DesignerAnalyticsManager
import com.android.tools.idea.common.surface.DesignSurfaceZoomController.Companion.MAX_SCALE
import com.android.tools.idea.common.surface.DesignSurfaceZoomController.Companion.MIN_SCALE
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DesignSurfaceZoomControllerTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `change scale`() {
    val zoomController = createDesignSurfaceZoomController()
    zoomController.setScale(1.0)

    assertTrue(zoomController.setScale(0.5))
    assertEquals(0.5, zoomController.scale, 0.0)
    assertTrue(zoomController.setScale(3.0))
    assertEquals(3.0, zoomController.scale, 0.0)

    // We can't change the scale less than the minimum scale
    assertTrue(zoomController.setScale(-10.0))
    assertEquals(MIN_SCALE, zoomController.scale, 0.0)

    // We can't change the scale more than the maximum scale
    assertTrue(zoomController.setScale(20.0))
    assertEquals(MAX_SCALE, zoomController.scale, 0.0)

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
    val zoomController = createDesignSurfaceZoomController()
    zoomController.setScale(1.0)

    assertTrue(zoomController.setScale(0.5, 2, 4))
    assertTrue(zoomController.scale == 0.5)
    assertTrue(zoomController.setScale(3.0, 2, 1))
    assertEquals(3.0, zoomController.scale, 0.0)

    // We can't change the scale less than the minimum scale
    assertTrue(zoomController.setScale(-10.0, 3, 5))
    assertEquals(MIN_SCALE, zoomController.scale, 0.0)

    // We can't change the scale more than the maximum scale
    assertTrue(zoomController.setScale(20.0, 4, 2))
    assertEquals(MAX_SCALE, zoomController.scale, 0.0)

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
    val zoomController = createDesignSurfaceZoomController()

    zoomController.setScale(1.0)
    do {
      assertTrue(zoomController.zoom(ZoomType.IN, 3, 3))
    } while (zoomController.canZoomIn())

    assertEquals(10.0, zoomController.scale, 0.0)
  }

  @Test
  fun `test zoom out`() {
    val zoomController = createDesignSurfaceZoomController()

    zoomController.setScale(10.0)
    do {
      assertTrue(zoomController.zoom(ZoomType.OUT, 2, 3))
    } while (zoomController.canZoomOut())

    assertEquals(0.0, zoomController.scale, 0.0)
  }

  @Test
  fun `test zoom in and zoom out`() {
    val zoomController = createDesignSurfaceZoomController()

    val initialScale = 9.0
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

    // Zoom in and out we reach out the same value under a delta tolerance of screen scaling factor
    assertEquals(initialScale, zoomController.scale, zoomController.screenScalingFactor)
  }

  @Test
  fun `can zoom to fit`() {
    val zoomController = createDesignSurfaceZoomController()

    repeat((0..4).count()) { zoomController.zoom(ZoomType.IN) }

    val zoomInScale = zoomController.scale
    assertTrue(zoomController.canZoomToFit())
    assertTrue(zoomController.zoomToFit())

    assertTrue(zoomController.scale < zoomInScale)

    // We can't zoom to fit as we are already in the zoom to fit scale
    assertFalse(zoomController.canZoomToFit())
  }

  @Test
  fun `can zoom to the actual sizes`() {
    val zoomController = createDesignSurfaceZoomController()

    repeat((0..4).count()) { zoomController.zoom(ZoomType.IN) }

    val zoomInScale = zoomController.scale
    assertTrue(zoomController.canZoomToActual())
    assertTrue(zoomController.zoom(ZoomType.ACTUAL))

    assertTrue(zoomController.scale < zoomInScale)

    // We can't apply zoom to actual as we are already in the zoom to actual scale
    assertFalse(zoomController.canZoomToActual())
  }

  @Test
  fun `can track zoom`() {
    var zoomTypeToTrack: ZoomType? = null
    val zoomController = createDesignSurfaceZoomController { zoomTypeToTrack = it }

    zoomController.zoom(ZoomType.IN)
    assertEquals(zoomTypeToTrack, ZoomType.IN)

    zoomController.zoom(ZoomType.OUT)
    assertEquals(zoomTypeToTrack, ZoomType.OUT)

    zoomController.zoom(ZoomType.FIT)
    assertEquals(zoomTypeToTrack, ZoomType.FIT)

    zoomController.zoom(ZoomType.ACTUAL)
    assertEquals(zoomTypeToTrack, ZoomType.ACTUAL)
  }

  private fun createDesignSurfaceZoomController(
    trackZoom: ((ZoomType) -> Unit)? = null
  ): DesignSurfaceZoomController {
    val designerAnalyticsManager =
      trackZoom?.let {
        object :
          DesignerAnalyticsManager(
            TestDesignSurface(projectRule.project, projectRule.fixture.projectDisposable)
          ) {
          override fun trackZoom(type: ZoomType) {
            trackZoom(type)
          }
        }
      }
    return object :
      DesignSurfaceZoomController(
        designerAnalyticsManager = designerAnalyticsManager,
        selectionModel = null,
        scenesOwner = null,
      ) {
      override fun getFitScale() = 1.0
    }
  }
}
