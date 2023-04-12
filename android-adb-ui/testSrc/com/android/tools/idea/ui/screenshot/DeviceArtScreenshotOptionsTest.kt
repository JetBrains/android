/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage

/**
 * Tests for [DeviceArtScreenshotOptions].
 */
class DeviceArtScreenshotOptionsTest {

  private val screenshotOptions = DeviceArtScreenshotOptions(serialNumber = "some serial number", deviceModel = null)

  @Before
  fun setup() {
    StudioFlags.PLAY_COMPATIBLE_WEAR_SCREENSHOTS_ENABLED.override(true)
  }

  @Test
  fun testCreateScreenshotImage() {
    val image = createImage(1080, 2400, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1080 x 2400, ..., density 560, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.PHONE)
    Truth.assertThat(screenshotImage.image.width).isEqualTo(1080)
    Truth.assertThat(screenshotImage.image.height).isEqualTo(2400)
    Truth.assertThat(screenshotImage.displaySize).isEqualTo(Dimension(1080, 2400))
    Truth.assertThat(screenshotImage.isRoundDisplay).isFalse()
    Truth.assertThat(screenshotImage.isTv).isFalse()
    Truth.assertThat(screenshotImage.isWear).isFalse()
  }

  @Test
  fun testCreateScreenshotImageForWatchRound() {
    val image = createImage(384, 384, Color.DARK_GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 384 x 384, ..., density 200, ..., FLAG_ROUND}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.WEAR)
    Truth.assertThat(screenshotImage.image.width).isEqualTo(384)
    Truth.assertThat(screenshotImage.image.height).isEqualTo(384)
    Truth.assertThat(screenshotImage.displaySize).isEqualTo(Dimension(384, 384))
    Truth.assertThat(screenshotImage.isRoundDisplay).isTrue()
    Truth.assertThat(screenshotImage.isTv).isFalse()
    Truth.assertThat(screenshotImage.isWear).isTrue()
  }

  @Test
  fun testCreateScreenshotImageForWatchSquare() {
    val image = createImage(384, 384, Color.DARK_GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 384 x 384, ..., density 200, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.WEAR)
    Truth.assertThat(screenshotImage.image.width).isEqualTo(384)
    Truth.assertThat(screenshotImage.image.height).isEqualTo(384)
    Truth.assertThat(screenshotImage.displaySize).isEqualTo(Dimension(384, 384))
    Truth.assertThat(screenshotImage.isRoundDisplay).isFalse()
    Truth.assertThat(screenshotImage.isTv).isFalse()
    Truth.assertThat(screenshotImage.isWear).isTrue()
  }

  @Test
  fun testGetFramingOptionsPhone() {
    val image = createImage(1080, 2340, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1080 x 2340, ..., density 420, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.PHONE)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    Truth.assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Generic Phone", "Generic Tablet")
  }

  @Test
  fun testGetFramingOptionsWatchRound() {
    val image = createImage(384, 384, Color.DARK_GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 384 x 384, ..., density 200, ..., FLAG_ROUND}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.WEAR)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    Truth.assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Watch Round")
  }

  @Test
  fun testGetFramingOptionsWatchRound_FlagDisabled() {
    StudioFlags.PLAY_COMPATIBLE_WEAR_SCREENSHOTS_ENABLED.override(false)
    val image = createImage(384, 384, Color.DARK_GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 384 x 384, ..., density 200, ..., FLAG_ROUND}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.WEAR)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    Truth.assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Watch Square", "Watch Round", "Generic Phone",
                                                                                     "Generic Tablet")
  }

  @Test
  fun testGetFramingOptionsWatchSquare() {
    val image = createImage(384, 384, Color.DARK_GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 384 x 384, ..., density 200, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.WEAR)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    Truth.assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Watch Square")
  }

  @Test
  fun testGetFramingOptionsWatchSquare_FlagDisabled() {
    StudioFlags.PLAY_COMPATIBLE_WEAR_SCREENSHOTS_ENABLED.override(false)
    val image = createImage(384, 384, Color.DARK_GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 384 x 384, ..., density 200, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.WEAR)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    Truth.assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Watch Square", "Watch Round", "Generic Phone",
                                                                                     "Generic Tablet")
  }

  private fun createImage(width: Int, height: Int, color: Color): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2 = image.createGraphics()
    g2.color = color
    g2.fillRect(0, 0, width, height)
    g2.dispose()
    return image
  }

}