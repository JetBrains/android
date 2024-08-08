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

import com.android.adblib.DevicePropertyNames
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.adtui.webp.WebpMetadata
import com.android.tools.idea.streaming.device.DeviceView
import com.android.tools.idea.streaming.device.createDeviceConfiguration
import com.android.tools.idea.streaming.device.emptyDeviceConfiguration
import com.android.tools.idea.ui.screenshot.FramingOption
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage

/**
 * Tests for [DeviceScreenshotOptions].
 */
class DeviceScreenshotOptionsTest {

  private val serialNumber = "serial number"
  private val deviceView: DeviceView = mock()

  @Before
  fun setUp() {
    WebpMetadata.ensureWebpRegistered()
    whenever(deviceView.displayOrientationQuadrants).thenReturn(0)
    whenever(deviceView.displayOrientationCorrectionQuadrants).thenReturn(0)
  }

  @Test
  fun testScreenshotViewerOptionsProperty() {
    assertThat(DeviceScreenshotOptions(serialNumber, emptyDeviceConfiguration, deviceView).screenshotViewerOptions).isEmpty()
  }

  @Test
  fun testScreenshotPostprocessorProperty() {
    assertThat(DeviceScreenshotOptions(serialNumber, emptyDeviceConfiguration, deviceView).screenshotDecorator)
        .isInstanceOf(DeviceScreenshotDecorator::class.java)
  }

  @Test
  fun testCreateScreenshotImage() {
    whenever(deviceView.displayOrientationQuadrants).thenReturn(0)
    whenever(deviceView.displayOrientationCorrectionQuadrants).thenReturn(1)
    val screenshotOptions = DeviceScreenshotOptions(serialNumber, emptyDeviceConfiguration, deviceView)
    val image = createImage(1080, 2400, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1080 x 2400, ..., density 560, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.HANDHELD)
    assertThat(screenshotImage.image.width).isEqualTo(2400)
    assertThat(screenshotImage.image.height).isEqualTo(1080)
    assertThat(screenshotImage.displaySize).isEqualTo(Dimension(1080, 2400))
    assertThat(screenshotImage.isRoundDisplay).isFalse()
    assertThat(screenshotImage.isTv).isFalse()
    assertThat(screenshotImage.isWear).isFalse()
  }

  @Test
  fun testGetFramingOptionsKnownPhone() {
    val deviceConfiguration = createDeviceConfiguration(mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel 4 XL"))
    val screenshotOptions = DeviceScreenshotOptions(serialNumber, deviceConfiguration, deviceView)
    val image = createImage(1440, 3040, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1440 x 3040, ..., density 560, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.HANDHELD)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions).containsExactly(DeviceFramingOption("Pixel 4 XL", SKIN_FOLDER.resolve("pixel_4_xl")))
    assertThat(screenshotOptions.getDefaultFramingOption(framingOptions, screenshotImage)).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsUnknownPhone() {
    val deviceConfiguration = createDeviceConfiguration(mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Samsung Galaxy S22"))
    val screenshotOptions = DeviceScreenshotOptions(serialNumber, deviceConfiguration, deviceView)
    val image = createImage(1080, 2340, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1080 x 2340, ..., density 420, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.HANDHELD)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Generic Phone")
    assertThat(screenshotOptions.getDefaultFramingOption(framingOptions, screenshotImage)).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsTablet() {
    val deviceConfiguration = createDeviceConfiguration(mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Xiaomi Pad 5"))
    val screenshotOptions = DeviceScreenshotOptions(serialNumber, deviceConfiguration, deviceView)
    val image = createImage(1600, 2560, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1600 x 2560, ..., density 280, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.HANDHELD)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Generic Tablet")
    assertThat(screenshotOptions.getDefaultFramingOption(framingOptions, screenshotImage)).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsAutomotiveMatchingAspectRatio() {
    val deviceConfiguration = createDeviceConfiguration(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "automotive"))
    val screenshotOptions = DeviceScreenshotOptions(serialNumber, deviceConfiguration, deviceView)
    val image = createImage(1280, 960, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1280 x 960, ..., density 180, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.HANDHELD)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly(
      "Automotive (1080p landscape)",
      "Automotive Large Portrait",
      "Automotive",
      "Generic Tablet")
    assertThat(screenshotOptions.getDefaultFramingOption(framingOptions, screenshotImage)).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsAutomotiveGeneric() {
    val deviceConfiguration = createDeviceConfiguration(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "automotive"))
    val screenshotOptions = DeviceScreenshotOptions(serialNumber, deviceConfiguration, deviceView)
    val image = createImage(1280, 768, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1280 x 768, ..., density 180, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.HANDHELD)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Automotive (1080p landscape)", "Generic Tablet")
    assertThat(screenshotOptions.getDefaultFramingOption(framingOptions, screenshotImage)).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsTv() {
    val screenshotOptions = DeviceScreenshotOptions(serialNumber, emptyDeviceConfiguration, deviceView)
    val image = createImage(1920, 1080, Color.GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 1920 x 1080, ..., density 480, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.TV)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Television (1080p)")
    assertThat(screenshotOptions.getDefaultFramingOption(framingOptions, screenshotImage)).isEqualTo(0)
    assertThat(screenshotImage.isTv).isTrue()
  }

  @Test
  fun testGetFramingOptionsWatch() {
    val deviceConfiguration = createDeviceConfiguration(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))
    val screenshotOptions = DeviceScreenshotOptions(serialNumber, deviceConfiguration, deviceView)
    val image = createImage(384, 384, Color.DARK_GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 384 x 384, ..., density 200, ..., FLAG_ROUND}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.WEAR)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Wear OS Small Round")
    assertThat(screenshotOptions.getDefaultFramingOption(framingOptions, screenshotImage)).isEqualTo(0)
    assertThat(screenshotImage.isWear).isTrue()
  }

  @Test
  fun testGetFramingOptionsWatchSquare() {
    val deviceConfiguration = createDeviceConfiguration(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))
    val screenshotOptions = DeviceScreenshotOptions(serialNumber, deviceConfiguration, deviceView)
    val image = createImage(384, 384, Color.DARK_GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 384 x 384, ..., density 200, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.WEAR)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Wear OS Square")
    assertThat(screenshotOptions.getDefaultFramingOption(framingOptions, screenshotImage)).isEqualTo(0)
    assertThat(screenshotImage.isWear).isTrue()
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

private val SKIN_FOLDER = DeviceArtDescriptor.getBundledDescriptorsFolder()!!.toPath()

