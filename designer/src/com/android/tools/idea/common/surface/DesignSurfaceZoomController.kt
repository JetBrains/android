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

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.ZoomController
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.analytics.DesignerAnalyticsManager
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.scene.Scene
import java.awt.Point
import javax.swing.JViewport
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@SurfaceScale private const val MIN_SCALE: Double = 0.0

@SurfaceScale private const val MAX_SCALE: Double = 10.0

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
 * @param designerAnalyticsManager Analytics tracker responsible to track the zoom changes.
 * @param selectionModel The collection of [NlComponent]s of [DesignSurface].
 * @param scenesOwner The owner of this [ZoomController].
 * @param maxZoomToFitLevel The maximum zoom level allowed for ZoomType#FIT.
 */
abstract class DesignSurfaceZoomController(
  private val designerAnalyticsManager: DesignerAnalyticsManager?,
  private val selectionModel: SelectionModel?,
  private val scenesOwner: ScenesOwner?,
  override val maxZoomToFitLevel: Double = Double.MAX_VALUE,
) : ZoomController {

  override val minScale: Double = MIN_SCALE

  override val maxScale: Double = MAX_SCALE

  /**
   * The max zoom level allowed in zoom to fit could not correspond if [screenScalingFactor] is
   * different from 1.0.
   */
  protected val maxZoomToFitScale
    get() = maxZoomToFitLevel / screenScalingFactor

  /**
   * The current scale of [DesignSurface]. This variable should be only changed by [setScale]. If
   * you want to get the scale you can use [scale].
   */
  @SurfaceScale private var currentScale: Double = 1.0

  /** A listener that calls a callback whenever zoom changes. */
  private var myScaleListener: ScaleListener? = null

  /** Returns the current scale of [DesignSurface]. */
  @SurfaceScale
  override val scale: Double
    get() = currentScale

  @SurfaceScreenScalingFactor override var screenScalingFactor: Double = 1.0

  /**
   * Set the scale factor used to multiply the content size and try to position the viewport such
   * that its center is the closest possible to the provided x and y coordinate in the Viewport's
   * view coordinate system [JViewport.getView]. If x OR y are negative, the scale will be centered
   * toward the center the viewport.
   *
   * @param scale The scale factor. Can be any value, but it will be capped between -1 and 10 (value
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
    if (isScaleSame(currentScale, newScale)) {
      return false
    }
    val previewsScale = currentScale
    currentScale = newScale
    myScaleListener?.onScaleChange(ScaleChange(previewsScale, newScale, Point(x, y)))
    return true
  }

  override fun setScale(scale: Double): Boolean = setScale(scale, -1, -1)

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
          @SurfaceZoomLevel val currentScale: Double = currentScale * screenScalingFactor
          val current = Math.round(currentScale * 100).toInt()
          @SurfaceScale
          val newScale: Double = (ZoomType.zoomIn(current) / 100.0) / screenScalingFactor
          setScale(newScale, newX, newY)
        }
        ZoomType.OUT -> {
          @SurfaceZoomLevel val currentScale: Double = currentScale * screenScalingFactor
          val current = (currentScale * 100).toInt()
          @SurfaceScale
          val newScale: Double = (ZoomType.zoomOut(current) / 100.0) / screenScalingFactor
          setScale(newScale, newX, newY)
        }
        ZoomType.ACTUAL -> setScale(1.0 / screenScalingFactor)
        ZoomType.FIT -> setScale(getFitScale())
        else -> throw UnsupportedOperationException("Not yet implemented: $type")
      }

    return scaled
  }

  @UiThread override fun zoomToFit(): Boolean = zoom(ZoomType.FIT, -1, -1)

  @UiThread override fun zoom(type: ZoomType): Boolean = zoom(type, -1, -1)

  override fun canZoomIn(): Boolean = currentScale < maxScale && !isScaleSame(scale, maxScale)

  override fun canZoomOut(): Boolean = minScale < currentScale && !isScaleSame(minScale, scale)

  override fun canZoomToFit(): Boolean {
    @SurfaceScale val zoomToFitScale = getFitScale()
    return (currentScale > zoomToFitScale && canZoomOut()) ||
      (currentScale < zoomToFitScale && canZoomIn())
  }

  override fun canZoomToActual(): Boolean =
    (scale > 1 && canZoomOut()) || (scale < 1 && canZoomIn())

  protected fun getFocusedSceneView(): SceneView? = scenesOwner?.focusedSceneView

  /** Sets a [ScaleListener] used by [DesignSurface] to interact with zoom changes. */
  fun setOnScaleListener(listener: ScaleListener) {
    myScaleListener = listener
  }

  /**
   * If the differences of two scales are smaller than tolerance, they are considered as the same
   * scale.
   */
  private fun isScaleSame(@SurfaceScale scaleA: Double, @SurfaceScale scaleB: Double): Boolean {
    val tolerance: Double = SCALING_THRESHOLD / screenScalingFactor
    return abs(scaleA - scaleB) < tolerance
  }

  private fun getBoundedScale(scale: Double): Double = min(max(scale, minScale), maxScale)
}
