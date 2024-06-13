/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.android.tools.adtui.Zoomable
import com.android.tools.adtui.actions.ZoomType
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val ZOOM_LEVELS = doubleArrayOf(0.0625, 0.125, 0.25, 0.5, 1.0, 2.0, 4.0)
private val SQRT2 = sqrt(2.0)

/**
 * A [BorderLayoutPanel] with zoom support.
 */
abstract class ZoomablePanel : BorderLayoutPanel(), Zoomable {

  protected var screenScale = 0.0 // Scale factor of the host screen.
    get() {
      if (field == 0.0) {
        field = graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
      }
      return field
    }

  /** Width in physical pixels. */
  protected val physicalWidth
    get() = width.scaled(screenScale)

  /** Height in physical pixels. */
  protected val physicalHeight
    get() = height.scaled(screenScale)

  /** Size in physical pixels. */
  protected val physicalSize
    get() = Dimension(physicalWidth, physicalHeight)

  override val scale: Double
    get() = roundDownIfNecessary(computeScaleToFit(computeMaxImageSize()))

  override val screenScalingFactor
    get() = screenScale

  internal val explicitlySetPreferredSize: Dimension?
    get() = if (isPreferredSizeSet) preferredSize else null

  /**
   * An integer number represented as Double. If zero, indicates that fractional scale above 1
   * is not allowed. Otherwise, indicates that fractional scale is allowed between
   * `fractionalScaleRange` and `fractionalScaleRange` + 1.
   */
  private var fractionalScaleRange: Double = 0.0

  protected abstract fun computeActualSize(): Dimension

  protected fun roundDownIfNecessary(scale: Double): Double {
    val roundedScale = roundDownIfGreaterThanOne(scale)
    return if (roundedScale == fractionalScaleRange) scale else roundedScale
  }

  protected abstract fun canZoom(): Boolean

  override fun zoom(type: ZoomType): Boolean {
    val oldFractionalScaleRange = fractionalScaleRange
    if (type == ZoomType.FIT) {
      if (fractionalScaleRange == 0.0) {
        fractionalScaleRange = roundDownIfGreaterThanOne(computeScaleToFitInParent()) // Allow fractional scale greater than one.
      }
    }
    else {
      fractionalScaleRange = 0.0
    }
    val scaledSize = computeZoomedSize(type)
    if (scaledSize == preferredSize && fractionalScaleRange == oldFractionalScaleRange) {
      return false
    }
    preferredSize = scaledSize
    revalidate()
    repaint()
    return true
  }

  override fun canZoomIn(): Boolean {
    return canZoom() && computeZoomedSize(ZoomType.IN) != explicitlySetPreferredSize
  }

  override fun canZoomOut(): Boolean {
    return canZoom() && (computeZoomedSize(ZoomType.OUT) != explicitlySetPreferredSize || isFractionalGreaterThanOne(scale))
  }

  override fun canZoomToActual(): Boolean =
    canZoom() && (computeZoomedSize(ZoomType.ACTUAL) != explicitlySetPreferredSize || isFractionalGreaterThanOne(scale))

  override fun canZoomToFit(): Boolean {
    if (!canZoom()) {
      return false
    }
    if (isPreferredSizeSet) {
      return true
    }
    if (fractionalScaleRange != 0.0) {
      return false
    }
    val scaleToFit = computeScaleToFitInParent()
    val roundedScale = roundDownIfGreaterThanOne(scaleToFit)
    return roundedScale < scaleToFit
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    super.setBounds(x, y, width, height)
    if (fractionalScaleRange != 0.0 && fractionalScaleRange != roundDownIfGreaterThanOne(computeScaleToFit(computeMaxImageSize()))) {
      fractionalScaleRange = 0.0
    }
  }

  /**
   * Computes the maximum allowed size of the device display image in physical pixels.
   */
  protected fun computeMaxImageSize(): Dimension =
    (explicitlySetPreferredSize ?: size).scaled(screenScale)

  /**
   * Computes the preferred size in virtual pixels after the given zoom operation.
   * The preferred size is null for zoom to fit.
   */
  private fun computeZoomedSize(zoomType: ZoomType): Dimension? {
    val newScale = when (zoomType) {
      ZoomType.IN -> {
        val nextScale = nearestZoomLevel(scale * (2 * SQRT2))
        val fitScale = roundDownIfGreaterThanOne(computeScaleToFitInParent())
        if (nextScale >= fitScale) {
          if (fitScale >= ZOOM_LEVELS.last()) {
            return null
          }
        }
        nextScale
      }

      ZoomType.OUT -> {
        val scale = scale
        var nextScale = nearestZoomLevel(scale / SQRT2)
        val fitScale = roundDownIfGreaterThanOne(computeScaleToFitInParent())
        if (fitScale > 1) {
          if (nextScale < 1) {
            nextScale = 1.0
          }
        }
        else if (nextScale <= fitScale || nextScale >= scale) {
          return null
        }
        nextScale
      }

      ZoomType.ACTUAL -> {
        if (roundDownIfGreaterThanOne(computeScaleToFitInParent()) == 1.0) {
          return null
        }
        1.0
      }
      ZoomType.FIT -> return null
      else -> throw IllegalArgumentException("Unsupported zoom type $zoomType")
    }
    val newScaledSize = computeActualSize().scaled(newScale)
    return newScaledSize.scaled(1 / screenScale)
  }

  /** Returns the highest zoom level not exceeding [scale]. */
  private fun nearestZoomLevel(scale: Double): Double {
    // Binary search.
    var low = 0
    var high = ZOOM_LEVELS.size

    while (low < high) {
      val mid = low + high ushr 1
      val level = ZOOM_LEVELS[mid]
      val cmp = level - scale
      if (cmp < 0) {
        low = mid + 1
      } else if (cmp > 0) {
        high = mid
      } else {
        return level // Found.
      }
    }
    return ZOOM_LEVELS[max(low - 1, 0)]
  }

  private fun computeScaleToFitInParent() =
    computeScaleToFit(computeAvailableSize())

  private fun computeScaleToFit(availableSize: Dimension): Double =
    computeScaleToFit(computeActualSize(), availableSize)

  private fun computeScaleToFit(actualSize: Dimension, availableSize: Dimension): Double {
    if (actualSize.width == 0 || actualSize.height == 0) {
      return 1.0
    }
    return min(availableSize.width.toDouble() / actualSize.width, availableSize.height.toDouble() / actualSize.height)
  }

  private fun roundDownIfGreaterThanOne(scale: Double): Double =
    if (scale <= 1.0) scale else floor(scale)

  private fun isFractionalGreaterThanOne(scale: Double): Boolean =
    scale > 1.0 && floor(scale) != scale

  private fun computeAvailableSize(): Dimension =
    parent?.sizeWithoutInsets?.scaled(screenScale) ?: Dimension(0, 0)
}