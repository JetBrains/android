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
package com.android.tools.adtui

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.surface.SurfaceScale
import java.awt.Dimension

/** Applies zoom changes to a [Zoomable] surface. */
interface ZoomController : Zoomable {

  /**
   * Id used when saving and restoring scale values associated with a ZoomController instance.
   * Particularly needed when multiple surfaces come from the same file.
   */
  var storeId: String?

  /** The minimum scale allowed. */
  val minScale: Double

  /** The maximum scale allowed. */
  val maxScale: Double

  /**
   * Sets the scale factor used to multiply content size.
   *
   * @param scale The scale factor. Can be any value, but it will be capped between -1 and 10 (value
   *   below 0 means zoom to fit).
   * @param x the horizontal coordinate to where to focus the scaling.
   * @param y the vertical coordinate to where to focus the scaling.
   * @return True if the scaling was changed, false otherwise.
   */
  fun setScale(
    @SurfaceScale scale: Double,
    @SwingCoordinate x: Int,
    @SwingCoordinate y: Int,
  ): Boolean

  /**
   * Sets the scale factor used to multiply the content size.
   *
   * @param scale The scale factor. Can be any value, but it will be capped between -1 and 10 (value
   *   below 0 means zoom to fit)
   * @return True if the scaling was changed, false if this was a noop.
   */
  fun setScale(@SurfaceScale scale: Double): Boolean {
    return setScale(scale, -1, -1)
  }

  /** Get [scale] bounded by [minScale] and [maxScale]. */
  @SurfaceScale
  fun getBoundedScale(@SurfaceScale scale: Double): Double {
    return scale.coerceIn(minScale, maxScale)
  }

  /**
   * Call this method whenever a surface changes its size or gets recreated. Notifies the
   * [ZoomController] that the surface has changed its size. This method is used to keep consistent
   * the calculation of the [zoomToFit] which requires an updated size.
   *
   * @param shouldWaitForResize If true, the zoom to fit will be applied after the surface has been
   *   resized.
   * @param surfaceSize The current size of the surface.
   */
  fun resetZoomToFitSettings(shouldWaitForResize: Boolean = true, surfaceSize: Dimension)

  /**
   * Applies zoom-to-fit safely by synchronizing with its surface to prevent race conditions.
   *
   * This function avoids calculating an incorrect scale by ensuring the content and surface sizes
   * are resolved before applying the zoom. Note that [resetZoomToFitSettings] must be called when
   * the surface is resized or re-created.
   *
   * @return True if the zoom-to-fit was successfully applied, false otherwise.
   */
  fun zoomToFit(): Boolean

  /**
   * The scale to make the content fit the design surface.
   *
   * This value is the result of the measure of the scale size which can fit the SceneViews into the
   * scrollable area. It doesn't consider the legal scale range, which can be got by max scale and
   * min scale.
   */
  @SurfaceScale fun getFitScale(): Double
}
