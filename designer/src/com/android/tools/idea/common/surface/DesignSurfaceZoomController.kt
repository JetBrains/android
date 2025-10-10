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
import com.android.tools.idea.common.surface.ZoomConstants.DEFAULT_MAX_SCALE
import com.android.tools.idea.common.surface.ZoomConstants.DEFAULT_MIN_SCALE
import com.intellij.openapi.application.EDT
import java.awt.Dimension
import java.awt.Point
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JViewport
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

/**
 * If the difference between old and new scaling values is less than threshold, the scaling will be
 * ignored.
 */
@SurfaceZoomLevel const val SCALING_THRESHOLD = 0.005

/** The max milliseconds duration of the zooming animation. */
private const val ANIMATION_MAX_DURATION_MILLIS = 200

/** The number of scale changes allowed during one zooming animation. */
private const val SCALE_CHANGES_PER_ANIMATION = 50

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
 */
abstract class DesignSurfaceZoomController(
  private val designerAnalyticsManager: DesignerAnalyticsManager?,
  private val selectionModel: SelectionModel?,
  private val scenesOwner: ScenesOwner?,
) : ZoomController {

  override var storeId: String? = null

  /** The minimum scale allowed. */
  override val minScale: Double = DEFAULT_MIN_SCALE

  /** The maximum scale allowed. */
  override val maxScale: Double = DEFAULT_MAX_SCALE

  open val shouldShowZoomAnimation: Boolean = false

  /**
   * The expected bitwise mask value for when we want to apply zoom-to-fit. This mask is used to
   * wait for: rendering, layout creation, layout resize before applying zoom-to-fit.
   */
  private val expectedZoomToFitMask: Int =
    ZoomMaskConstants.NOTIFY_ZOOM_TO_FIT_INT_MASK or
      ZoomMaskConstants.NOTIFY_COMPONENT_RESIZED_INT_MASK or
      ZoomMaskConstants.NOTIFY_LAYOUT_CREATED_INT_MASK

  /**
   * The current scale of [DesignSurface]. This variable should be only changed by [setScale]. If
   * you want to get the scale you can use [scale].
   */
  @SurfaceScale private var currentScale: Double = 1.0

  /** A listener that calls a callback whenever scale changes. */
  private var scaleListener: ScaleListener? = null

  /** Returns the current scale of [DesignSurface]. */
  @SurfaceScale
  override val scale: Double
    get() = currentScale

  @SurfaceScreenScalingFactor override var screenScalingFactor: Double = 1.0

  /**
   * A bitwise mask used by [notifyZoomToFit]. If the "or" operator applied to this mask gets a
   * bitwise values of [ZoomMaskConstants.NOTIFY_ZOOM_TO_FIT_INT_MASK],
   * [ZoomMaskConstants.NOTIFY_COMPONENT_RESIZED_INT_MASK] we can apply zoom-to-fit.
   */
  private val currentZoomToFitMask = AtomicInteger(ZoomMaskConstants.INITIAL_STATE_INT_MASK)

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

    if (shouldShowZoomAnimation) {
      animateScaleChange(newScale) { scaleIncrement, isAnimating ->
        val previousScale = currentScale
        currentScale = scaleIncrement
        scaleListener?.onScaleChange(
          ScaleChange(
            previousScale = previousScale,
            newScale = scaleIncrement,
            focusPoint = Point(x, y),
            isAnimating = isAnimating,
          )
        )
      }
    } else {
      val previewsScale = currentScale
      currentScale = newScale
      scaleListener?.onScaleChange(
        ScaleChange(previousScale = previewsScale, newScale = newScale, focusPoint = Point(x, y))
      )
    }
    return true
  }

  private var currentTimer: Timer? = null

  /**
   * Shows a zooming animation.
   *
   * @param newScale The final scale we want to apply.
   * @param changeScale Applies scale changes to [DesignSurface]
   *
   * TODO(b/331165064): if we want to ship this feature, it is better moving this code in a
   *   different class
   */
  @UiThread
  private fun animateScaleChange(newScale: Double, changeScale: (Double, Boolean) -> Unit) {
    // Stop the previous timer if any
    currentTimer?.stop()

    val previousScale = currentScale

    // The milliseconds interval between zoom change and another
    val intervalDelay = ANIMATION_MAX_DURATION_MILLIS / SCALE_CHANGES_PER_ANIMATION

    // The zoom level that we want to increment within the interval
    val zoomChangePerInterval = max(0.01, abs(newScale - previousScale) / intervalDelay)

    // Function that describes the zoom animation.
    // It is a function similar to a Bezier curve, it calculates the scale change to be applied
    // during the animation in a way that the zoom is happening in a smooth-looking way
    val transformation: (Double) -> Double = { t ->
      val x = t * zoomChangePerInterval
      val threshold = SCALE_CHANGES_PER_ANIMATION / 2
      when {
        zoomChangePerInterval > 0.05 && t <= threshold -> {
          2 * x * x * x
        }
        t <= threshold -> {
          2 * x * x
        }
        else -> {
          2 * x + (SCALE_CHANGES_PER_ANIMATION - x) + (SCALE_CHANGES_PER_ANIMATION / 2)
        }
      }
    }

    var intervalCounter = 0.0
    currentTimer =
      Timer(intervalDelay) {
          if (isScaleSame(currentScale, newScale)) {
            // scale is the same, we don't need to change scale anymore, timer can be stopped.
            currentTimer?.stop()
          } else if (intervalCounter >= SCALE_CHANGES_PER_ANIMATION) {
            // we have reached the maximum scale changes per interval we set the zoom to newScale,
            // no matter if the animation was completed or not, to ensure the user to be in the
            // requested scale level.
            changeScale(newScale, false)
            currentTimer?.stop()
          } else {
            val updatedScale =
              if (previousScale <= newScale) {
                minOf(newScale, previousScale + transformation(intervalCounter))
              } else {
                maxOf(newScale, previousScale - transformation(intervalCounter))
              }
            changeScale(updatedScale, true)
          }
          intervalCounter++
        }
        .apply { start() }
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

  @UiThread
  override fun zoomToFit(): Boolean {
    // The zoom to fit might not be applied if the surface is not ready yet (e.g. not rendered or
    // not resized). In that case, the zoom to fit will be applied later when the surface is ready.
    return zoomToFitIfReady(ZoomMaskConstants.NOTIFY_ZOOM_TO_FIT_INT_MASK)
  }

  @UiThread override fun zoom(type: ZoomType): Boolean = zoom(type, -1, -1)

  override fun canZoomIn(): Boolean = currentScale < maxScale && !isScaleSame(scale, maxScale)

  override fun canZoomOut(): Boolean = currentScale > minScale && !isScaleSame(minScale, scale)

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
    scaleListener = listener
  }

  /**
   * If the differences of two scales are smaller than tolerance, they are considered as the same
   * scale.
   */
  private fun isScaleSame(@SurfaceScale scaleA: Double, @SurfaceScale scaleB: Double): Boolean {
    val tolerance: Double = SCALING_THRESHOLD / screenScalingFactor
    return abs(scaleA - scaleB) < tolerance
  }

  /**
   * Resets the bitwise mask responsible to wait for [notifyZoomToFit]. Resetting will allow
   * DesignSurface to call [zoomToFit] as if it happens for the first time.
   *
   * This is useful when we switch modes or layouts.
   *
   * @param shouldWaitForResize When true, the zoom mask waits for the resize notification
   *   [ZoomMaskConstants.NOTIFY_COMPONENT_RESIZED_INT_MASK]. When false, the notification is
   *   applied immediately, avoiding the need to wait for the next [DesignSurface] resize event.
   * @param width wip
   * @param height wip Note: if [waitForRenderBeforeZoomToFit] is enabled, it will wait
   *   [notifyZoomToFit] to be performed at least once before trying to apply zoom-to-fit.
   */
  override fun resetZoomToFitSettings(shouldWaitForResize: Boolean, surfaceSize: Dimension) {
    val newZoomToFitStateMask =
      if (!shouldWaitForResize && surfaceSize.height > 0 && surfaceSize.width > 0) {
        // If we want to perform a zoom-to-fit, but we don't need that [DesignSurface] notifies that
        // has been resized we reset the mask adding [NOTIFY_COMPONENT_RESIZED_INT_MASK] already.
        ZoomMaskConstants.NOTIFY_COMPONENT_RESIZED_INT_MASK or
          ZoomMaskConstants.NOTIFY_LAYOUT_CREATED_INT_MASK
      } else {
        ZoomMaskConstants.INITIAL_STATE_INT_MASK
      }

    // If we want to perform a zoom-to-fit, and we need to wait for the creation of a layout and
    // the resize of design surface we set the mask to its initial bitwise number
    // [INITIAL_STATE_INT_MASK].
    currentZoomToFitMask.set(newZoomToFitStateMask)
  }

  suspend fun notifyDesignSurfaceCreated() {
    if (currentZoomToFitMask.get() != ZoomMaskConstants.ZOOM_TO_FIT_DONE_INT_MASK) {
      withContext(Dispatchers.EDT) {
        // Premature zoom updates can occur if NOTIFY_COMPONENT_RESIZED_INT_MASK is updated
        // before the component is created.
        // This is avoided by calling NOTIFY_LAYOUT_CREATED_INT_MASK on component creation.
        zoomToFitIfReady(ZoomMaskConstants.NOTIFY_LAYOUT_CREATED_INT_MASK)
      }
    }
  }

  fun notifyDesignSurfaceResized(width: Int, height: Int, isShowing: Boolean = true) {
    if (
      currentZoomToFitMask.get() != ZoomMaskConstants.ZOOM_TO_FIT_DONE_INT_MASK &&
        isShowing &&
        width > 0 &&
        height > 0
    ) {
      zoomToFitIfReady(ZoomMaskConstants.NOTIFY_COMPONENT_RESIZED_INT_MASK)
    }
  }

  /**
   * Try to apply [zoomToFit] if [DesignSurface] has been resized and its [bitwiseNumber] mask is
   * equal to the [expectedZoomToFitMask]. This function solves a race condition of when the sizes
   * of the content to show and the sizes of [DesignSurface] aren't yet synchronized causing a wrong
   * fitScale value.
   *
   * Note: if [waitForRenderBeforeZoomToFit] is enabled it will wait [notifyZoomToFit] to be
   * performed at least once. if [waitForRenderBeforeZoomToFit] is disabled it will directly perform
   * [zoomToFit]
   */
  @UiThread
  protected open fun zoomToFitIfReady(bitwiseNumber: Int): Boolean {
    val newMask =
      currentZoomToFitMask.updateAndGet {
        if (it == expectedZoomToFitMask || it == ZoomMaskConstants.ZOOM_TO_FIT_DONE_INT_MASK) {
          // The operations needed to apply zoom-to-fit are complete, we mark the mask as done.
          ZoomMaskConstants.ZOOM_TO_FIT_DONE_INT_MASK
        } else {
          // Calculate the new mask value.
          it or bitwiseNumber
        }
      }
    if (newMask == expectedZoomToFitMask) {
      return zoom(ZoomType.FIT, -1, -1)
    }
    return false
  }

  /**
   * Class to define constants used to manage the bitwise logic to check if apply zoom-to-fit. These
   * constants are integers masks to be used in bitwise operations.
   *
   * @see [currentZoomToFitMask]
   */
  protected class ZoomMaskConstants {
    companion object {

      /** Constant to represent the initial state, where none of the values below are set. */
      const val INITIAL_STATE_INT_MASK = 0

      /**
       * Number used as part of the bitwise mask to notify [DesignSurface] to apply zoom-to-fit.
       *
       * @see [DesignSurface.notifyZoomToFit]
       */
      const val NOTIFY_ZOOM_TO_FIT_INT_MASK = 1

      /**
       * Number used as part of the bitwise mask to notify [DesignSurface] has been resized.
       *
       * @see also [DesignSurface.zoomToFitIfReady].
       */
      const val NOTIFY_COMPONENT_RESIZED_INT_MASK = 2

      /**
       * Number used as part of the bitwise mask to notify to [DesignSurface] its layout has been
       * created.
       *
       * @see also [DesignSurface.zoomToFitIfReady].
       */
      const val NOTIFY_LAYOUT_CREATED_INT_MASK = 4

      /**
       * The expected bitwise Integer when
       * * [DesignSurface] sizes is updated
       * * preview renders and
       * * layout is created
       *
       * It indicates that the zooming has been done already and should not have shared bits with
       * [NOTIFY_ZOOM_TO_FIT_INT_MASK], [NOTIFY_COMPONENT_RESIZED_INT_MASK] or
       * [NOTIFY_LAYOUT_CREATED_INT_MASK].
       */
      const val ZOOM_TO_FIT_DONE_INT_MASK = 8
    }
  }

  @TestOnly
  fun notifyLayoutCreatedForTest() {
    zoomToFitIfReady(ZoomMaskConstants.NOTIFY_LAYOUT_CREATED_INT_MASK)
  }

  @TestOnly
  fun notifyComponentResizedForTest() {
    zoomToFitIfReady(ZoomMaskConstants.NOTIFY_COMPONENT_RESIZED_INT_MASK)
  }
}
