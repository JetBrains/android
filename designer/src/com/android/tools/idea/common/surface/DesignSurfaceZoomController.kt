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

import androidx.annotation.VisibleForTesting
import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.ZoomController
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.analytics.DesignerAnalyticsManager
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.scene.Scene
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * If the difference between old and new scaling values is less than threshold, the scaling will be
 * ignored.
 */
@SurfaceZoomLevel const val SCALING_THRESHOLD = 0.005

/**
 * Implementation of [ZoomController] for [DesignSurface] zoom logic.
 *
 * It is responsible for zooming interaction of type [ZoomType] and to change scale. This class can
 * be changed if we want to change zooming behaviours in [DesignSurface] and its implementations,
 * this means that changing zooming interaction in this class will also affect [NlDesignSurface] as
 * well as [NavDesignSurface].
 *
 * FIXME(b/291572358): this will replace the zoom logic within [DesignSurface]
 */
class DesignSurfaceZoomController(
  /** Analytics tracker responsible to track the zoom changes. */
  val designerAnalyticsManager: DesignerAnalyticsManager?,
  /** The collection of [NlComponent]s of [DesignSurface]. */
  val selectionModel: SelectionModel?,
  /** Returns the current [SceneView] that owns the focus. */
  val getFocusedSceneView: () -> SceneView?,
) : ZoomController {

  /**
   * The current scale of [DesignSurface]. This variable should be only changed by [setScale]. If
   * you want to get the scale you can use [scale].
   */
  private var currentScale: Double = 1.0

  /** A listener that calls a callback whenever zoom changes. */
  private var zoomListener: ZoomListener? = null

  /** Returns the current scale of [DesignSurface]. */
  @SurfaceScale
  override val scale: Double
    get() = currentScale

  @SurfaceScreenScalingFactor override var screenScalingFactor: Double = 1.0

  /**
   * The scale to make the content fit the design surface.
   *
   * This value is the result of the measure of the scale size which can fit the SceneViews into the
   * scrollable area. It doesn't consider the legal scale range, which can be get by
   * {@link #getMaxScale()} and {@link #getMinScale()}.
   */
  @SurfaceScale
  private val fitScale: Double
    get() = 1.0

  override fun setScale(scale: Double): Boolean {
    return setScale(scale, -1, -1)
  }

  /**
   * <p>
   * Set the scale factor used to multiply the content size and try to position the viewport such
   * that its center is the closest possible to the provided x and y coordinate in the Viewport's
   * view coordinate system ({@link JViewport#getView()}). </p><p> If x OR y are negative, the scale
   * will be centered toward the center the viewport. </p>
   *
   * @param scale The scale factor. Can be any value but it will be capped between -1 and 10 (value
   *   below 0 means zoom to fit) This value doesn't consider DPI.
   * @param x The X coordinate to center the scale to (in the Viewport's view coordinate system)
   * @param y The Y coordinate to center the scale to (in the Viewport's view coordinate system)
   * @return True if the scaling was changed, false if this was a noop.
   */
  override fun setScale(
    @SurfaceScale scale: Double,
    @SwingCoordinate x: Int,
    @SwingCoordinate y: Int,
  ): Boolean {
    @SurfaceScale val newScale: Double = getBoundedScale(scale)
    if (isScaleSame(this.scale, newScale)) {
      return false
    }
    val previewsScale = this.scale
    this.currentScale = newScale
    zoomListener?.setOnScaleChangeListener(previewsScale, this.scale)
    return true
  }

  private fun getBoundedScale(scale: Double): Double = min(max(scale, MIN_SCALE), MAX_SCALE)

  @UiThread override fun zoomToFit(): Boolean = zoom(ZoomType.FIT, -1, -1)

  @UiThread override fun zoom(type: ZoomType): Boolean = zoom(type, -1, -1)

  @UiThread
  override fun zoom(type: ZoomType, @SwingCoordinate x: Int, @SwingCoordinate y: Int): Boolean {
    var newX = x
    var newY = y
    // track user triggered change
    designerAnalyticsManager?.trackZoom(type)
    val view = getFocusedSceneView()
    if (
      type == ZoomType.IN &&
        (newX < 0 || newY < 0) &&
        view != null &&
        selectionModel?.isEmpty == false
    ) {
      val scene: Scene = view.scene
      val component = scene.getSceneComponent(selectionModel.primary)
      if (component != null) {
        newX = Coordinates.getSwingXDip(view, component.centerX)
        newY = Coordinates.getSwingYDip(view, component.centerY)
      }
    }
    val scaled: Boolean =
      when (type) {
        ZoomType.IN -> {
          @SurfaceZoomLevel val currentScale: Double = scale * screenScalingFactor
          val current = Math.round(currentScale * 100).toInt()
          @SurfaceScale val scale: Double = (ZoomType.zoomIn(current) / 100.0) / screenScalingFactor
          setScale(scale, newX, newY)
        }
        ZoomType.OUT -> {
          @SurfaceZoomLevel val currentScale: Double = scale * screenScalingFactor
          val current = (currentScale * 100).toInt()
          @SurfaceScale
          val scale: Double = (ZoomType.zoomOut(current) / 100.0) / screenScalingFactor
          setScale(scale, newX, newY)
        }
        ZoomType.ACTUAL -> setScale(1.0 / screenScalingFactor)
        ZoomType.FIT -> setScale(fitScale)
        else -> throw UnsupportedOperationException("Not yet implemented: $type")
      }

    return scaled
  }

  override fun canZoomIn(): Boolean = scale < MAX_SCALE && !isScaleSame(scale, MAX_SCALE)

  override fun canZoomOut(): Boolean = MIN_SCALE < scale && !isScaleSame(MIN_SCALE, scale)

  override fun canZoomToFit(): Boolean =
    (scale > fitScale && canZoomOut()) || (scale < fitScale && canZoomIn())

  override fun canZoomToActual(): Boolean =
    (scale > 1 && canZoomOut()) || (scale < 1 && canZoomIn())

  /** Sets a [ZoomListener] used by [DesignSurface] to interact with zoom changes. */
  fun setOnScaleChangeListener(listener: ZoomListener) {
    zoomListener = listener
  }

  /**
   * If the differences of two scales are smaller than tolerance, they are considered as the same
   * scale.
   */
  private fun isScaleSame(@SurfaceScale scaleA: Double, @SurfaceScale scaleB: Double): Boolean {
    val tolerance: Double = SCALING_THRESHOLD / screenScalingFactor
    return abs(scaleA - scaleB) < tolerance
  }

  companion object {
    /** The minimum scale we'll allow. */
    @VisibleForTesting @SurfaceScale const val MIN_SCALE: Double = 0.0

    /** The maximum scale we'll allow. */
    @VisibleForTesting @SurfaceScale const val MAX_SCALE: Double = 10.0
  }
}
