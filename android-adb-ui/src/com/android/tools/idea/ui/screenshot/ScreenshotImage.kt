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
import com.android.tools.adtui.device.SkinDefinition
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

class ScreenshotImage(
  val image: BufferedImage,
  val screenshotOrientationQuadrants: Int,
  val deviceType: DeviceType,
  val deviceName: String,
  val displayId: Int,
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
      deviceType = deviceType,
      deviceName = deviceName,
      displayId = displayId,
      displayInfo = displayInfo,
    )
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

  fun decorate(drawFrame: Boolean, skinDefinition: SkinDefinition, backgroundColor: Color?): BufferedImage {
    val w = image.width
    val h = image.height
    val skin = skinDefinition.createScaledLayout(w, h, screenshotOrientationQuadrants)
    val arcWidth = skin.displayCornerSize.width
    val arcHeight = skin.displayCornerSize.height
    if (drawFrame) {
      val frameRectangle = skin.frameRectangle
      @Suppress("UndesirableClassUsage")
      val decoratedImage = BufferedImage(frameRectangle.width, frameRectangle.height, BufferedImage.TYPE_INT_ARGB)
      val graphics = decoratedImage.createGraphics()
      val displayRectangle = Rectangle(-frameRectangle.x, -frameRectangle.y, w, h)
      graphics.drawImageWithRoundedCorners(image, displayRectangle, arcWidth, arcHeight)
      skin.drawFrameAndMask(graphics, displayRectangle)
      graphics.dispose()
      return decoratedImage
    }

    @Suppress("UndesirableClassUsage")
    val decoratedImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val graphics = decoratedImage.createGraphics()
    val displayRectangle = Rectangle(0, 0, w, h)
    graphics.drawImageWithRoundedCorners(image, displayRectangle, arcWidth, arcHeight)
    graphics.composite = AlphaComposite.getInstance(AlphaComposite.DST_OUT)
    skin.drawFrameAndMask(graphics, displayRectangle) // Erase the part of the image overlapping with the frame.
    if (backgroundColor != null) {
      graphics.color = backgroundColor
      graphics.composite = AlphaComposite.getInstance(AlphaComposite.DST_OVER)
      graphics.fillRect(0, 0, image.width, image.height)
    }
    graphics.dispose()
    return decoratedImage
  }

  private fun Graphics2D.drawImageWithRoundedCorners(image: BufferedImage, displayRectangle: Rectangle, arcWidth: Int, arcHeight: Int) {
    if (arcWidth > 0 && arcHeight > 0) {
      clip = Area(RoundRectangle2D.Double(displayRectangle.x.toDouble(), displayRectangle.y.toDouble(),
                                          displayRectangle.width.toDouble(), displayRectangle.height.toDouble(),
                                          arcWidth.toDouble(), arcHeight.toDouble()))
    }
    drawImage(image, null, displayRectangle.x, displayRectangle.y)
    clip = null
  }
}
