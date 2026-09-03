/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.device

import com.android.tools.adtui.ImageUtils.ALPHA_MASK
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.awt.image.DataBufferInt
import kotlin.math.max
import kotlin.math.min

/** Implements Screen blending, see https://en.wikipedia.org/wiki/Blend_modes. */
class ScreenBlender {

  private val screenBlendLookupTable =
    ByteArray(256 * 256).apply {
      for (bg in 0..255) {
        for (fg in 0..255) {
          this[(bg shl 8) or fg] = (255 - (((255 - bg) * (255 - fg)) / 255)).toByte()
        }
      }
    }

  /** Overlays [foreground] in the center of [background] using the screen blend algorithm. */
  @Suppress("UndesirableClassUsage")
  fun overlay(foreground: BufferedImage, background: BufferedImage): BufferedImage {
    val width = background.width
    val height = background.height
    val foregroundWidth = foreground.width
    val foregroundHeight = foreground.height
    val x0 = (width - foregroundWidth) / 2
    val y0 = (height - foregroundHeight) / 2
    val xStart = max(0, x0)
    val xEnd = min(width, x0 + foregroundWidth)
    val yStart = max(0, y0)
    val yEnd = min(height, y0 + foregroundHeight)

    val result = BufferedImage(width, height, TYPE_INT_ARGB)
    val compositePixels = (result.raster.dataBuffer as DataBufferInt).data
    val backgroundData = (background.raster.dataBuffer as DataBufferInt).data
    System.arraycopy(backgroundData, 0, compositePixels, 0, compositePixels.size)

    val foregroundData = (foreground.raster.dataBuffer as DataBufferInt).data

    for (y in yStart until yEnd) {
      val backgroundRowOffset = y * width
      val foregroundRowOffset = ((y - y0) * foregroundWidth) - x0
      for (x in xStart until xEnd) {
        val fgPixel = foregroundData[foregroundRowOffset + x]
        if ((fgPixel and 0xFFFFFF) == 0) continue // Black pixel.

        val bgPixel = compositePixels[backgroundRowOffset + x]

        val bgRed = (bgPixel ushr 16) and 0xFF
        val bgGreen = (bgPixel ushr 8) and 0xFF
        val bgBlue = bgPixel and 0xFF

        val fgRed = (fgPixel ushr 16) and 0xFF
        val fgGreen = (fgPixel ushr 8) and 0xFF
        val fgBlue = fgPixel and 0xFF

        val r = screenBlendLookupTable[(bgRed shl 8) or fgRed].toInt() and 0xFF
        val g = screenBlendLookupTable[(bgGreen shl 8) or fgGreen].toInt() and 0xFF
        val b = screenBlendLookupTable[(bgBlue shl 8) or fgBlue].toInt() and 0xFF

        compositePixels[backgroundRowOffset + x] = ALPHA_MASK or (r shl 16) or (g shl 8) or b
      }
    }

    return result
  }
}
