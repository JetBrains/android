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
package com.android.tools.idea.streaming

import com.android.tools.adtui.Zoomable
import com.android.tools.adtui.actions.ZoomType
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_SCALE = 2.0 // Zoom above 200% is not allowed.

private val ZOOM_LEVELS = intArrayOf(5, 10, 25, 50, 100, 200) // In percent.

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
    get() = computeScaleToFit(computeMaxImageSize())

  override val screenScalingFactor
    get() = screenScale

  protected abstract fun canZoom(): Boolean

  override fun zoom(type: ZoomType): Boolean {
    val scaledSize = computeZoomedSize(type)
    if (scaledSize == preferredSize) {
      return false
    }
    preferredSize = scaledSize
    revalidate()
    return true
  }

  override fun canZoomIn(): Boolean {
    return canZoom() && computeZoomedSize(ZoomType.IN) != explicitlySetPreferredSize
  }

  override fun canZoomOut(): Boolean {
    return canZoom() && computeZoomedSize(ZoomType.OUT) != explicitlySetPreferredSize
  }

  override fun canZoomToActual(): Boolean =
    canZoom() && computeZoomedSize(ZoomType.ACTUAL) != explicitlySetPreferredSize

  override fun canZoomToFit(): Boolean =
    canZoom() && isPreferredSizeSet

  internal val explicitlySetPreferredSize: Dimension?
    get() = if (isPreferredSizeSet) preferredSize else null

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
        var nextScale = (ZoomType.zoomIn((scale * 100).roundToInt(), ZOOM_LEVELS) / 100.0)
        val fitScale = computeScaleToFitInParent()
        if (nextScale >= fitScale) {
          if (fitScale >= MAX_SCALE) {
            return null
          }
          if (nextScale > MAX_SCALE) {
            nextScale = MAX_SCALE
          }
        }
        nextScale
      }

      ZoomType.OUT -> {
        var nextScale = ZoomType.zoomOut((scale * 100).roundToInt(), ZOOM_LEVELS) / 100.0
        val fitScale = computeScaleToFitInParent()
        if (fitScale > 1) {
          if (nextScale < 1) {
            nextScale = 1.0
          }
        }
        else if (nextScale <= fitScale) {
          return null
        }
        nextScale
      }

      ZoomType.ACTUAL -> {
        if (computeScaleToFitInParent() == 1.0) {
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

  private fun computeScaleToFitInParent() =
    computeScaleToFit(computeAvailableSize())

  private fun computeAvailableSize(): Dimension =
    parent.sizeWithoutInsets.scaled(screenScale)

  private fun computeScaleToFit(availableSize: Dimension): Double =
    computeScaleToFit(computeActualSize(), availableSize)

  private fun computeScaleToFit(actualSize: Dimension, availableSize: Dimension): Double {
    if (actualSize.width == 0 || actualSize.height == 0) {
      return 1.0
    }
    val scale = min(availableSize.width.toDouble() / actualSize.width, availableSize.height.toDouble() / actualSize.height)
    return if (scale <= 1.0) scale else floor(scale)
  }

  protected abstract fun computeActualSize(): Dimension
}