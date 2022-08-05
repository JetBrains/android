/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.tools.adtui.ImageUtils
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

/**
 * Layout of the device frame and mask for a particular display orientation.
 *
 * @param displaySize the size of the display
 * @param frameRectangle the frame boundary rectangle relative to the upper left corner of the display
 * @param frameImages the images constituting the device frame
 * @param maskImages the images constituting the device display mask
 */
class SkinLayout(val displaySize: Dimension, val frameRectangle: Rectangle,
                 val frameImages: List<AnchoredImage>, val maskImages: List<AnchoredImage>) {

  /**
   * Creates a layout without a frame or mask.
   */
  constructor(width: Int, height: Int) : this(Dimension(width, height), Rectangle(0, 0, width, height), emptyList(), emptyList())

  /**
   * Draws frame and mask to the given graphics context. The [displayRectangle]  parameter defines
   * the coordinates and the scaled size of the display.
   */
  fun drawFrameAndMask(g: Graphics2D, displayRectangle: Rectangle) {
    if (frameImages.isNotEmpty() || maskImages.isNotEmpty()) {
      val scaleX = displayRectangle.width.toDouble() / displaySize.width
      val scaleY = displayRectangle.height.toDouble() / displaySize.height
      val transform = AffineTransform()
      // Draw frame.
      for (image in frameImages) {
        drawImage(g, image, displayRectangle, scaleX, scaleY, transform)
      }
      for (image in maskImages) {
        drawImage(g, image, displayRectangle, scaleX, scaleY, transform)
      }
    }
  }

  private fun drawImage(g: Graphics2D, anchoredImage: AnchoredImage, displayRectangle: Rectangle, scaleX: Double, scaleY: Double,
                        transform: AffineTransform) {
    val x = displayRectangle.x + anchoredImage.anchorPoint.x * displayRectangle.width + anchoredImage.offset.x.scaled(scaleX)
    val y = displayRectangle.y + anchoredImage.anchorPoint.y * displayRectangle.height + anchoredImage.offset.y.scaled(scaleY)
    transform.setToTranslation(x.toDouble(), y.toDouble())
    transform.scale(scaleX, scaleY)
    g.drawImage(anchoredImage.image, transform, null)
  }
}

/**
 * Image attached to a rectangle.
 *
 * @param image the graphic image
 * @param size the size of the image
 * @param anchorPoint the point on the boundary of the display rectangle the image is attached to
 * @param offset the offset of the upper left corner of the image relative to the anchor point
 */
class AnchoredImage(val image: BufferedImage, val size: Dimension, val anchorPoint: AnchorPoint, val offset: Point) {
  /**
   * Creates another [AnchoredImage] that is result of rotating and scaling this one. Returns null
   * if the scaled image has zero width or height.
   *
   * @param orientationQuadrants the rotation that is applied to the image and the display rectangle
   * @param scaleX the X-axis scale factor applied to the rotated image
   * @param scaleY the Y-axis scale factor applied to the rotated image
   */
  fun rotatedAndScaled(orientationQuadrants: Int, scaleX: Double, scaleY: Double): AnchoredImage? {
    val rotatedSize = size.rotatedByQuadrants(orientationQuadrants)
    val width = rotatedSize.width.scaled(scaleX)
    val height = rotatedSize.height.scaled(scaleY)
    if (width == 0 || height == 0) {
      return null // Degenerate image.
    }
    val rotatedAnchorPoint = anchorPoint.rotatedByQuadrants(orientationQuadrants)
    val rotatedOffset = offset.rotatedByQuadrants(orientationQuadrants)
    val transformedOffset =
      when (orientationQuadrants) {
        1 -> Point(rotatedOffset.x.scaled(scaleX), rotatedOffset.y.scaled(scaleY) - height)
        2 -> Point(rotatedOffset.x.scaled(scaleX) - width, rotatedOffset.y.scaled(scaleY) - height)
        3 -> Point(rotatedOffset.x.scaled(scaleX) - width, rotatedOffset.y.scaled(scaleY))
        else -> Point(rotatedOffset.x.scaled(scaleX), rotatedOffset.y.scaled(scaleY))
      }
    val transformedImage = ImageUtils.rotateByQuadrantsAndScale(image, orientationQuadrants, width, height)
    return AnchoredImage(transformedImage, Dimension(width, height), rotatedAnchorPoint, transformedOffset)
  }
}

/**
 * One of the four corners of the display rectangle.
 *
 * @param x the normalized X coordinate, with the value of 0 or 1
 * @param y the normalized Y coordinate, with the value of 0 or 1
 */
enum class AnchorPoint(val x: Int, val y: Int) {
  TOP_LEFT(0, 0), BOTTOM_LEFT(0, 1), BOTTOM_RIGHT(1, 1), TOP_RIGHT(1, 0);

  /**
   * Returns the anchor point corresponding to this one after rotating the display rectangle.
   *
   * @param rotation the rotation of the display rectangle in quadrants counterclockwise
   */
  fun rotatedByQuadrants(rotation: Int): AnchorPoint {
    return values()[(ordinal + rotation) % values().size]
  }
}