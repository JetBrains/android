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
package com.android.tools.adtui.device

import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.util.rotatedByQuadrants
import com.android.tools.adtui.util.scaled
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
 * @param displayCornerSize the dimensions of the elliptical corner arc
 * @param frameRectangle the frame boundary rectangle relative to the upper left corner of the display
 * @param frameImages the images constituting the device frame
 * @param maskImages the images constituting the device display mask
 */
class SkinLayout(val displaySize: Dimension, val displayCornerSize: Dimension, val frameRectangle: Rectangle,
                 val frameImages: List<AnchoredImage>, val maskImages: List<AnchoredImage>, val buttons: List<SkinButton>) {

  /**
   * Creates a layout without a frame or mask.
   */
  constructor(width: Int, height: Int) :
      this(Dimension(width, height), Dimension(0, 0), Rectangle(0, 0, width, height),
           emptyList(), emptyList(), emptyList())

  /**
   * Draws frame and mask to the given graphics context. The [displayRectangle]  parameter defines
   * the coordinates and the scaled size of the display.
   */
  fun drawFrameAndMask(g: Graphics2D, displayRectangle: Rectangle, highlightedButtonKey: String? = null) {
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
      if (highlightedButtonKey != null) {
        val highlightedButton = buttons.find { it.keyName == highlightedButtonKey }
        if (highlightedButton != null) {
          drawImage(g, highlightedButton.image, displayRectangle, scaleX, scaleY, transform)
        }
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

  /**
   * Returns the skin button containing the given coordinates, or null if not found.
   * The coordinates are considered contained in a button if they are located inside the button
   * rectangle and the corresponding pixel of the button image is not fully transparent.
   */
  fun findSkinButtonContaining(x: Int, y: Int): SkinButton? {
    for (button in buttons) {
      val anchoredImage = button.image
      val relativeX = x - anchoredImage.offset.x - anchoredImage.anchorPoint.x * displaySize.width
      if (relativeX < 0 || relativeX >= anchoredImage.size.width) {
        continue
      }
      val relativeY = y - anchoredImage.offset.y - anchoredImage.anchorPoint.y * displaySize.height
      if (relativeY < 0 || relativeY >= anchoredImage.size.height) {
        continue
      }
      if (!ImageUtils.isTransparentPixel(anchoredImage.image, relativeX, relativeY)) {
        return button
      }
    }
    return null
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
data class AnchoredImage(val image: BufferedImage, val size: Dimension, val anchorPoint: AnchorPoint, val offset: Point) {
  /**
   * Creates another [AnchoredImage] that is the result of rotating and scaling this one.
   * Returns null if the scaled image has zero width or height.
   */
  internal fun rotatedAndScaled(imageTransformer: ImageTransformer): AnchoredImage? {
    val rotationQuadrants = imageTransformer.rotationQuadrants
    val scaleX = imageTransformer.scaleX
    val scaleY = imageTransformer.scaleY
    val rotatedSize = size.rotatedByQuadrants(rotationQuadrants)
    val width = rotatedSize.width.scaled(scaleX)
    val height = rotatedSize.height.scaled(scaleY)
    if (width == 0 || height == 0) {
      return null // Degenerate image.
    }
    val transformedImage = imageTransformer.transform(image) ?: return null
    val rotatedAnchorPoint = anchorPoint.rotatedByQuadrants(rotationQuadrants)
    val rotatedOffset = offset.rotatedByQuadrants(rotationQuadrants)
    val transformedOffset =
      when (rotationQuadrants) {
        1 -> Point(rotatedOffset.x.scaled(scaleX), rotatedOffset.y.scaled(scaleY) - height)
        2 -> Point(rotatedOffset.x.scaled(scaleX) - width, rotatedOffset.y.scaled(scaleY) - height)
        3 -> Point(rotatedOffset.x.scaled(scaleX) - width, rotatedOffset.y.scaled(scaleY))
        else -> Point(rotatedOffset.x.scaled(scaleX), rotatedOffset.y.scaled(scaleY))
      }
    return AnchoredImage(transformedImage, Dimension(width, height), rotatedAnchorPoint, transformedOffset)
  }

  /** Returns the AnchoredImage shifted by [x] and [y] and reanchored to the nearest corner of the display. */
  fun translatedAndReanchored(x: Int, y: Int, displaySize: Dimension): AnchoredImage {
    val point = Point(offset.x + x, offset.y + y)
    var minDistanceSquared = Int.MAX_VALUE
    var closestAnchor = anchorPoint
    for (anchor in AnchorPoint.entries) {
      val cornerX = displaySize.width * (anchor.x - anchorPoint.x)
      val cornerY = displaySize.height * (anchor.y - anchorPoint.y)
      val dx = point.x - cornerX
      val dy = point.y - cornerY
      val d = dx * dx + dy * dy
      if (d < minDistanceSquared) {
        minDistanceSquared = d
        closestAnchor = anchor
      }
    }
    point.translate(displaySize.width * (anchorPoint.x - closestAnchor.x), displaySize.height * (anchorPoint.y - closestAnchor.y))
    return AnchoredImage(image, size, closestAnchor, point)
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
    return entries[(ordinal + rotation) % entries.size]
  }
}

/**
 * Rotates and scales graphical images.
 *
 * @param rotationQuadrants the rotation that is applied to images
 * @param scaleX the X-axis scale factor applied to images after rotation
 * @param scaleY the Y-axis scale factor applied to images after rotation
 */
internal class ImageTransformer(val rotationQuadrants: Int, val scaleX: Double, val scaleY: Double) {

  private val cache = mutableMapOf<BufferedImage, BufferedImage?>()

  /**
   * Returns the transformed image or null if the transformed image would have zero width or height.
   */
  fun transform(image: BufferedImage): BufferedImage? {
    return cache.computeIfAbsent(image) {
      val rotatedSize = Dimension(image.width, image.height).rotatedByQuadrants(rotationQuadrants)
      val width = rotatedSize.width.scaled(scaleX)
      val height = rotatedSize.height.scaled(scaleY)
      if (width > 0 && height > 0) {
        ImageUtils.rotateByQuadrantsAndScale(image, rotationQuadrants, width, height, BufferedImage.TYPE_INT_ARGB)
      }
      else {
        null // Degenerate image.
      }
    }
  }
}

class SkinButton(val keyName: String, val image: AnchoredImage)
