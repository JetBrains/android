/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui

import com.android.annotations.concurrency.Slow
import com.intellij.util.ui.ImageUtil
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.awt.image.SinglePixelPackedSampleModel
import java.io.IOException
import java.io.InputStream
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Utilities related to image processing. */
@Suppress("UndesirableClassUsage", "UseJBColor") // BufferedImage is ok, deliberately not creating Retina images in some cases
object ImageUtils {
  /** Mask of the alpha channel in the ARGB color representation. */
  const val ALPHA_MASK: Int = 0xFF000000.toInt()

  private const val TILE_SIZE = 32

  /** Transforms the source image to TYPE_INT_ARGB if it has TYPE_CUSTOM. */
  @JvmStatic
  fun normalizeImage(source: BufferedImage): BufferedImage {
    if (source.type != BufferedImage.TYPE_CUSTOM) {
      return source
    }
    val result = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
    val g = result.createGraphics()
    g.drawImage(source, 0, 0, null)
    g.dispose()
    return result
  }

  /**
   * Rotates the given image by the given number of quadrants.
   *
   * @param source the source image
   * @param numQuadrants the number of quadrants to rotate by counterclockwise
   * @return the rotated image
   */
  @JvmStatic
  fun rotateByQuadrants(source: BufferedImage, numQuadrants: Int): BufferedImage {
    val quadrants = numQuadrants and 0x3
    if (quadrants == 0) {
      return source
    }

    val w = source.width
    val h = source.height
    val type = source.type

    if (isStandardIntImage(source, type, w, h)) {
      val rotatedW = if (quadrants == 2) w else h
      val rotatedH = if (quadrants == 2) h else w
      val result = BufferedImage(rotatedW, rotatedH, type)
      val srcBuffer = source.raster.dataBuffer as DataBufferInt
      val dstBuffer = result.raster.dataBuffer as DataBufferInt
      rotateIntArray(srcBuffer.data, dstBuffer.data, w, h, quadrants)
      return result
    }

    return rotateByQuadrantsUsingJava2D(source, quadrants, w, h, type)
  }

  private fun isStandardIntImage(image: BufferedImage, type: Int, w: Int, h: Int): Boolean {
    val raster = image.raster
    val dataBuffer = raster.dataBuffer
    val sampleModel = image.sampleModel
    val isSupportedType =
      when (type) {
        BufferedImage.TYPE_INT_ARGB,
        BufferedImage.TYPE_INT_RGB,
        BufferedImage.TYPE_INT_BGR,
        BufferedImage.TYPE_INT_ARGB_PRE -> true
        else -> false
      }
    return isSupportedType &&
      (dataBuffer is DataBufferInt) &&
      (dataBuffer.offset == 0) &&
      (dataBuffer.data.size == w * h) &&
      (sampleModel is SinglePixelPackedSampleModel) &&
      (sampleModel.scanlineStride == w)
  }

  private fun rotateIntArray(src: IntArray, dst: IntArray, w: Int, h: Int, numQuadrants: Int) {
    when (numQuadrants) {
      1 -> { // 90 degrees CCW (270 degrees CW): dst[(w - 1 - x) * h + y] = src[y * w + x]
        var tx = 0
        while (tx < w) {
          val xMax = min(tx + TILE_SIZE, w)
          var ty = 0
          while (ty < h) {
            val yMax = min(ty + TILE_SIZE, h)
            for (x in tx until xMax) {
              val dstRowOffset = (w - 1 - x) * h
              for (y in ty until yMax) {
                dst[dstRowOffset + y] = src[y * w + x]
              }
            }
            ty += TILE_SIZE
          }
          tx += TILE_SIZE
        }
      }

      2 -> { // 180 degrees
        val len = w * h
        for (i in 0 until len) {
          dst[i] = src[len - 1 - i]
        }
      }

      3 -> { // 270 degrees CCW (90 degrees CW): dst[x * h + (h - 1 - y)] = src[y * w + x]
        var tx = 0
        while (tx < w) {
          val xMax = min(tx + TILE_SIZE, w)
          var ty = 0
          while (ty < h) {
            val yMax = min(ty + TILE_SIZE, h)
            for (x in tx until xMax) {
              val dstRowOffset = x * h + h - 1
              for (y in ty until yMax) {
                dst[dstRowOffset - y] = src[y * w + x]
              }
            }
            ty += TILE_SIZE
          }
          tx += TILE_SIZE
        }
      }
    }
  }

  private fun rotateByQuadrantsUsingJava2D(
    source: BufferedImage,
    numQuadrants: Int,
    w: Int,
    h: Int,
    type: Int,
  ): BufferedImage {
    val rotatedW: Int
    val rotatedH: Int
    val shiftX: Int
    val shiftY: Int
    when (numQuadrants) {
      1 -> {
        rotatedW = h
        rotatedH = w
        shiftX = 0
        shiftY = w
      }

      2 -> {
        rotatedW = w
        rotatedH = h
        shiftX = w
        shiftY = h
      }

      3 -> {
        rotatedW = h
        rotatedH = w
        shiftX = h
        shiftY = 0
      }

      else -> {
        rotatedW = w
        rotatedH = h
        shiftX = 0
        shiftY = 0
      }
    }

    val imageType = if (type == BufferedImage.TYPE_CUSTOM) BufferedImage.TYPE_INT_ARGB else type

    val result = BufferedImage(rotatedW, rotatedH, imageType)
    val graphics = result.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
    val transform = AffineTransform()
    // Please notice that the transformations are applied in the reverse order, starting from rotation.
    transform.translate(shiftX.toDouble(), shiftY.toDouble())
    transform.quadrantRotate(-numQuadrants)
    graphics.drawRenderedImage(source, transform)
    graphics.dispose()
    return result
  }

  /**
   * Rotates the given image by the given number of quadrants and scales it to the given dimensions.
   *
   * @param source the source image
   * @param numQuadrants the number of quadrants to rotate by counterclockwise
   * @param destinationWidth the width of the resulting image
   * @param destinationHeight the height of the resulting image
   * @return the rotated and scaled image
   */
  @JvmStatic
  fun rotateByQuadrantsAndScale(
    source: BufferedImage,
    numQuadrants: Int,
    destinationWidth: Int,
    destinationHeight: Int,
  ): BufferedImage {
    val imageType = if (source.type == BufferedImage.TYPE_CUSTOM) BufferedImage.TYPE_INT_ARGB else source.type
    return rotateByQuadrantsAndScale(source, numQuadrants, destinationWidth, destinationHeight, imageType)
  }

  /**
   * Rotates the given image by the given number of quadrants and scales it to the given dimensions.
   *
   * @param source the source image
   * @param numQuadrants the number of quadrants to rotate by counterclockwise
   * @param destinationWidth the width of the resulting image
   * @param destinationHeight the height of the resulting image
   * @param imageType the type of the image to produce
   * @return the rotated and scaled image
   */
  @JvmStatic
  fun rotateByQuadrantsAndScale(
    source: BufferedImage,
    numQuadrants: Int,
    destinationWidth: Int,
    destinationHeight: Int,
    imageType: Int,
  ): BufferedImage {
    val quadrants = numQuadrants and 0x3
    if (quadrants == 0 && destinationWidth == source.width && destinationHeight == source.height) {
      return source
    }

    val w = source.width
    val h = source.height

    val rotatedW: Double
    val rotatedH: Double
    val shiftX: Double
    val shiftY: Double
    when (quadrants) {
      0 -> {
        rotatedW = w.toDouble()
        rotatedH = h.toDouble()
        shiftX = 0.0
        shiftY = 0.0
      }

      1 -> {
        rotatedW = h.toDouble()
        rotatedH = w.toDouble()
        shiftX = 0.0
        shiftY = destinationHeight.toDouble()
      }

      2 -> {
        rotatedW = w.toDouble()
        rotatedH = h.toDouble()
        shiftX = destinationWidth.toDouble()
        shiftY = destinationHeight.toDouble()
      }

      3 -> {
        rotatedW = h.toDouble()
        rotatedH = w.toDouble()
        shiftX = destinationWidth.toDouble()
        shiftY = 0.0
      }

      else -> {
        rotatedW = w.toDouble()
        rotatedH = h.toDouble()
        shiftX = 0.0
        shiftY = 0.0
      }
    }

    val result = BufferedImage(destinationWidth, destinationHeight, imageType)
    val transform = AffineTransform()
    // Please notice that the transformations are applied in the reverse order, starting from rotation.
    transform.translate(shiftX, shiftY)
    transform.scale(destinationWidth / rotatedW, destinationHeight / rotatedH)
    transform.quadrantRotate(-quadrants)
    val transformOp = AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR)
    return transformOp.filter(source, result)
  }

  /** Creates a HiDPI aware image. */
  @JvmStatic
  fun createDipImage(width: Int, height: Int, type: Int): BufferedImage {
    return ImageUtil.createImage(width, height, type)
  }

  /** Returns a new image that is the source image surrounded by a transparent margin of given size. */
  @JvmStatic
  fun addMargin(source: BufferedImage, marginSize: Int): BufferedImage {
    val destWidth = source.width + 2 * marginSize
    val destHeight = source.height + 2 * marginSize

    // since we are adding a transparent margin, make sure destination image has an alpha channel
    val type = if (source.colorModel.hasAlpha()) source.type else BufferedImage.TYPE_INT_ARGB

    val expanded = BufferedImage(destWidth, destHeight, type)
    val g2 = expanded.createGraphics()
    g2.color = Color(0, true)
    g2.fillRect(0, 0, destWidth, destHeight)
    g2.drawImage(source, marginSize, marginSize, null)
    g2.dispose()

    return expanded
  }

  /**
   * Resize the given image
   *
   * @param source the image to be scaled
   * @param amount to scale the image in both directions
   * @return the scaled image
   */
  @JvmStatic
  fun scale(source: BufferedImage, amount: Double): BufferedImage {
    return scale(source, amount, amount, 0, 0, null)
  }

  /**
   * Resize the given image
   *
   * @param source the image to be scaled
   * @param xScale x scale
   * @param yScale y scale
   * @return the scaled image
   */
  @JvmStatic
  fun scale(source: BufferedImage, xScale: Double, yScale: Double): BufferedImage {
    return scale(source, xScale, yScale, 0, 0, null)
  }

  /**
   * Resize the given image
   *
   * @param source the image to be scaled
   * @param xScale x scale
   * @param yScale y scale
   * @param clip an optional clip rectangle to use
   * @return the scaled image
   */
  @JvmStatic
  fun scale(source: BufferedImage, xScale: Double, yScale: Double, clip: Shape?): BufferedImage {
    return scale(source, xScale, yScale, 0, 0, clip)
  }

  /**
   * Resize the given image
   *
   * @param source the image to be scaled
   * @param xScale x scale
   * @param yScale y scale
   * @param rightMargin extra margin to add on the right
   * @param bottomMargin extra margin to add on the bottom
   * @return the scaled image
   */
  @JvmStatic
  fun scale(
    source: BufferedImage,
    xScale: Double,
    yScale: Double,
    rightMargin: Int,
    bottomMargin: Int,
  ): BufferedImage {
    return scale(source, xScale, yScale, rightMargin, bottomMargin, null)
  }

  /**
   * Resize the given image
   *
   * @param source the image to be scaled
   * @param xScale x scale
   * @param yScale y scale
   * @param rightMargin extra margin to add on the right
   * @param bottomMargin extra margin to add on the bottom
   * @param clip an optional clip rectangle to use
   * @return the scaled image
   */
  @JvmStatic
  fun scale(
    source: BufferedImage,
    xScale: Double,
    yScale: Double,
    rightMargin: Int,
    bottomMargin: Int,
    clip: Shape?,
  ): BufferedImage {
    var src = source
    var sourceWidth = src.width
    var sourceHeight = src.height
    val destWidth = max(1, (xScale * sourceWidth).toInt())
    val destHeight = max(1, (yScale * sourceHeight).toInt())
    var imageType = src.type
    if (
      imageType == BufferedImage.TYPE_CUSTOM || imageType == BufferedImage.TYPE_BYTE_INDEXED || imageType == BufferedImage.TYPE_BYTE_BINARY
    ) {
      imageType = BufferedImage.TYPE_INT_ARGB
    }
    if (xScale > 0.5 && yScale > 0.5) {
      val scaled = BufferedImage(destWidth + rightMargin, destHeight + bottomMargin, imageType)
      val g2 = scaled.createGraphics()
      g2.composite = AlphaComposite.Src
      g2.color = Color(0, true)
      g2.fillRect(0, 0, destWidth + rightMargin, destHeight + bottomMargin)
      if (clip != null) {
        g2.clip = clip
      }
      if (xScale == 1.0 && yScale == 1.0) {
        g2.drawImage(src, 0, 0, null)
      } else {
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.drawImage(
          src,
          0,
          0,
          destWidth,
          destHeight,
          0,
          0,
          sourceWidth,
          sourceHeight,
          null,
        )
      }
      g2.dispose()
      return scaled
    } else {
      // When creating a thumbnail, using the above code doesn't work very well;
      // you get some visible artifacts, especially for text. Instead, use the
      // technique of repeatedly scaling the image into half; this will cause
      // proper averaging of neighboring pixels, and will typically (for the kinds
      // of screen sizes used by this utility method in the layout editor) take
      // about 3-4 iterations to get the result since we are logarithmically reducing
      // the size. Besides, each successive pass in operating on much fewer pixels
      // (a reduction of 4 in each pass).
      //
      // However, we may not be resizing to a size that can be reached exactly by
      // successively diving in half. Therefore, once we're within a factor of 2 of
      // the final size, we can do a resize to the exact target size.
      // However, we can get even better results if we perform this final resize
      // up front. Let's say we're going from width 1000 to a destination width of 85.
      // The first approach would cause a resize from 1000 to 500 to 250 to 125, and
      // then a resize from 125 to 85. That last resize can distort/blur a lot.
      // Instead, we can start with the destination width, 85, and double it
      // successfully until we're close to the initial size: 85, then 170,
      // then 340, and finally 680. (The next one, 1360, is larger than 1000).
      // So, now we *start* the thumbnail operation by resizing from width 1000 to
      // width 680, which will preserve a lot of visual details such as text.
      // Then we can successively resize the image in half, 680 to 340 to 170 to 85.
      // We end up with the expected final size, but we've been doing an exact
      // divide-in-half resizing operation at the end so there is less distortion.

      var iterations = 0 // Number of halving operations to perform after the initial resize
      var nearestWidth = destWidth // Width closest to source width that = 2^x, x is integer
      var nearestHeight = destHeight
      while (nearestWidth < sourceWidth / 2) {
        nearestWidth *= 2
        nearestHeight *= 2
        iterations++
      }

      // If we're supposed to add in margins, we need to do it in the initial resizing
      // operation if we don't have any subsequent resizing operations.
      if (iterations == 0) {
        nearestWidth += rightMargin
        nearestHeight += bottomMargin
      }

      var scaled = BufferedImage(nearestWidth, nearestHeight, imageType)

      var g2 = scaled.createGraphics()
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.drawImage(
        src,
        0,
        0,
        nearestWidth,
        nearestHeight,
        0,
        0,
        sourceWidth,
        sourceHeight,
        null,
      )
      g2.dispose()

      sourceWidth = nearestWidth
      sourceHeight = nearestHeight
      src = scaled

      for (iteration in iterations - 1 downTo 0) {
        val halfWidth = sourceWidth / 2
        val halfHeight = sourceHeight / 2
        if (iteration == 0) { // Last iteration: Add margins in final image
          scaled =
            BufferedImage(
              halfWidth + rightMargin,
              halfHeight + bottomMargin,
              imageType,
            )
          g2 = scaled.createGraphics()
          if (clip != null) {
            g2.clip = clip
          }
        } else {
          scaled = BufferedImage(halfWidth, halfHeight, imageType)
          g2 = scaled.createGraphics()
        }
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.drawImage(
          src,
          0,
          0,
          halfWidth,
          halfHeight,
          0,
          0,
          sourceWidth,
          sourceHeight,
          null,
        )
        g2.dispose()

        sourceWidth = halfWidth
        sourceHeight = halfHeight
        src = scaled
      }
      return scaled
    }
  }

  /**
   * Do a fast, low-quality, scaling of the given image
   *
   * @param source the image to be scaled
   * @param xScale x scale
   * @param yScale y scale
   * @return the scaled image
   */
  @JvmStatic
  fun lowQualityFastScale(source: BufferedImage, xScale: Double, yScale: Double): BufferedImage {
    return lowQualityFastScale(source, xScale, yScale, 0, 0, null)
  }

  /**
   * Does a fast, low-quality, scaling of the given image
   *
   * @param source the image to be scaled
   * @param xScale x scale
   * @param yScale y scale
   * @param rightMargin extra margin to add on the right
   * @param bottomMargin extra margin to add on the bottom
   * @param clip an optional clip rectangle to use
   * @return the scaled image
   */
  @JvmStatic
  fun lowQualityFastScale(
    source: BufferedImage,
    xScale: Double,
    yScale: Double,
    rightMargin: Int,
    bottomMargin: Int,
    clip: Shape?,
  ): BufferedImage {
    val sourceWidth = source.width
    val sourceHeight = source.height
    val destWidth = max(1, (xScale * sourceWidth).toInt())
    val destHeight = max(1, (yScale * sourceHeight).toInt())
    var imageType = source.type
    if (imageType == BufferedImage.TYPE_CUSTOM) {
      imageType = BufferedImage.TYPE_INT_ARGB
    }
    val scaled = BufferedImage(destWidth + rightMargin, destHeight + bottomMargin, imageType)
    val g2 = scaled.createGraphics()
    g2.composite = AlphaComposite.Src
    g2.color = Color(0, true)
    g2.fillRect(0, 0, destWidth + rightMargin, destHeight + bottomMargin)
    if (clip != null) {
      g2.clip = clip
    }
    if (xScale == 1.0 && yScale == 1.0) {
      g2.drawImage(source, 0, 0, null)
    } else {
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
      g2.drawImage(source, 0, 0, destWidth, destHeight, 0, 0, sourceWidth, sourceHeight, null)
    }
    g2.dispose()
    return scaled
  }

  /**
   * Creates a [BufferedImage] from the provided inputStream and scale it to fit into the provided dimension while keeping the original
   * aspect ratio.
   *
   * The particularity of this method is that if the original image is more than twice the size of the target dimensions, it doesn't load
   * the full image in memory but only reads enough pixels to have quality good enough for the target size.
   *
   * For example, if an image measures 100x100 pixels, and the target dimension is 10x10 (10 times smaller), only the pixels at x and y
   * coordinates 0, 9, 19,..., 99 will be read.
   *
   * See [javax.imageio.ImageReadParam.setSourceSubsampling] for more details.
   *
   * @param dimension The dimension in which the image will be rendered. The image will keep its original aspect ratio and will be fitted
   *   inside these dimension.
   * @param inputStream the input
   * @return the image file as a [BufferedImage]
   */
  @Slow
  @Throws(IOException::class)
  @JvmStatic
  fun readImageAtScale(inputStream: InputStream, dimension: Dimension): BufferedImage? {
    val imageStream = ImageIO.createImageInputStream(inputStream) ?: return null

    // Find all image readers that recognize the image format
    val readerIterator = ImageIO.getImageReaders(imageStream)
    if (!readerIterator.hasNext()) {
      imageStream.close()
      return null
    }

    val reader = readerIterator.next()
    reader.input = imageStream
    val readParams = reader.defaultReadParam

    val srcW = reader.getWidth(0).toDouble()
    val srcH = reader.getHeight(0).toDouble()
    var scale = if (srcW > srcH) dimension.width / srcW else dimension.height / srcH

    // If the target size is at least twice as small as the origin, we do the subsampling.
    // Otherwise, we just scale.
    if (scale < 0.5) {
      // Because subsampling actually skip pixels, the end result quality is lower
      // than a downscaling (which average neighboring pixels). To minimize the loss
      // of quality, we double the initial scale value so the subsampling is reduced and
      // replaced by a downscaling step.
      scale *= 2.0
      val xStep = floor(1.0 / scale)
      val yStep = floor(1.0 / scale)

      readParams.setSourceSubsampling(xStep.toInt(), yStep.toInt(), 0, 0)
    }

    // Read the image, with the optional downsampling.
    val intermediateImage = reader.read(0, readParams)

    imageStream.close()
    inputStream.close()

    // Do a final scale to be sure that the image fits in the provided dimension
    scale =
      if (srcW > srcH) dimension.width.toDouble() / intermediateImage.width else dimension.height.toDouble() / intermediateImage.height
    return scale(intermediateImage, scale, scale)
  }

  /**
   * Crops blank pixels from the edges of the image and returns the cropped result. We crop off pixels that are blank (meaning they have an
   * alpha value = 0). Note that this is not the same as pixels that aren't opaque (an alpha value other than 255).
   *
   * @param image the image to be cropped
   * @param initialCrop If not null, specifies a rectangle which contains an initial crop to continue. This can be used to crop an image
   *   where you already know about margins in the image
   * @return a cropped version of the source image, or null if the whole image was blank and cropping completely removed everything
   */
  @JvmStatic
  fun cropBlank(image: BufferedImage, initialCrop: Rectangle?): BufferedImage? {
    return cropBlank(image, initialCrop, image.type)
  }

  /**
   * Crops blank pixels from the edges of the image and returns the cropped result. We crop off pixels that are blank (meaning they have an
   * alpha value = 0). Note that this is not the same as pixels that aren't opaque (an alpha value other than 255).
   *
   * @param image the image to be cropped
   * @param initialCrop If not null, specifies a rectangle which contains an initial crop to continue. This can be used to crop an image
   *   where you already know about margins in the image
   * @param imageType the type of [BufferedImage] to create
   * @return a cropped version of the source image, or null if the whole image was blank and cropping completely removed everything
   */
  @JvmStatic
  fun cropBlank(image: BufferedImage?, initialCrop: Rectangle?, imageType: Int): BufferedImage? {
    return crop(image, CropFilter(::isTransparentPixel), initialCrop, imageType)
  }

  /**
   * Determines the crop bounds for the given image.
   *
   * @param image the image to be cropped
   * @param filter the filter determining whether a pixel is blank or not
   * @param initialCrop If not null, specifies a rectangle which contains an initial crop to continue. This can be used to crop an image
   *   where you already know about margins in the image
   * @return the bounds of the crop in the given image, or null if the whole image was blank and cropping completely removed everything
   */
  @JvmStatic
  fun getCropBounds(image: BufferedImage?, filter: CropFilter, initialCrop: Rectangle?): Rectangle? {
    if (image == null) {
      return null
    }

    // First, determine the dimensions of the real image within the image.
    var x1: Int
    var y1: Int
    var x2: Int
    var y2: Int
    if (initialCrop != null) {
      x1 = max(initialCrop.x, 0)
      y1 = max(initialCrop.y, 0)
      x2 = min(initialCrop.x + initialCrop.width, image.width)
      y2 = min(initialCrop.y + initialCrop.height, image.height)
    } else {
      x1 = 0
      y1 = 0
      x2 = image.width
      y2 = image.height
    }

    // Nothing left to crop.
    if (x1 == x2 || y1 == y2) {
      return null
    }

    // This algorithm is linear with respect to the number of pixels in the cropped
    // area of the image. A sublinear algorithm is not possible since each cropped
    // pixel has to be examined at least once because the non-blank part of the image
    // may be disjoint.

    // First determine top edge.
    topEdge@ while (y1 < y2) {
      for (x in x1 until x2) {
        if (!filter.crop(image, x, y1)) {
          break@topEdge
        }
      }
      y1++
    }

    if (y1 == y2) {
      // The image is blank.
      return null
    }

    // Next determine left edge.
    leftEdge@ while (x1 < x2) {
      for (y in y1 until y2) {
        if (!filter.crop(image, x1, y)) {
          break@leftEdge
        }
      }
      x1++
    }

    // Next determine right edge.
    rightEdge@ while (--x2 >= x1) {
      for (y in y1 until y2) {
        if (!filter.crop(image, x2, y)) {
          break@rightEdge
        }
      }
    }
    ++x2

    // Finally determine bottom edge.
    bottomEdge@ while (--y2 >= y1) {
      for (x in x1 until x2) {
        if (!filter.crop(image, x, y2)) {
          break@bottomEdge
        }
      }
    }
    ++y2

    if (x1 == x2 || y1 == y2) {
      // Nothing left after crop -- blank image
      return null
    }

    val width = x2 - x1
    val height = y2 - y1

    return Rectangle(x1, y1, width, height)
  }

  /**
   * Crops a given image with the given crop filter.
   *
   * @param image the image to be cropped
   * @param filter the filter determining whether a pixel is blank or not
   * @param initialCrop If not null, specifies a rectangle which contains an initial crop to continue. This can be used to crop an image
   *   where you already know about margins in the image
   * @param imageType the type of [BufferedImage] to create, or -1 to use the type of the original image
   * @return a cropped version of the source image, or null if the whole image was blank and cropping completely removed everything
   */
  @JvmStatic
  fun crop(
    image: BufferedImage?,
    filter: CropFilter,
    initialCrop: Rectangle?,
    imageType: Int,
  ): BufferedImage? {
    if (image == null) {
      return null
    }

    val cropBounds = getCropBounds(image, filter, initialCrop) ?: return null

    return getCroppedImage(image, cropBounds, imageType)
  }

  /**
   * Returns a given image cropped by the given rectangle. The original image is preserved.
   *
   * @param image the image to be cropped
   * @param cropBounds defines the part of the original image that is returned
   * @param imageType the type of [BufferedImage] to create, or -1 to use the type of the original image
   * @return the part of the original image located inside the `cropBounds` rectangle
   */
  @JvmStatic
  fun getCroppedImage(image: BufferedImage, cropBounds: Rectangle, imageType: Int): BufferedImage {
    val x1 = cropBounds.x
    val y1 = cropBounds.y
    val width = cropBounds.width
    val height = cropBounds.height
    val x2 = x1 + width
    val y2 = y1 + height

    var actualImageType = imageType
    if (actualImageType == -1) {
      actualImageType = image.type
    }
    if (actualImageType == BufferedImage.TYPE_CUSTOM) {
      actualImageType = BufferedImage.TYPE_INT_ARGB
    }

    if (x1 == 0 && y1 == 0 && width == image.width && height == image.height && actualImageType == image.type) {
      return image
    }

    // Create a cropped image.
    val cropped = BufferedImage(width, height, actualImageType)
    val g = cropped.graphics
    g.drawImage(image, 0, 0, width, height, x1, y1, x2, y2, null)

    g.dispose()

    return cropped
  }

  /**
   * Returns true if at least one pixel in the image is semi-transparent (alpha != 255)
   *
   * @param image the image to check
   * @return true if it has one or more non-opaque pixels
   */
  @JvmStatic
  fun isNonOpaque(image: BufferedImage): Boolean {
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        if (!isOpaquePixel(image, x, y)) {
          return true
        }
      }
    }
    return false
  }

  /** Checks if the image is fully transparent at the given coordinates. */
  @JvmStatic
  fun isTransparentPixel(image: BufferedImage, x: Int, y: Int): Boolean {
    return (image.getRGB(x, y) and ALPHA_MASK) == 0
  }

  /** Checks if the image is fully opaque at the given coordinates. */
  @JvmStatic
  fun isOpaquePixel(image: BufferedImage, x: Int, y: Int): Boolean {
    return (image.getRGB(x, y) and ALPHA_MASK) == ALPHA_MASK
  }

  /**
   * Clips the image by the ellipse inscribed into the image. The area outside the ellipse is filled with backgroundColor, or left
   * transparent if backgroundColor is null.
   */
  @JvmStatic
  fun ellipticalClip(image: BufferedImage, backgroundColor: Color?): BufferedImage {
    val mask = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    var g2 = mask.createGraphics()
    ImageUtil.applyQualityRenderingHints(g2)
    g2.fill(Area(Ellipse2D.Double(0.0, 0.0, image.width.toDouble(), image.height.toDouble())))
    g2.dispose()
    val shapedImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    g2 = shapedImage.createGraphics()
    ImageUtil.applyQualityRenderingHints(g2)
    g2.drawImage(image, 0, 0, null)
    g2.composite = AlphaComposite.getInstance(AlphaComposite.DST_IN)
    g2.drawImage(mask, 0, 0, null)
    if (backgroundColor != null) {
      g2.color = backgroundColor
      g2.composite = AlphaComposite.getInstance(AlphaComposite.DST_OVER)
      g2.fillRect(0, 0, image.width, image.height)
    }
    g2.dispose()
    return shapedImage
  }

  /** Creates a diff image between two images. Unchanged pixels are grayscale, changed pixels are red. */
  @Slow
  @JvmStatic
  fun createDiffImage(img1: BufferedImage, img2: BufferedImage): BufferedImage {
    require(img1.width == img2.width && img1.height == img2.height) { "Images must be of the same size" }
    val width = img1.width
    val height = img1.height
    val diffImg = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    // Use scanlines to process the image row-by-row. This is significantly faster than
    // pixel-by-pixel getRGB() calls while avoiding the memory spikes of loading the
    // full image into a single buffer.
    val row1 = IntArray(width)
    val row2 = IntArray(width)
    val diffRow = IntArray(width)
    val redRgb = Color.RED.rgb

    for (y in 0 until height) {
      img1.getRGB(0, y, width, 1, row1, 0, width)
      img2.getRGB(0, y, width, 1, row2, 0, width)

      for (x in 0 until width) {
        val rgb1 = row1[x]
        val rgb2 = row2[x]

        if (rgb1 == rgb2) {
          // Same pixel: convert to grayscale using bitwise operations (Rec. 601)
          val a = (rgb2 ushr 24) and 0xFF
          val r = (rgb2 ushr 16) and 0xFF
          val g = (rgb2 ushr 8) and 0xFF
          val b = rgb2 and 0xFF
          // Use Math.round for better accuracy during grayscale conversion.
          val gray = (r * 0.299 + g * 0.587 + b * 0.114).roundToInt()
          diffRow[x] = (a shl 24) or (gray shl 16) or (gray shl 8) or gray
        } else {
          // Different pixel: highlight in red
          diffRow[x] = redRgb
        }
      }
      diffImg.setRGB(0, y, width, 1, diffRow, 0, width)
    }
    return diffImg
  }

  /** Interface implemented by cropping functions that determine whether a pixel should be cropped or not. */
  fun interface CropFilter {
    /**
     * Returns true if the pixel should be cropped.
     *
     * @param image the image containing the pixel in question
     * @param x the x position of the pixel
     * @param y the y position of the pixel
     * @return true if the pixel should be cropped (for example, is blank)
     */
    fun crop(image: BufferedImage, x: Int, y: Int): Boolean
  }

  /** Utility function to convert from an Icon to a BufferedImage. */
  @JvmStatic
  fun iconToImage(icon: Icon): BufferedImage {
    if (icon is ImageIcon) {
      return ImageUtil.toBufferedImage(icon.image)
    }
    val w = icon.iconWidth
    val h = icon.iconHeight
    val image = ImageUtil.createImage(w, h, BufferedImage.TYPE_4BYTE_ABGR)
    val g = image.createGraphics()
    icon.paintIcon(null, g, 0, 0)
    g.dispose()
    return image
  }
}
