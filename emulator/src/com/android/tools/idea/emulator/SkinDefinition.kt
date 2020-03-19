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

import com.android.annotations.concurrency.Slow
import com.android.emulator.control.Rotation.SkinRotation
import com.android.emulator.control.Rotation.SkinRotation.LANDSCAPE
import com.android.emulator.control.Rotation.SkinRotation.REVERSE_LANDSCAPE
import com.android.emulator.control.Rotation.SkinRotation.REVERSE_PORTRAIT
import com.android.tools.adtui.ImageUtils.rotateByQuadrantsAndScale
import com.android.tools.idea.avdmanager.SkinLayoutDefinition
import com.android.tools.idea.emulator.ScaledSkinLayout.AnchoredImage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.ImageUtil
import org.jetbrains.kotlin.utils.ThreadSafe
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlin.math.min

/**
 * Description of AVD frame and mask.
 *
 * @param primaryLayout the layout corresponding to the default orientation of the virtual device display
 * @param rotatedLayout the optional layout corresponding to the virtual device display rotated by
 *    90 degrees counterclockwise
 */
@ThreadSafe
internal class SkinDefinition private constructor(
  private val primaryLayout: Layout,
  private val rotatedLayout: Layout?
) {
  private val images: MutableMap<URL, BufferedImage> = ContainerUtil.createConcurrentSoftValueMap()

  private fun getImage(file: URL): BufferedImage? {
    val image = images.computeIfAbsent(file) {
      try {
        return@computeIfAbsent ImageIO.read(file)
      }
      catch (e: IOException) {
        logger.warn("Failed to read Emulator skin image $file")
      }
      return@computeIfAbsent NULL_IMAGE
    }
    return if (image == NULL_IMAGE) null else image
  }

  /**
   * Returns the Emulator display size such that the Emulator skin fits into the [width] by [height] rectangle.
   *
   * @param width the width of the available screen area
   * @param height the height of the available screen area
   * @param rotation the orientation of the display
   */
  fun getScaledDisplaySize(width: Int, height: Int, rotation: SkinRotation): Dimension {
    val layout: Layout
    if (rotation.is90Degrees) {
      if (rotatedLayout == null) {
        val skinSize = primaryLayout.skinSize
        val displayRect = primaryLayout.displayRect
        val scale = min(width.toDouble() / skinSize.height, height.toDouble() / skinSize.width)
        return Dimension((displayRect.height * scale).toInt(), (displayRect.width * scale).toInt())
      }
      layout = rotatedLayout
    }
    else {
      layout = primaryLayout
    }

    val skinSize = layout.skinSize
    val displayRect = layout.displayRect
    val scale = min(width.toDouble() / skinSize.width, height.toDouble() / skinSize.height)
    return Dimension((displayRect.width * scale).toInt(), (displayRect.height * scale).toInt())
  }

  /**
   * Creates a [ScaledSkinLayout] for the given display dimensions and rotation.
   *
   * @param displayWidth the width of the rotated display
   * @param displayHeight the height of the rotated display
   * @param displayRotation the orientation of the display
   */
  @Slow
  fun createScaledLayout(displayWidth: Int, displayHeight: Int, displayRotation: SkinRotation): ScaledSkinLayout {
    val layout: Layout
    val rotation: SkinRotation
    if (displayRotation.is90Degrees && rotatedLayout != null) {
      layout = rotatedLayout
      rotation = displayRotation.decrementedBy90Degrees()
    }
    else {
      layout = primaryLayout
      rotation = displayRotation
    }

    val rotatedSkinSize = rotated(layout.skinSize, rotation)
    val rotatedDisplayRect = rotated(layout.displayRect, rotation, layout.skinSize)
    val scale = min(displayWidth.toDouble() / rotatedDisplayRect.width, displayHeight.toDouble() / rotatedDisplayRect.height)
    val skinSize = Dimension(rotatedSkinSize.width.scaleUp(scale), rotatedSkinSize.height.scaleUp(scale))
    val displayRect = Rectangle(rotatedDisplayRect.x.scale(scale), rotatedDisplayRect.y.scale(scale), displayWidth, displayHeight)
    val part = layout.part
    val background =
        getTransformedPartImage(part.backgroundFile, rotation, scale, layout.partOffset.x, layout.partOffset.y, layout.skinSize)
    val mask = getTransformedPartImage(part.maskFile, rotation, scale, layout.displayRect.x, layout.displayRect.y, layout.skinSize)

    return ScaledSkinLayout(skinSize, displayRect, background, mask)
  }

  private fun getTransformedPartImage(imageUrl: URL?,
                                      rotation: SkinRotation,
                                      scale: Double,
                                      layoutPartOffsetX: Int,
                                      layoutPartOffsetY: Int,
                                      skinSize: Dimension): AnchoredImage? {
    if (imageUrl == null) {
      return null
    }

    val image = getImage(imageUrl) ?: return null
    val rect = Rectangle(layoutPartOffsetX, layoutPartOffsetY, image.width, image.height)
    val rotatedRect = rotated(rect, rotation, skinSize)
    val x = rotatedRect.x.scale(scale)
    val y = rotatedRect.y.scale(scale)
    val transformedImage =
        rotateByQuadrantsAndScale(image, rotation.ordinal, rotatedRect.width.scale(scale), rotatedRect.height.scale(scale))
    return AnchoredImage(transformedImage, x, y)
  }

  companion object {
    @Slow
    @JvmStatic
    fun getSkinFolder(avdFolder: Path): Path? {
      val configFile = avdFolder.resolve("config.ini")
      try {
        for (line in Files.readAllLines(configFile)) {
          if (line.startsWith("skin.path=")) {
            return Paths.get(line.substring("skin.path=".length))
          }
        }
      }
      catch (e: IOException) {
        logger.error(e)
      }
      return null
    }

    @Slow
    @JvmStatic
    fun create(skinFolder: Path): SkinDefinition? {
      try {
        val layoutFile = skinFolder.resolve("layout") ?: return null
        val contents = Files.readAllBytes(layoutFile).toString(UTF_8)
        val skin = SkinLayoutDefinition.parseString(contents)
        var displayWidth = 0
        var displayHeight = 0
        // Process part nodes. The "onion" and "controls" nodes are ignored because they don't
        // contribute to the device frame appearance.
        val partsByName: MutableMap<String, Part> = hashMapOf()
        val partNodes = skin.getNode("parts")?.children ?: return null
        for ((name, node) in partNodes.entries) {
          if (name == "device") {
            displayWidth = node.getValue("display.width")?.toInt() ?: return null
            displayHeight = node.getValue("display.height")?.toInt() ?: return null
          }
          else if (name != "onion" && name != "controls") {
            partsByName[name] = createPart(node, skinFolder)
          }
        }

        // Process layout nodes.
        var primaryLayout: Layout? = null
        var rotatedLayout: Layout? = null
        val layoutNodes = skin.getNode("layouts")?.children ?: return null
        for (layoutNode in layoutNodes.values) {
          val width = layoutNode.getValue("width")?.toInt() ?: continue
          val height = layoutNode.getValue("height")?.toInt() ?: continue
          var rotation = 0
          var part: Part? = null
          var partOffset: Point? = null
          var displayX = 0
          var displayY = 0
          for (subnode in layoutNode.children.values) {
            val x = subnode.getValue("x")?.toInt() ?: 0
            val y = subnode.getValue("y")?.toInt() ?: 0
            val name = subnode.getValue("name") ?: continue
            if (name == "device") {
              rotation = subnode.getValue("rotation")?.toInt() ?: 0
              displayX = x
              displayY = y
            }
            else {
              if (part == null) {
                part = partsByName[name]
                if (part != null) {
                  partOffset = Point(x, y)
                }
              }
            }
          }

          if (part != null && partOffset != null) {
            val skinSize = Dimension(width, height)
            // The rotation value here corresponds to the number of quadrants of clockwise rotation.
            // The number 3 below corresponds to 270-degree clockwise rotation, or 90-degree
            // counterclockwise one. Other non-zero rotation values are not considered because they
            // never appear in the skin layout files.
            when (rotation) {
              0 -> primaryLayout = Layout(skinSize, Rectangle(displayX, displayY, displayWidth, displayHeight), part, partOffset)
              3 -> rotatedLayout = Layout(rotated(skinSize, LANDSCAPE),
                                          rotated(Rectangle(displayX, displayY, displayWidth, displayHeight), LANDSCAPE, skinSize),
                                          part, partOffset)
            }
          }
        }

        if (primaryLayout != null) {
          return SkinDefinition(primaryLayout, rotatedLayout)
        }
      }
      catch (e: IOException) {
        logger.error(e)
      }
      return null
    }

    /**
     * Returns the given rectangle rotated with the skin according to [rotation].
     *
     * @param rect the original rectangle
     * @param rotation the requested rotation
     * @param skinSize the original skin size
     */
    private fun rotated(rect: Rectangle, rotation: SkinRotation, skinSize: Dimension): Rectangle {
      return when (rotation) {
        LANDSCAPE -> Rectangle(rect.y, skinSize.width - rect.width - rect.x, rect.height, rect.width)
        REVERSE_PORTRAIT -> Rectangle(skinSize.width - rect.width - rect.x, skinSize.height - rect.height - rect.y,
                                      rect.width, rect.height)
        REVERSE_LANDSCAPE -> Rectangle(skinSize.height - rect.height - rect.y, rect.x, rect.height, rect.width)
        else -> rect
      }
    }

    /**
     * Returns the given dimensions rotated according to [rotation].
     */
    private fun rotated(dimension: Dimension, rotation: SkinRotation): Dimension {
      return when (rotation) {
        LANDSCAPE, REVERSE_LANDSCAPE -> Dimension(dimension.height, dimension.width)
        else -> dimension
      }
    }

    @JvmStatic
    private fun createPart(partNode: SkinLayoutDefinition, skinFolder: Path): Part {
      val background = getReferencedFile(partNode, "background.image", skinFolder)
      val mask = getReferencedFile(partNode, "foreground.mask", skinFolder)
      return Part(background, mask)
    }

    @JvmStatic
    private fun getReferencedFile(node: SkinLayoutDefinition, propertyName: String, skinFolder: Path): URL? {
      val filename = node.getValue(propertyName) ?: return null
      return skinFolder.resolve(filename).toUri().toURL()
    }

    @JvmStatic
    private val NULL_IMAGE = ImageUtil.createImage(1, 1, BufferedImage.TYPE_INT_ARGB)

    @JvmStatic
    private val logger
      get() = Logger.getInstance(SkinDefinition::class.java)
  }

  private data class Part(val backgroundFile: URL?, val maskFile: URL?)

  private data class Layout(val skinSize: Dimension, val displayRect: Rectangle, val part: Part, val partOffset: Point)
}