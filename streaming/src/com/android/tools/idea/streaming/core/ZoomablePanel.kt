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

import com.android.tools.adtui.util.scaled
import com.intellij.ide.ActivityTracker
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private val ZOOM_LEVELS = doubleArrayOf(0.0625, 0.125, 0.25, 0.5, 1.0, 2.0, 4.0)

/** A [BorderLayoutPanel] with zoom support. */
internal abstract class ZoomablePanel : BorderLayoutPanel(), Zoomable {

  override val screenScalingFactor: Double
    get() = if (JreHiDpiUtil.isJreHiDPI(graphicsConfiguration)) JBUIScale.sysScale(this).toDouble() else 1.0

  /** Width in physical pixels. */
  protected val physicalWidth
    get() = width.scaled(screenScalingFactor)

  /** Height in physical pixels. */
  protected val physicalHeight
    get() = height.scaled(screenScalingFactor)

  /** Size in physical pixels. */
  protected val physicalSize
    get() = Dimension(physicalWidth, physicalHeight)

  override val scale: Double
    get() = roundDownIfNecessary(computeScaleToFit(framing, computeMaxImageSize()))

  internal val explicitlySetPreferredSize: Dimension?
    get() = if (isPreferredSizeSet) preferredSize else null

  /**
   * An integer number represented as Double. If zero, indicates that fractional scale above 1 is not allowed. Otherwise, indicates that
   * fractional scale is allowed between `fractionalScaleRange` and `fractionalScaleRange` + 1.
   */
  private var fractionalScaleRange: Double = 0.0

  /** Indicates whether the view is scaled so that its inner part fits into the available space. */
  protected open var framing: Framing = Framing.OUTER

  /** Returns the size of the content at 100% zoom. */
  protected abstract fun computeActualSize(framing: Framing): Dimension

  /** Returns true if the panel contains zoomable content. */
  protected abstract fun canZoom(): Boolean

  protected fun roundDownIfNecessary(scale: Double): Double {
    val roundedScale = roundDownIfGreaterThanOne(scale)
    return if (roundedScale == fractionalScaleRange) scale else roundedScale
  }

  override fun zoom(zoomType: ZoomType): Boolean {
    val oldFractionalScaleRange = fractionalScaleRange
    val newFraming = zoomType.toFraming()
    if (zoomType == ZoomType.FIT || zoomType == ZoomType.FIT_INNER) {
      if (fractionalScaleRange == 0.0) {
        // Allow fractional scale greater than one.
        fractionalScaleRange = roundDownIfGreaterThanOne(computeScaleToFitInParent(newFraming))
      }
    } else {
      fractionalScaleRange = 0.0
    }
    val scaledSize = computeZoomedSize(zoomType)
    if (scaledSize == preferredSize && fractionalScaleRange == oldFractionalScaleRange && newFraming == framing) {
      return false
    }
    preferredSize = scaledSize
    framing = newFraming
    revalidate()
    repaint()
    return true
  }

  override fun canZoom(zoomType: ZoomType): Boolean {
    return canZoom() &&
      when (zoomType) {
        ZoomType.IN -> computeZoomedSize(zoomType) != explicitlySetPreferredSize

        ZoomType.OUT,
        ZoomType.ACTUAL -> computeZoomedSize(zoomType) != explicitlySetPreferredSize || isFractionalGreaterThanOne(scale)

        ZoomType.FIT,
        ZoomType.FIT_INNER -> {
          if (zoomType == ZoomType.FIT_INNER && !hasInnerPart) {
            return false
          }
          if (isPreferredSizeSet || framing != zoomType.toFraming()) {
            return true
          }
          if (fractionalScaleRange != 0.0) {
            return false
          }
          val scaleToFit = computeScaleToFitInParent(zoomType.toFraming())
          val roundedScale = roundDownIfGreaterThanOne(scaleToFit)
          return roundedScale < scaleToFit
        }
      }
  }

  fun resetZoom() {
    preferredSize = null
    fractionalScaleRange = 0.0
    revalidate()
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    val sizeChanged = width != this.width || height != this.height
    super.setBounds(x, y, width, height)
    if (
      fractionalScaleRange != 0.0 && fractionalScaleRange != roundDownIfGreaterThanOne(computeScaleToFit(framing, computeMaxImageSize()))
    ) {
      fractionalScaleRange = 0.0
    }
    if (sizeChanged) {
      ActivityTracker.getInstance().inc() // Trigger a toolbar update.
    }
  }

  /** Computes the maximum allowed size of the device display image in physical pixels. */
  protected fun computeMaxImageSize(): Dimension = (explicitlySetPreferredSize ?: size).scaled(screenScalingFactor)

  /** Computes the preferred size in virtual pixels after the given zoom operation. The preferred size is null for zoom to fit. */
  private fun computeZoomedSize(zoomType: ZoomType): Dimension? {
    val newFraming = zoomType.toFraming()
    val newScale =
      when (zoomType) {
        ZoomType.IN -> {
          val scale = scale
          val lastZoomLevelIndex = ZOOM_LEVELS.size - 1
          var index = (findZoomLevelsPosition(scale) + 1).coerceAtMost(lastZoomLevelIndex)
          if (index < lastZoomLevelIndex && areSamePercentages(scale, ZOOM_LEVELS[index])) {
            ++index
          }
          ZOOM_LEVELS[index]
        }

        ZoomType.OUT -> {
          val scale = scale
          if (scale > ZOOM_LEVELS[0]) {
            var index = findZoomLevelsPosition(scale)
            if (index > 0 && scale == ZOOM_LEVELS[index]) {
              --index
            }
            val nextScale = ZOOM_LEVELS[index]
            val fitScale = roundDownIfGreaterThanOne(computeScaleToFitInParent(newFraming))
            if (areSamePercentages(nextScale, fitScale) || (nextScale < fitScale && fitScale <= 1)) {
              return null
            }
            if (fitScale <= 1 || nextScale >= 1) nextScale else scale
          } else {
            scale
          }
        }

        ZoomType.FIT,
        ZoomType.FIT_INNER -> return null

        ZoomType.ACTUAL -> {
          if (roundDownIfGreaterThanOne(computeScaleToFitInParent(newFraming)) == 1.0) {
            return null
          }
          1.0
        }
      }
    val newScaledSize = computeActualSize(newFraming).scaled(newScale)
    return newScaledSize.scaled(1 / screenScalingFactor)
  }

  /** Returns the index of the highest zoom level not exceeding [scale], or -1 if there is no such level. */
  private fun findZoomLevelsPosition(scale: Double): Int {
    val n = ZOOM_LEVELS.size
    for (i in 0 until n) {
      if (scale < ZOOM_LEVELS[i]) {
        return i - 1
      }
    }
    return n - 1
  }

  /** Checks is the two given numbers would look the same when expressed as integer percentages. */
  private fun areSamePercentages(d1: Double, d2: Double) = (d1 * 100).roundToInt() == (d2 * 100).roundToInt()

  private fun computeScaleToFitInParent(framing: Framing) = computeScaleToFit(framing, computeAvailableSize())

  private fun computeScaleToFit(framing: Framing, availableSize: Dimension): Double =
    computeScaleToFit(computeActualSize(framing), availableSize)

  private fun computeScaleToFit(actualSize: Dimension, availableSize: Dimension): Double {
    if (actualSize.width == 0 || actualSize.height == 0) {
      return 1.0
    }
    return min(availableSize.width.toDouble() / actualSize.width, availableSize.height.toDouble() / actualSize.height)
  }

  private fun roundDownIfGreaterThanOne(scale: Double): Double = if (scale <= 1.0) scale else floor(scale)

  private fun isFractionalGreaterThanOne(scale: Double): Boolean = scale > 1.0 && floor(scale) != scale

  /** Returns the size of the containing scroll pane without insets. */
  private fun computeAvailableSize(): Dimension = parent?.parent?.sizeWithoutInsets?.scaled(screenScalingFactor) ?: Dimension(0, 0)

  private fun ZoomType.toFraming(): Framing = if (this == ZoomType.FIT_INNER) Framing.INNER else Framing.OUTER

  protected enum class Framing {
    OUTER,
    INNER,
  }
}
