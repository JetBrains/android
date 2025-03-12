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
package com.android.tools.idea.ui.screenshot

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.ImageUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

/** Tests for [ScreenshotImage]. */
class ScreenshotImageTest {

  @Test
  fun testRotatedAndScaled() {
    val image = createTestImage(100, 180, Color.BLUE)
    val screenshot = ScreenshotImage(image, 0, DeviceType.HANDHELD)
    val transformedScreenshot = screenshot.rotatedAndScaled(1, 0.5)
    assertThat(transformedScreenshot.width).isEqualTo(90)
    assertThat(transformedScreenshot.height).isEqualTo(50)
  }

  private fun createTestImage(width: Int, height: Int, color: Color): BufferedImage {
    val image = ImageUtils.createDipImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.paint = color
    graphics.fillRect(0, 0, image.width, image.height)
    graphics.dispose()
    return image
  }
}