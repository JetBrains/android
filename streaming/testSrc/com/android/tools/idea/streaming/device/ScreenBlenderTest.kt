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

import com.google.common.truth.Truth.assertThat
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import org.junit.Test

/** Tests for [ScreenBlender.overlay] in ScreenBlender.kt. */
class ScreenBlenderTest {

  @Test
  fun testOverlayPositioningAndBlending() {
    val backgroundWidth = 100
    val backgroundHeight = 80
    val foregroundWidth = 40
    val foregroundHeight = 20

    val background = BufferedImage(backgroundWidth, backgroundHeight, TYPE_INT_ARGB)
    val bgColors = Color(100, 150, 200)
    for (y in 0 until backgroundHeight) {
      for (x in 0 until backgroundWidth) {
        background.setRGB(x, y, bgColors.rgb)
      }
    }

    val foreground = BufferedImage(foregroundWidth, foregroundHeight, TYPE_INT_ARGB)
    val fgColors = Color(50, 100, 150)
    for (y in 0 until foregroundHeight) {
      for (x in 0 until foregroundWidth) {
        foreground.setRGB(x, y, fgColors.rgb)
      }
    }

    val result = ScreenBlender().overlay(foreground, background)
    assertThat(result.width).isEqualTo(backgroundWidth)
    assertThat(result.height).isEqualTo(backgroundHeight)

    val expectedX0 = (backgroundWidth - foregroundWidth) / 2 // 30
    val expectedY0 = (backgroundHeight - foregroundHeight) / 2 // 30

    val expectedRed = 255 - (((255 - 100) * (255 - 50)) / 255) // 131
    val expectedGreen = 255 - (((255 - 150) * (255 - 100)) / 255) // 192
    val expectedBlue = 255 - (((255 - 200) * (255 - 150)) / 255) // 233
    val expectedBlendedColor = Color(expectedRed, expectedGreen, expectedBlue)

    for (y in 0 until backgroundHeight) {
      for (x in 0 until backgroundWidth) {
        val rgb = Color(result.getRGB(x, y))
        val inX = x in (expectedX0 until (expectedX0 + foregroundWidth))
        val inY = y in (expectedY0 until (expectedY0 + foregroundHeight))
        if (inX && inY) {
          assertThat(rgb.red).isEqualTo(expectedBlendedColor.red)
          assertThat(rgb.green).isEqualTo(expectedBlendedColor.green)
          assertThat(rgb.blue).isEqualTo(expectedBlendedColor.blue)
        } else {
          assertThat(rgb.red).isEqualTo(bgColors.red)
          assertThat(rgb.green).isEqualTo(bgColors.green)
          assertThat(rgb.blue).isEqualTo(bgColors.blue)
        }
      }
    }
  }

  @Test
  fun testOverlayWithBlackForeground() {
    val background = BufferedImage(50, 50, TYPE_INT_ARGB)
    val bgColors = Color(120, 80, 40)
    for (y in 0 until 50) {
      for (x in 0 until 50) {
        background.setRGB(x, y, bgColors.rgb)
      }
    }

    val foreground = BufferedImage(20, 20, TYPE_INT_ARGB)
    val blackColor = Color(0, 0, 0)
    for (y in 0 until 20) {
      for (x in 0 until 20) {
        foreground.setRGB(x, y, blackColor.rgb)
      }
    }

    val result = ScreenBlender().overlay(foreground, background)
    for (y in 0 until 50) {
      for (x in 0 until 50) {
        val rgb = Color(result.getRGB(x, y))
        assertThat(rgb.red).isEqualTo(bgColors.red)
        assertThat(rgb.green).isEqualTo(bgColors.green)
        assertThat(rgb.blue).isEqualTo(bgColors.blue)
      }
    }
  }

  @Test
  fun testOverlayWithWhiteForeground() {
    val background = BufferedImage(50, 50, TYPE_INT_ARGB)
    val bgColors = Color(120, 80, 40)
    for (y in 0 until 50) {
      for (x in 0 until 50) {
        background.setRGB(x, y, bgColors.rgb)
      }
    }

    val foreground = BufferedImage(20, 20, TYPE_INT_ARGB)
    val whiteColor = Color(255, 255, 255)
    for (y in 0 until 20) {
      for (x in 0 until 20) {
        foreground.setRGB(x, y, whiteColor.rgb)
      }
    }

    val result = ScreenBlender().overlay(foreground, background)
    val x0 = (50 - 20) / 2
    val y0 = (50 - 20) / 2
    for (y in 0 until 50) {
      for (x in 0 until 50) {
        val rgb = Color(result.getRGB(x, y))
        val inRegion = (x in (x0 until (x0 + 20))) && (y in (y0 until (y0 + 20)))
        if (inRegion) {
          assertThat(rgb.red).isEqualTo(255)
          assertThat(rgb.green).isEqualTo(255)
          assertThat(rgb.blue).isEqualTo(255)
        } else {
          assertThat(rgb.red).isEqualTo(bgColors.red)
          assertThat(rgb.green).isEqualTo(bgColors.green)
          assertThat(rgb.blue).isEqualTo(bgColors.blue)
        }
      }
    }
  }

  @Test
  fun testOverlayForegroundLargerThanBackground() {
    val background = BufferedImage(30, 30, TYPE_INT_ARGB)
    val bgColors = Color(100, 100, 100)
    for (y in 0 until 30) {
      for (x in 0 until 30) {
        background.setRGB(x, y, bgColors.rgb)
      }
    }

    val foreground = BufferedImage(50, 50, TYPE_INT_ARGB)
    val fgColors = Color(200, 200, 200)
    for (y in 0 until 50) {
      for (x in 0 until 50) {
        foreground.setRGB(x, y, fgColors.rgb)
      }
    }

    val result = ScreenBlender().overlay(foreground, background)
    assertThat(result.width).isEqualTo(30)
    assertThat(result.height).isEqualTo(30)

    val expectedVal = 255 - (((255 - 100) * (255 - 200)) / 255) // 222
    for (y in 0 until 30) {
      for (x in 0 until 30) {
        val rgb = Color(result.getRGB(x, y))
        assertThat(rgb.red).isEqualTo(expectedVal)
        assertThat(rgb.green).isEqualTo(expectedVal)
        assertThat(rgb.blue).isEqualTo(expectedVal)
      }
    }
  }
}
