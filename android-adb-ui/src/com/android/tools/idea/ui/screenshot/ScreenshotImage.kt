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
package com.android.tools.idea.ui.screenshot

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.ImageUtils
import java.awt.Dimension
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

class ScreenshotImage(
  val image: BufferedImage,
  val screenshotOrientationQuadrants: Int,
  val deviceType: DeviceType,
  private val displayInfo: String = "",
) {

  val width: Int
    get() = image.width
  val height: Int
    get() = image.height

  // True is the display is round.
  val isRoundDisplay: Boolean = displayInfo.contains("FLAG_ROUND")
  // Size of the display in pixels.
  val displaySize: Dimension? = computeDisplaySize()
  // Display density in dpi, or Double.NaN if not available. Please keep in mind that some devices,
  // e.g. Android TV, report fictitious display density.
  val displayDensity: Double = computeDisplayDensity()

  val isWear: Boolean
    get() = deviceType == DeviceType.WEAR

  /**
   * Returns the rotated and scaled screenshot.
   */
  fun rotatedAndScaled(rotationQuadrants: Int = 0, scale: Double = 1.0): ScreenshotImage {
    if (rotationQuadrants == 0 && scale == 1.0) {
      return this
    }
    val w: Int
    val h: Int
    when (rotationQuadrants % 2) {
      0 -> { w = width; h = height }
      else -> { w = height; h = width }
    }
    return ScreenshotImage(
      image = ImageUtils.rotateByQuadrantsAndScale(image, rotationQuadrants, (w * scale).roundToInt(), (h * scale).roundToInt()),
      screenshotOrientationQuadrants = (screenshotOrientationQuadrants + rotationQuadrants) and 0x03,
      displayInfo = displayInfo,
      deviceType = deviceType)
  }

  private fun computeDisplaySize(): Dimension? {
    val (width, height) = Regex("(\\d+) x (\\d+)").find(displayInfo)?.destructured ?: return null
    return try {
      Dimension(width.toInt(), height.toInt())
    }
    catch (_: NumberFormatException) {
      null
    }
  }

  private fun computeDisplayDensity(): Double {
    val (density) = Regex("density (\\d+)").find(displayInfo)?.destructured ?: return Double.NaN
    return try {
      density.toDouble()
    }
    catch (_: NumberFormatException) {
      Double.NaN
    }
  }
}

