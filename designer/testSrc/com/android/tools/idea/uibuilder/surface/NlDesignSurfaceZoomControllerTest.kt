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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.common.surface.createNlDesignSurfaceZoomController
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Assert
import org.junit.Test

class NlDesignSurfaceZoomControllerTest {

  @Test
  fun `test fit scale when the surface is empty`() {
    // There are no items to show in [NlDesignSurface]
    val zoomController = createNlDesignSurfaceZoomController()

    // Fit scale remains with a value of 1.0
    assertEquals(1.0, zoomController.getFitScale())
    assertEquals(1.0, zoomController.scale)
    assertFalse(zoomController.zoomToFit())
  }

  @Test
  fun `test fit scale when fitScale is changed`() {
    // We assume the [PositionableLayoutManager] calculates 10.0 as a scale value to fit the panels
    val fitScaleProvider = { 10.0 }
    val zoomController = createNlDesignSurfaceZoomController(fitScaleProvider = fitScaleProvider)

    // Expected scale is the one returned by the [PositionableLayoutManager]
    assertEquals(10.0, zoomController.getFitScale())
    assertTrue(zoomController.zoomToFit())
    assertEquals(10.0, zoomController.scale)
    assertFalse(zoomController.zoomToFit())
  }

  @Test
  fun `change scale`() {
    val zoomController = createNlDesignSurfaceZoomController()
    zoomController.setScale(1.0)

    Assert.assertTrue(zoomController.setScale(0.5))
    Assert.assertEquals(0.5, zoomController.scale, 0.0)
    Assert.assertTrue(zoomController.setScale(3.0))
    Assert.assertEquals(3.0, zoomController.scale, 0.0)

    // We can't change the scale less than the minimum scale
    Assert.assertTrue(zoomController.setScale(-10.0))
    Assert.assertEquals(zoomController.minScale, zoomController.scale, 0.0)

    // We can't change the scale more than the maximum scale
    Assert.assertTrue(zoomController.setScale(20.0))
    Assert.assertEquals(zoomController.maxScale, zoomController.scale, 0.0)

    zoomController.setScale(2.0)
    // We can't change the scale if scale is the same
    Assert.assertFalse(zoomController.setScale(2.0))

    // We can't change the scale if the scale is similar into a tolerance value
    Assert.assertFalse(zoomController.setScale(2.005))
    Assert.assertFalse(zoomController.setScale(1.995))
    Assert.assertEquals(2.0, zoomController.scale, 0.0)
  }

  @Test
  fun `can change scale with coordinates`() {
    val zoomController = createNlDesignSurfaceZoomController()
    zoomController.setScale(1.0)

    Assert.assertTrue(zoomController.setScale(0.5, 2, 4))
    Assert.assertTrue(zoomController.scale == 0.5)
    Assert.assertTrue(zoomController.setScale(3.0, 2, 1))
    Assert.assertEquals(3.0, zoomController.scale, 0.0)

    // We can't change the scale less than the minimum scale
    Assert.assertTrue(zoomController.setScale(-10.0, 3, 5))
    Assert.assertEquals(zoomController.minScale, zoomController.scale, 0.0)

    // We can't change the scale more than the maximum scale
    Assert.assertTrue(zoomController.setScale(20.0, 4, 2))
    Assert.assertEquals(zoomController.maxScale, zoomController.scale, 0.0)

    zoomController.setScale(2.0)
    // We can't change the scale if scale is the same
    Assert.assertFalse(zoomController.setScale(2.0, 1, 1))

    // We can't change the scale if the scale is similar into a tolerance value
    Assert.assertFalse(zoomController.setScale(2.005, 5, 3))
    Assert.assertFalse(zoomController.setScale(1.995, 2, 4))
    Assert.assertEquals(2.0, zoomController.scale, 0.0)
  }

  @Test
  fun `test zoom in`() {
    val zoomController = createNlDesignSurfaceZoomController()

    zoomController.setScale(1.0)
    do {
      Assert.assertTrue(zoomController.zoom(ZoomType.IN, 3, 3))
    } while (zoomController.canZoomIn())

    Assert.assertEquals(10.0, zoomController.scale, 0.0)
  }

  @Test
  fun `test zoom out`() {
    val zoomController = createNlDesignSurfaceZoomController()

    zoomController.setScale(10.0)
    do {
      Assert.assertTrue(zoomController.zoom(ZoomType.OUT, 2, 3))
    } while (zoomController.canZoomOut())

    // Min zoom level is
    Assert.assertEquals(0.03, zoomController.scale, 0.0)
  }

  @Test
  fun `test zoom in and zoom out`() {
    val zoomController = createNlDesignSurfaceZoomController()

    val initialScale = 9.0
    zoomController.setScale(initialScale)

    // We can zoom in until we reach the minScale
    Assert.assertTrue(zoomController.canZoomOut())
    Assert.assertTrue(zoomController.zoom(ZoomType.OUT))
    Assert.assertTrue(zoomController.canZoomOut())
    Assert.assertTrue(zoomController.zoom(ZoomType.OUT))

    // We should now be able to zoom in
    Assert.assertTrue(zoomController.canZoomIn())
    Assert.assertTrue(zoomController.zoom(ZoomType.IN))
    Assert.assertTrue(zoomController.canZoomIn())
    Assert.assertTrue(zoomController.zoom(ZoomType.IN))

    // And we should still be able to zoom in and out
    Assert.assertTrue(zoomController.canZoomOut())
    Assert.assertTrue(zoomController.canZoomIn())

    // Zoom in and out we reach out the same value under a delta tolerance of screen scaling factor
    Assert.assertEquals(initialScale, zoomController.scale, zoomController.screenScalingFactor)
  }

  @Test
  fun `can zoom to fit`() {
    val zoomController = createNlDesignSurfaceZoomController()

    repeat(5) { zoomController.zoom(ZoomType.IN) }

    val zoomInScale = zoomController.scale
    Assert.assertTrue(zoomController.canZoomToFit())
    Assert.assertTrue(zoomController.zoomToFit())

    Assert.assertTrue(zoomController.scale < zoomInScale)

    // We can't zoom to fit as we are already in the zoom to fit scale
    Assert.assertFalse(zoomController.canZoomToFit())
  }

  @Test
  fun `can zoom to the actual sizes`() {
    val zoomController = createNlDesignSurfaceZoomController()

    repeat(5) { zoomController.zoom(ZoomType.IN) }

    val zoomInScale = zoomController.scale
    Assert.assertTrue(zoomController.canZoomToActual())
    Assert.assertTrue(zoomController.zoom(ZoomType.ACTUAL))

    Assert.assertTrue(zoomController.scale < zoomInScale)

    // We can't apply zoom to actual as we are already in the zoom to actual scale
    Assert.assertFalse(zoomController.canZoomToActual())
  }

  @Test
  fun `test can zoom to fit`() {
    val zoomController = createNlDesignSurfaceZoomController()

    repeat(4) { zoomController.zoom(ZoomType.OUT) }

    Assert.assertTrue(zoomController.canZoomToFit())
    Assert.assertTrue(zoomController.zoom(ZoomType.FIT))

    Assert.assertEquals(zoomController.scale, zoomController.getFitScale(), 0.0)

    // We can't apply zoom to fit as we are already in the zoom to fit scale.
    Assert.assertFalse(zoomController.canZoomToFit())

    // We now zoom in
    repeat(3) { zoomController.zoom(ZoomType.IN) }

    // We can apply zoom to fit again.
    Assert.assertTrue(zoomController.canZoomToFit())
    Assert.assertTrue(zoomController.zoom(ZoomType.FIT))
    Assert.assertFalse(zoomController.canZoomToFit())
  }

  @Test
  fun `can track zoom`() {
    var zoomTypeToTrack: ZoomType? = null
    val zoomController = createNlDesignSurfaceZoomController({ zoomTypeToTrack = it })

    zoomController.zoom(ZoomType.IN)
    Assert.assertEquals(zoomTypeToTrack, ZoomType.IN)

    zoomController.zoom(ZoomType.OUT)
    Assert.assertEquals(zoomTypeToTrack, ZoomType.OUT)

    zoomController.zoom(ZoomType.FIT)
    Assert.assertEquals(zoomTypeToTrack, ZoomType.FIT)

    zoomController.zoom(ZoomType.ACTUAL)
    Assert.assertEquals(zoomTypeToTrack, ZoomType.ACTUAL)
  }
}
