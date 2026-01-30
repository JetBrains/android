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
import com.android.tools.adtui.util.scaled
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private val ZOOM_LEVELS = doubleArrayOf(0.0625, 0.125, 0.25, 0.5, 1.0, 2.0, 4.0)

/**
 * A [BorderLayoutPanel] with zoom support.
 */
abstract class ZoomablePanel : BorderLayoutPanel(), Zoomable, PropertyChangeListener {

  /** Scale factor of the host screen. */
  protected val screenScale: Double
    get() = if (cachedScreenScale > 0.0) cachedScreenScale else getCurrentScreenScaleOr(1.0)

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

  private var cachedScreenScale = 0.0

  /**
   * An integer number represented as Double. If zero, indicates that fractional scale above 1
   * is not allowed. Otherwise, indicates that fractional scale is allowed between
   * `fractionalScaleRange` and `fractionalScaleRange` + 1.
   */
  private var fractionalScaleRange: Double = 0.0

  /** Returns the size of the content at 100% zoom.*/
  protected abstract fun computeActualSize(): Dimension

  /** Returns true if the panel contains zoomable content. */
  protected abstract fun canZoom(): Boolean

  protected open fun onScreenScaleChanged() {}

  init {
    addPropertyChangeListener(this)
  }

  protected fun roundDownIfNecessary(scale: Double): Double {
    val roundedScale = roundDownIfGreaterThanOne(scale)
    return if (roundedScale == fractionalScaleRange) scale else roundedScale
  }

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

  override fun canZoomIn(): Boolean =
      canZoom() && computeZoomedSize(ZoomType.IN) != explicitlySetPreferredSize

  override fun canZoomOut(): Boolean =
      canZoom() && (computeZoomedSize(ZoomType.OUT) != explicitlySetPreferredSize || isFractionalGreaterThanOne(scale))

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

  fun resetZoom() {
    preferredSize = null
    fractionalScaleRange = 0.0
    revalidate()
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    val sizeChanged = width != this.width || height != this.height
    super.setBounds(x, y, width, height)
    if (fractionalScaleRange != 0.0 && fractionalScaleRange != roundDownIfGreaterThanOne(computeScaleToFit(computeMaxImageSize()))) {
      fractionalScaleRange = 0.0
    }
    if (sizeChanged) {
      thisLogger().info("ZoomablePanel.setBounds: triggering toolbar update") // b/479059316
      ActivityTracker.getInstance().inc() // Trigger a toolbar update.
    }
  }

  override fun propertyChange(event: PropertyChangeEvent) {
    if (event.propertyName == "graphicsConfiguration") {
      val newScreenScale = getCurrentScreenScaleOr(0.0)
      if (newScreenScale != 0.0 && newScreenScale != cachedScreenScale) {
        cachedScreenScale = newScreenScale
        onScreenScaleChanged()
      }
    }
  }

  final override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    super.addPropertyChangeListener(listener)
  }

  private fun getCurrentScreenScaleOr(defaultValue: Double) = graphicsConfiguration?.defaultTransform?.scaleX ?: defaultValue

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
          val fitScale = roundDownIfGreaterThanOne(computeScaleToFitInParent())
          if (areSamePercentages(nextScale, fitScale) || (nextScale < fitScale && fitScale <= 1)) {
            return null
          }
          if (fitScale <= 1 || nextScale >= 1) nextScale else scale
        }
        else {
          scale
        }
      }

      ZoomType.ACTUAL -> {
        if (roundDownIfGreaterThanOne(computeScaleToFitInParent()) == 1.0) {
          return null
        }
        1.0
      }

      ZoomType.FIT -> return null
    }
    val newScaledSize = computeActualSize().scaled(newScale)
    return newScaledSize.scaled(1 / screenScale)
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
  private fun areSamePercentages(d1: Double, d2: Double) =
      (d1 * 100).roundToInt() == (d2 * 100).roundToInt()

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

  /** Returns the size of the containing scroll pane without insets. */
  private fun computeAvailableSize(): Dimension =
      parent?.parent?.sizeWithoutInsets?.scaled(screenScale) ?: Dimension(0, 0)
}