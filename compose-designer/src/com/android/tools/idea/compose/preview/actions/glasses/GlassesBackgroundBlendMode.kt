/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions.glasses

import com.android.tools.idea.common.scene.draw.HQ_RENDERING_HINTS
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.jetbrains.annotations.TestOnly

/**
 * Method to scale a buffered image to a given width and height. This method returns an image of
 * type BufferedImage.TYPE_INT_ARGB_PRE.
 */
private fun BufferedImage.scaleTo(width: Int, height: Int): BufferedImage {
  @Suppress(
    "UndesirableClassUsage"
  ) // We do not want HiDPI images, we want to maintain the same pixel size.
  val scaledImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)
  val bgG2d: Graphics2D = scaledImage.createGraphics()
  try {
    bgG2d.setRenderingHints(HQ_RENDERING_HINTS)
    bgG2d.drawImage(this, 0, 0, width, height, null)
  } finally {
    bgG2d.dispose()
  }
  return scaledImage
}

/** A class that applies a blend mode to a rendered image to simulate a glasses environment. */
internal class GlassesBackgroundBlendMode
private constructor(private val background: BufferedImage) {
  private val log = Logger.getInstance(GlassesBackgroundBlendMode::class.java)

  /**
   * Applies the blend mode to the given image.
   *
   * @param renderedImage the image to apply the blend mode to. The image must be of type
   *   [BufferedImage.TYPE_INT_ARGB_PRE].
   */
  fun applyBackground(renderedImage: BufferedImage) {
    require(renderedImage.type == BufferedImage.TYPE_INT_ARGB_PRE) {
      "Rendered image must support transparency"
    }

    // We need to scale the background so it's the same size as the rendered preview image, since
    // we're doing pixel-by-pixel blending.
    val targetWidth = renderedImage.width
    val targetHeight = renderedImage.height
    // scaledBackgroundImage is always of type BufferedImage.TYPE_INT_ARGB_PRE.
    val scaledBackgroundImage = background.scaleTo(targetWidth, targetHeight)

    // Blend both images using the screen blend mode
    val blendedImage = blendRenderedImageAndBackground(renderedImage, scaledBackgroundImage)

    // Modify the preview rendered image in-place by copying the blended image to it
    try {
      val g2d: Graphics2D = renderedImage.createGraphics()
      g2d.setRenderingHints(HQ_RENDERING_HINTS)
      try {
        g2d.drawImage(blendedImage, 0, 0, null)
      } finally {
        g2d.dispose()
      }
    } catch (e: Exception) {
      log.warn("Error applying screen blend in-place: ${e.message}")
    }
  }

  /**
   * Blends the rendered image and the background image using the screen blend mode.
   *
   * @param renderedImage the image.
   * @param scaledBackgroundImage the background image, scaled to the same size as the rendered
   *   image.
   * @param renderedImage the rendered image.
   */
  private fun blendRenderedImageAndBackground(
    renderedImage: BufferedImage,
    scaledBackgroundImage: BufferedImage,
  ): BufferedImage {
    require(
      renderedImage.width == scaledBackgroundImage.width &&
        renderedImage.height == scaledBackgroundImage.height
    ) {
      """
        Rendered image and background image must have the same dimensions.
        Rendered image width: ${renderedImage.width}, height: ${renderedImage.height}
        Background image width: ${scaledBackgroundImage.width}, height: ${scaledBackgroundImage.height}
      """
        .trimIndent()
    }
    val width = renderedImage.width
    val height = renderedImage.height
    @Suppress(
      "UndesirableClassUsage"
    ) // We do not want HiDPI images, we want to maintain the same pixel size.
    val blendedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

    for (y in 0 until height) {
      for (x in 0 until width) {
        // Extract the rendered image and background pixel at the given coordinate to use them in
        // the formulas below.
        val renderedImagePixel = renderedImage.getRGB(x, y)
        val backgroundPixel = scaledBackgroundImage.getRGB(x, y)

        // Extract RGB components for both images.
        val imgRed = (renderedImagePixel shr 16) and 0xFF
        val imgGreen = (renderedImagePixel shr 8) and 0xFF
        val imgBlue = renderedImagePixel and 0xFF

        val bgRed = (backgroundPixel shr 16) and 0xFF
        val bgGreen = (backgroundPixel shr 8) and 0xFF
        val bgBlue = backgroundPixel and 0xFF

        // Screen Blend. Formula: f(a,b) = 1 - (1 - a) * (1 - b), where a is foreground color and b
        // is background color. Taken from https://en.wikipedia.org/wiki/Blend_modes#Screen.
        // Calculations below are performed in 0-255 range.
        val blendedRed = (255 - ((255 - imgRed) * (255 - bgRed)) / 255)
        val blendedGreen = (255 - ((255 - imgGreen) * (255 - bgGreen)) / 255)
        val blendedBlue = (255 - ((255 - imgBlue) * (255 - bgBlue)) / 255)

        // Set the pixel color in the blended image
        val finalPixelValue =
          (0xFF shl 24) or (blendedRed shl 16) or (blendedGreen shl 8) or blendedBlue
        blendedImage.setRGB(x, y, finalPixelValue)
      }
    }
    return blendedImage
  }

  companion object {
    /**
     * Generates a BufferedImage from a given file path.
     *
     * @param fileName the name of the background included in the distribution.
     * @return A [BufferedImage] if the file is found and can be read, or null if an error occurs.
     */
    private fun loadBackgroundImage(fileName: String): BufferedImage? {
      val imageStream =
        GlassesBackgroundBlendMode::class
          .java
          .classLoader
          .getResourceAsStream("glassesPreview/$fileName")
      if (imageStream == null) {
        thisLogger().warn("Error: Background '$fileName' not found.")
        return null
      }

      try {
        return ImageIO.read(imageStream)
      } catch (e: Exception) {
        thisLogger().warn("Error reading image '$fileName'. Details: ${e.message}")
        return null
      }
    }

    /**
     * Returns a [GlassesBackgroundBlendMode] for a given [GlassesBackground] or null if the
     * background is not available or [GlassesBackground.NONE].
     *
     * @param mode the [GlassesBackground] to be used.
     */
    fun getInstance(mode: GlassesBackground): GlassesBackgroundBlendMode? {
      val image = loadBackgroundImage(mode.fileName ?: return null)
      return GlassesBackgroundBlendMode(image ?: return null)
    }

    @TestOnly
    fun getInstanceForTest(background: BufferedImage): GlassesBackgroundBlendMode {
      return GlassesBackgroundBlendMode(background)
    }
  }
}
