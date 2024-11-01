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

import com.android.annotations.concurrency.Slow
import com.android.io.readImage
import com.android.io.writeImage
import com.android.tools.adtui.ImageUtils.getCropBounds
import com.android.tools.adtui.ImageUtils.getCroppedImage
import com.android.tools.adtui.ImageUtils.isTransparentPixel
import com.android.tools.adtui.ImageUtils.normalizeImage
import com.android.tools.adtui.util.rotatedByQuadrants
import com.android.tools.adtui.util.scaled
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.annotations.TestOnly
//import org.jetbrains.kotlin.utils.ThreadSafe
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/**
 * Description of AVD frame and mask.
 *
 * @param layout the layout corresponding to the default orientation of the virtual device display
 */
// TODO AS Ladybug Feature Drop Canary 5 - Find where is the ThreadSafe annotation moved
//@ThreadSafe
class SkinDefinition private constructor(val layout: SkinLayout) {
  /**
   * Creates a [SkinLayout] for the given display dimensions and rotation.
   *
   * @param displayWidth the width of the rotated display
   * @param displayHeight the height of the rotated display
   * @param displayOrientationQuadrants the orientation of the display in quadrants counterclockwise
   */
  fun createScaledLayout(displayWidth: Int, displayHeight: Int, displayOrientationQuadrants: Int): SkinLayout {
    if (displayOrientationQuadrants == 0 && displayWidth == layout.displaySize.width && displayHeight == layout.displaySize.height) {
      return layout // No rotation or scaling needed.
    }

    val rotatedFrameRect = layout.frameRectangle.rotatedByQuadrants(displayOrientationQuadrants, layout.displaySize)
    val rotatedDisplaySize = layout.displaySize.rotatedByQuadrants(displayOrientationQuadrants)
    val rotatedDisplayCornerSize = layout.displayCornerSize.rotatedByQuadrants(displayOrientationQuadrants)
    val scaleX = displayWidth.toDouble() / rotatedDisplaySize.width
    val scaleY = displayHeight.toDouble() / rotatedDisplaySize.height
    val displayCornerWidth = rotatedDisplayCornerSize.width.scaled(scaleX)
    val displayCornerHeight = rotatedDisplayCornerSize.height.scaled(scaleY)
    // To avoid visible seams between parts of the skin, scale the frame margins separately from the display.
    val frameX = rotatedFrameRect.x.scaled(scaleX)
    val frameY = rotatedFrameRect.y.scaled(scaleY)
    val frameWidth = -frameX + displayWidth + (rotatedFrameRect.right - rotatedDisplaySize.width).scaled(scaleX)
    val frameHeight = -frameY + displayHeight + (rotatedFrameRect.bottom - rotatedDisplaySize.height).scaled(scaleY)
    val frameRect = Rectangle(frameX, frameY, frameWidth, frameHeight)
    val imageTransformer = ImageTransformer(displayOrientationQuadrants, scaleX, scaleY)
    val frameImages = layout.frameImages.mapNotNull { it.rotatedAndScaled(imageTransformer) }.toList()
    val maskImages = layout.maskImages.mapNotNull { it.rotatedAndScaled(imageTransformer) }.toList()
    val buttons = layout.buttons.mapNotNull { it.image.rotatedAndScaled(imageTransformer)?.let { image -> SkinButton(it.keyName, image) } }
    return SkinLayout(Dimension(displayWidth, displayHeight), Dimension(displayCornerWidth, displayCornerHeight),
                      frameRect, frameImages, maskImages, buttons)
  }

  /**
   * Returns the frame dimensions for the given display orientation.
   *
   * @param displayRotationQuadrants the orientation of the display in quadrants counterclockwise
   * @param displaySize the size of the device display without rotation or scaling
   */
  fun getRotatedFrameSize(displayRotationQuadrants: Int, displaySize: Dimension = layout.displaySize): Dimension {
    val scaleX = displaySize.getWidth() / layout.displaySize.getWidth()
    val scaleY = displaySize.getHeight() / layout.displaySize.getHeight()
    val frameRect = layout.frameRectangle
    val size = if (scaleX == 1.0 && scaleY == 1.0) {
      frameRect.size
    }
    else {
      // For accuracy scale the frame margins separately from the display.
      Dimension(-frameRect.x.scaled(scaleX) + displaySize.width + (frameRect.right - layout.displaySize.width).scaled(scaleX),
                -frameRect.y.scaled(scaleY) + displaySize.height + (frameRect.bottom - layout.displaySize.height).scaled(scaleY))
    }

    return size.rotatedByQuadrants(displayRotationQuadrants)
  }

  private data class LayoutDescriptor(
    val displaySize: Dimension,
    val displayCornerRadius: Int,
    val frameRectangle: Rectangle,
    val part: Part,
  ) {

    private val imageLoader = ImageLoader()

    fun createLayout(): SkinLayout {
      val backgroundImages: List<AnchoredImage>
      val maskImages: List<AnchoredImage>
      val background = part.backgroundFile?.let { imageLoader.loadImage(it) }

      val mask = when {
        background != null && isTransparentNearCenterOfDisplay(background, displaySize, frameRectangle) -> {
          // The background image is transparent near the center of the display. Derive mask from the background image.
          background.cropped(Rectangle(-frameRectangle.x, -frameRectangle.y, displaySize.width, displaySize.height)).apply {
            // If running in a non-IDE environment and the mask file is specified in the layout but is absent on disk,
            // create the missing mask file.
            if (ApplicationManager.getApplication() == null && part.maskFile != null) {
              val maskFile = part.maskFile
              if (Files.notExists(maskFile)) {
                writeImage("WEBP", maskFile)
              }
            }
          }
        }
        part.maskFile != null -> imageLoader.loadImage(part.maskFile)
        else -> null
      }

      backgroundImages = background?.let { disassembleFrame(it, frameRectangle, displaySize) } ?: emptyList()
      maskImages = mask?.let { disassembleMask(it, displaySize) } ?: emptyList()

      val adjustedFrameRectangle = computeAdjustedFrameRectangle(backgroundImages, displaySize)
      // Create skin buttons. Reanchoring to the nearest display corner is done to reduce scaling inaccuracies later on.
      val buttons = part.buttons.mapNotNull { buttonDescriptor ->
        buttonDescriptor.createButton(imageLoader)?.let {
          SkinButton(it.keyName, it.image.translatedAndReanchored(frameRectangle.x, frameRectangle.y, displaySize))
        }
      }
      return SkinLayout(displaySize, Dimension(displayCornerRadius, displayCornerRadius), adjustedFrameRectangle,
                        backgroundImages, maskImages, buttons)
    }
  }

  private data class Part(val backgroundFile: Path?, val maskFile: Path?, val buttons: List<ButtonDescriptor>)

  private data class ButtonDescriptor(val keyName: String, val offset: Point, val imageFile: Path) {

    fun createButton(imageLoader: ImageLoader): SkinButton? {
      val image = imageLoader.loadImage(imageFile) ?: return null
      val anchoredImage = AnchoredImage(image, Dimension(image.width, image.height), AnchorPoint.TOP_LEFT, offset)
      return SkinButton(keyName, anchoredImage)
    }
  }

  private class ImageLoader {

    private val loadedImages = HashMap<Path, BufferedImage?>()

    fun loadImage(imageFile: Path): BufferedImage? {
      return loadedImages.computeIfAbsent(imageFile) { readImage(it) }
    }

    private fun readImage(file: Path): BufferedImage? {
      return try {
        normalizeImage(file.readImage())
      }
      catch (e: IOException) {
        val detail = if (Files.notExists(file)) "the file does not exist" else e.message ?: ""
        val separator = if (detail.isEmpty()) "" else " - "
        logger<SkinDefinition>().warn("Failed to read Emulator skin image $file$separator$detail")
        null
      }
    }
  }

  companion object {
    @Slow
    @JvmStatic
    fun createOrNull(skinFolder: Path): SkinDefinition? {
      try {
        return create(skinFolder)
      }
      catch (e: NoSuchFileException) {
        thisLogger().error("File not found: ${e.file}")
      }
      catch (e: IOException) {
        thisLogger().error(e)
      }
      catch (e: InvalidSkinException) {
        thisLogger().error(e.message)
      }
      return null
    }

    @Slow
    @Throws(IOException::class, InvalidSkinException::class)
    @JvmStatic
    fun create(skinFolder: Path): SkinDefinition {
      val layoutDescriptor = createLayoutDescriptor(skinFolder)
      return SkinDefinition(layoutDescriptor.createLayout())
    }

    @Slow
    @Throws(IOException::class, InvalidSkinException::class)
    @JvmStatic
    private fun createLayoutDescriptor(skinFolder: Path): LayoutDescriptor {
      val layoutFile = skinFolder.resolve("layout")
      val contents = Files.readAllBytes(layoutFile).toString(UTF_8)
      val skin = SkinLayoutDefinition.parseString(contents)
      var displayWidth = 0
      var displayHeight = 0
      var cornerRadius = 0
      // Process part nodes. The "onion" and "controls" nodes are ignored because they don't
      // contribute to the device frame appearance.
      val partsByName = mutableMapOf<String, Part>()
      val partNodes = skin.getNode("parts")?.children ?: throw InvalidSkinException("Missing \"parts\" element")
      for ((name, node) in partNodes.entries) {
        when (name) {
          "device", "primary" -> {
            if (displayWidth == 0 || displayHeight == 0) {
              displayWidth = node.getValue("display.width")?.toInt() ?: 0
              displayHeight = node.getValue("display.height")?.toInt() ?: 0
              cornerRadius = node.getValue("display.corner_radius")?.toInt() ?: 0
            }
          }

          "onion", "controls" -> continue
        }
        partsByName[name] = createPart(node, skinFolder)
      }

      if (displayWidth == 0 || displayHeight == 0) {
        throw InvalidSkinException("Missing or invalid display dimensions")
      }
      // Process layout nodes.
      val layoutNodes = skin.getNode("layouts")?.children ?: throw InvalidSkinException("Missing \"layouts\" element")
      layout@ for (layoutNode in layoutNodes.values) {
        val width = layoutNode.getValue("width")?.toInt() ?: continue
        val height = layoutNode.getValue("height")?.toInt() ?: continue
        var part: Part? = null
        var frameX = 0
        var frameY = 0
        var partX = 0
        var partY = 0
        for (subnode in layoutNode.children.values) {
          val x = subnode.getValue("x")?.toInt() ?: 0
          val y = subnode.getValue("y")?.toInt() ?: 0
          val name = subnode.getValue("name") ?: continue
          if (name == "device" || name == "primary") {
            val rotation = subnode.getValue("rotation")?.toInt() ?: 0
            if (rotation != 0) {
              continue@layout // The layout is rotated - ignore it.
            }
            frameX = -x
            frameY = -y
          }
          else {
            if (part == null) {
              part = partsByName[name]
              if (part != null) {
                partX = x
                partY = y
              }
            }
          }
        }

        if (part != null) {
          val frameRectangle = Rectangle(frameX + partX, frameY + partY, width, height)
          return LayoutDescriptor(Dimension(displayWidth, displayHeight), cornerRadius, frameRectangle, part)
        }
      }
      throw InvalidSkinException("Missing \"device\" part")
    }

    /**
     * Returns the rectangle rotated with the display according to [rotation].
     *
     * @param rotation the requested rotation
     * @param displaySize the display dimensions before rotation
     */
    @JvmStatic
    private fun Rectangle.rotatedByQuadrants(rotation: Int, displaySize: Dimension): Rectangle {
      return when (rotation) {
        1 -> Rectangle(y, displaySize.width - width - x, height, width)
        2 -> Rectangle(displaySize.width - width - x, displaySize.height - height - y, width, height)
        3 -> Rectangle(displaySize.height - height - y, x, height, width)
        else -> this
      }
    }

    @JvmStatic
    private fun createPart(partNode: SkinLayoutDefinition, skinFolder: Path): Part {
      val background = getReferencedFile(partNode, "background.image", skinFolder)
      val mask = getReferencedFile(partNode, "foreground.mask", skinFolder)
      val buttons = mutableListOf<ButtonDescriptor>()
      val buttonsNode = partNode.getNode("buttons")
      if (buttonsNode != null) {
        for ((buttonName, node) in buttonsNode.children) {
          val keyName = when (buttonName) {
            "back" -> "GoBack"
            "home" -> "GoHome"
            "power" -> "Power"
            "search" -> "Search"
            "soft-left" -> "Menu"
            "volume-down" -> "AudioVolumeDown"
            "volume-up" -> "AudioVolumeUp"
            else -> continue
          }
          val x = node.getValue("x")?.toInt() ?: continue
          val y = node.getValue("y")?.toInt() ?: continue
          val imageFile = getReferencedFile(node, "image", skinFolder) ?: continue
          buttons.add(ButtonDescriptor(keyName, Point(x, y), imageFile))
        }
      }

      return Part(background, mask, buttons)
    }

    @JvmStatic
    private fun getReferencedFile(node: SkinLayoutDefinition, propertyName: String, skinFolder: Path): Path? {
      val filename = node.getValue(propertyName) ?: return null
      return skinFolder.resolve(filename)
    }

    @JvmStatic
    private fun isTransparentNearCenterOfDisplay(image: BufferedImage, displaySize: Dimension, frameRectangle: Rectangle): Boolean =
      isTransparentPixel(image, displaySize.width / 2 - frameRectangle.x, displaySize.height / 2 - frameRectangle.y)

    /**
     * Crops the background image and breaks it into 8 pieces, 4 for sides and 4 for corners of the frame.
    internal */
    @JvmStatic
    private fun disassembleFrame(background: BufferedImage, frameRectangle: Rectangle, displaySize: Dimension): List<AnchoredImage> {
      // Display edges in coordinates of the cropped background image.
      val cropBounds = getCropBounds(background, null) ?: return emptyList()
      val displayLeft = -frameRectangle.x
      val displayRight = displayLeft + displaySize.width
      val displayTop = -frameRectangle.y
      val displayBottom = displayTop + displaySize.height
      // Thickness of the right and bottom sides of the frame.
      val marginLeft = displayLeft - cropBounds.x
      val marginRight = cropBounds.right - displayRight
      val marginTop = displayTop - cropBounds.y
      val marginBottom = cropBounds.bottom - displayBottom

      val images = mutableListOf<AnchoredImage>()
      if (marginRight > 0) {
        // Right side.
        val rect = Rectangle(displayRight, displayTop, marginRight, displaySize.height)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayRight, rect.y - displayTop)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.TOP_RIGHT, offset))
      }
      if (marginRight > 0 && marginTop > 0) {
        // Top right corner.
        val rect = Rectangle(displayRight, cropBounds.y, marginRight, marginTop)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayRight, rect.y - displayTop)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.TOP_RIGHT, offset))
      }
      if (marginTop > 0) {
        // Top side.
        val rect = Rectangle(displayLeft, cropBounds.y, displaySize.width, marginTop)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayLeft, rect.y - displayTop)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.TOP_LEFT, offset))
      }
      if (marginLeft > 0 && marginTop > 0) {
        // Top left corner.
        val rect = Rectangle(cropBounds.x, cropBounds.y, marginLeft, marginTop)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayLeft, rect.y - displayTop)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.TOP_LEFT, offset))
      }
      if (marginLeft > 0) {
        // Left side.
        val rect = Rectangle(cropBounds.x, displayTop, marginLeft, displaySize.height)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayLeft, rect.y - displayTop)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.TOP_LEFT, offset))
      }
      if (marginLeft > 0 && marginBottom > 0) {
        // Bottom left corner.
        val rect = Rectangle(cropBounds.x, displayBottom, marginLeft, marginBottom)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayLeft, rect.y - displayBottom)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.BOTTOM_LEFT, offset))
      }
      if (marginBottom > 0) {
        // Bottom side.
        val rect = Rectangle(displayLeft, displayBottom, displaySize.width, marginBottom)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayLeft, rect.y - displayBottom)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.BOTTOM_LEFT, offset))
      }
      if (marginRight > 0 && marginBottom > 0) {
        // Bottom right corner.
        val rect = Rectangle(displayRight, displayBottom, marginRight, marginBottom)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayRight, rect.y - displayBottom)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.BOTTOM_RIGHT, offset))
      }
      return images
    }

    /**
     * Breaks the background image into 8 pieces, 4 for sides and 4 for corners of the frame.
     * Each piece is cropped to remove the fully transparent part.
     */
    @JvmStatic
    private fun disassembleMask(mask: BufferedImage, displaySize: Dimension): List<AnchoredImage> {
      return cutHorizontally(mask, Rectangle(0, 0, mask.width, mask.height))
        .flatMap { cutVertically(mask, it) }
        .map { createAnchoredImage(mask, it, displaySize) }
        .toList()
    }

    @JvmStatic
    private fun cutHorizontally(image: BufferedImage, cropBounds: Rectangle): Sequence<Rectangle> {
      return sequence {
        var bounds = cropBounds
        outer@ while (true) {
          for (y in bounds.y until bounds.bottom) {
            if (isTransparentHorizontalLine(image, bounds.x, bounds.right, y)) {
              val piece = getCropBounds(image, Rectangle(bounds.x, cropBounds.y, bounds.width, y - bounds.y))
              piece?.let { yield(it) }
              bounds = getCropBounds(image, Rectangle(bounds.x, y + 1, cropBounds.width, bounds.height - y - 1)) ?: return@sequence
              continue@outer
            }
          }
          yield(bounds)
          return@sequence
        }
      }
    }

    @JvmStatic
    private fun cutVertically(image: BufferedImage, cropBounds: Rectangle): Sequence<Rectangle> {
      return sequence {
        var bounds = cropBounds
        outer@ while (true) {
          for (x in bounds.x until bounds.right) {
            if (isTransparentVerticalLine(image, x, bounds.y, bounds.bottom)) {
              val piece = getCropBounds(image, Rectangle(bounds.x, cropBounds.y, x - bounds.x, bounds.height))
              piece?.let { yield(it) }
              bounds = getCropBounds(image, Rectangle(x + 1, bounds.y, cropBounds.width - x - 1, bounds.height)) ?: return@sequence
              continue@outer
            }
          }
          yield(bounds)
          return@sequence
        }
      }
    }

    @JvmStatic
    private fun isTransparentHorizontalLine(image: BufferedImage, startX: Int, endX: Int, y: Int): Boolean {
      for (x in startX until endX) {
        if (!isTransparentPixel(image, x, y)) {
          return false
        }
      }
      return true
    }

    @JvmStatic
    private fun isTransparentVerticalLine(image: BufferedImage, x: Int, startY: Int, endY: Int): Boolean {
      for (y in startY until endY) {
        if (!isTransparentPixel(image, x, y)) {
          return false
        }
      }
      return true
    }

    @JvmStatic
    private fun createAnchoredImage(mask: BufferedImage, cropBounds: Rectangle, displaySize: Dimension): AnchoredImage {
      val anchorPoint =
        if (cropBounds.x > displaySize.width / 2) {
          if (cropBounds.y > displaySize.height / 2) {
            AnchorPoint.BOTTOM_RIGHT
          }
          else {
            AnchorPoint.TOP_RIGHT
          }
        }
        else {
          if (cropBounds.y > displaySize.height / 2) {
            AnchorPoint.BOTTOM_LEFT
          }
          else {
            AnchorPoint.TOP_LEFT
          }
        }
      val offset = Point(cropBounds.x - anchorPoint.x * displaySize.width, cropBounds.y - anchorPoint.y * displaySize.height)
      return AnchoredImage(mask.cropped(cropBounds), cropBounds.size, anchorPoint, offset)
    }

    @JvmStatic
    private fun computeAdjustedFrameRectangle(backgroundImages: Iterable<AnchoredImage>, displaySize: Dimension): Rectangle {
      var left = 0
      var top = 0
      var right = displaySize.width
      var bottom = displaySize.height
      for (image in backgroundImages) {
        left = left.coerceAtMost(image.offset.x + displaySize.width * image.anchorPoint.x)
        right = right.coerceAtLeast(image.offset.x + image.size.width + displaySize.width * image.anchorPoint.x)
        top = top.coerceAtMost(image.offset.y + displaySize.height * image.anchorPoint.y)
        bottom = bottom.coerceAtLeast(image.offset.y + image.size.height + displaySize.height * image.anchorPoint.y)
      }
      return Rectangle(left, top, right - left, bottom - top)
    }

    @JvmStatic
    private fun BufferedImage.cropped(cropBounds: Rectangle): BufferedImage =
        getCroppedImage(this, cropBounds, -1)

    @JvmStatic
    private fun getCropBounds(image: BufferedImage, initialCrop: Rectangle?): Rectangle? =
        getCropBounds(image, ::isTransparentPixel, initialCrop)

    @JvmStatic
    private val Rectangle.right
      get() = x + width

    @JvmStatic
    private val Rectangle.bottom
      get() = y + height

    @TestOnly
    @JvmStatic
    internal fun getBackgroundImageFile(skinFolder: Path): Path? = createLayoutDescriptor(skinFolder).part.backgroundFile
  }
}

class InvalidSkinException(message: String) : RuntimeException(message)