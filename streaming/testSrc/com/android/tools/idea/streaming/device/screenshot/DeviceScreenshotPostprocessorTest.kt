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
package com.android.tools.idea.streaming.device.screenshot

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.adtui.webp.WebpMetadata
import com.android.tools.idea.ui.screenshot.ScreenshotImage
import org.junit.Before
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.nio.file.Path

/**
 * Tests for [DeviceScreenshotPostprocessor].
 */
class DeviceScreenshotPostprocessorTest {

  @Before
  fun setUp() {
    WebpMetadata.ensureWebpRegistered()
  }

  private val postprocessor = DeviceScreenshotPostprocessor()

  @Test
  fun testSkinFrame() {
    val screenshotImage = ScreenshotImage(createImage(1080, 2400, Color.WHITE), 0, "", false)
    val skinFolder = DeviceArtDescriptor.getBundledDescriptorsFolder()!!.toPath().resolve("pixel_6")
    val framedImage = postprocessor.addFrame(screenshotImage, DeviceFramingOption("Pixel 6", skinFolder), null)
    assertImageSimilar("SkinFrame", framedImage)
  }

  @Test
  fun testDeviceArtFrame() {
    val screenshotImage = ScreenshotImage(createImage(1080, 2400, Color.WHITE), 0, "", false)
    val artDescriptor = DeviceArtDescriptor.getDescriptors(null).find { it.id == "phone" }!!
    val framedImage = postprocessor.addFrame(screenshotImage, DeviceFramingOption(artDescriptor), null)
    assertImageSimilar("DeviceArtFrame", framedImage)
  }

  @Test
  fun testCircularClip() {
    val screenshotImage = ScreenshotImage(createImage(400, 400, Color.CYAN), 0, "DisplayDeviceInfo{..., FLAG_ROUND}", false)
    val framedImage = postprocessor.addFrame(screenshotImage, null, null)
    assertImageSimilar("CircularClip", framedImage)
  }

  private fun createImage(width: Int, height: Int, color: Color): BufferedImage {
    val image = BufferedImage(width, height, TYPE_INT_ARGB)
    val g2 = image.createGraphics()
    g2.color = color
    g2.fillRect(0, 0, width, height)
    g2.dispose()
    return image
  }

  private fun assertImageSimilar(name: String, image: BufferedImage) =
    ImageDiffUtil.assertImageSimilar(getGoldenFile(name), ImageUtils.scale(image, 0.125))

  private fun getGoldenFile(name: String): Path =
    TestUtils.resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/${name}.png")
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/DeviceScreenshotTest/golden"
