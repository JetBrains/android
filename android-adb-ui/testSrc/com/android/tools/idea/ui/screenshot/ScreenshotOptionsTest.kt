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
package com.android.tools.idea.ui.screenshot

import com.android.adblib.DevicePropertyNames
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.adtui.webp.WebpMetadata
import com.android.tools.idea.ui.screenshot.ScreenshotAction.ScreenshotRotation
import com.google.common.truth.Truth.assertThat
import icons.StudioIcons
import org.junit.Before
import org.junit.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage

/** Tests for [ScreenshotOptions]. */
class ScreenshotOptionsTest {

  private val serialNumber = "serial number"

  @Before
  fun setUp() {
    WebpMetadata.ensureWebpRegistered()
  }

  @Test
  fun testScreenshotViewerOptionsPropertyWithoutOrientationInfo() {
    assertThat(createScreenshotOptions(emptyDeviceProperties).screenshotViewerOptions)
        .containsExactly(ScreenshotViewer.Option.ALLOW_IMAGE_ROTATION)
  }

  @Test
  fun testScreenshotViewerOptionsPropertyWithOrientationInfo() {
    assertThat(createScreenshotOptions(emptyDeviceProperties, ScreenshotRotation(0, 0)).screenshotViewerOptions).isEmpty()
  }

  @Test
  fun testCreateScreenshotImage() {
    val screenshotOptions = createScreenshotOptions(emptyDeviceProperties, ScreenshotRotation(0, 1))
    val image = createImage(1080, 2400, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1080 x 2400, ..., density 560, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.HANDHELD)
    assertThat(screenshotImage.image.width).isEqualTo(2400)
    assertThat(screenshotImage.image.height).isEqualTo(1080)
    assertThat(screenshotImage.displaySize).isEqualTo(Dimension(1080, 2400))
    assertThat(screenshotImage.isRoundDisplay).isFalse()
  }

  @Test
  fun testGetFramingOptionsKnownPhone() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel 4 XL"))
    val screenshotOptions = createScreenshotOptions(deviceProperties)
    val image = createImage(1440, 3040, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1440 x 3040, ..., density 560, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.HANDHELD)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions).containsExactly(DeviceFramingOption("Pixel 4 XL", SKIN_FOLDER.resolve("pixel_4_xl")))
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsUnknownPhone() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Samsung Galaxy S22"))
    val screenshotOptions = createScreenshotOptions(deviceProperties)
    val image = createImage(1080, 2340, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1080 x 2340, ..., density 420, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.HANDHELD)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Generic Phone")
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsTablet() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Xiaomi Pad 5"))
    val screenshotOptions = createScreenshotOptions(deviceProperties)
    val image = createImage(1600, 2560, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1600 x 2560, ..., density 280, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.HANDHELD)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Generic Tablet")
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsAutomotiveMatchingAspectRatio() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "automotive"))
    val screenshotOptions = createScreenshotOptions(deviceProperties)
    val image = createImage(1280, 960, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1280 x 960, ..., density 180, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.AUTOMOTIVE)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly(
      "Automotive (1080p landscape)",
      "Automotive Large Portrait",
      "Automotive",
      "Generic Tablet")
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsAutomotiveGeneric() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "automotive"))
    val screenshotOptions = createScreenshotOptions(deviceProperties)
    val image = createImage(1280, 768, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1280 x 768, ..., density 180, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.AUTOMOTIVE)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Automotive (1080p landscape)", "Generic Tablet")
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsTv() {
    val screenshotOptions = createScreenshotOptions(emptyDeviceProperties)
    val image = createImage(1920, 1080, Color.GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 1920 x 1080, ..., density 480, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.TV)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Television (1080p)")
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsWatch() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))
    val screenshotOptions = createScreenshotOptions(deviceProperties)
    val image = createImage(384, 384, Color.DARK_GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 384 x 384, ..., density 200, ..., FLAG_ROUND}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.WEAR)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Wear OS Small Round")
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsWatchSquare() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))
    val screenshotOptions = createScreenshotOptions(deviceProperties)
    val image = createImage(384, 384, Color.DARK_GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 384 x 384, ..., density 200, ...}"
    val screenshotImage = screenshotOptions.createScreenshotImage(image, displayInfo, DeviceType.WEAR)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Wear OS Square")
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  private fun createImage(width: Int, height: Int, color: Color): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2 = image.createGraphics()
    g2.color = color
    g2.fillRect(0, 0, width, height)
    g2.dispose()
    return image
  }

  private fun createScreenshotOptions(deviceProperties: DeviceProperties, screenshotRotation: ScreenshotRotation? = null) =
      ScreenshotOptions(serialNumber, deviceProperties.model, screenshotRotation?.let { { it } })
}

private fun createDeviceProperties(propertyMap: Map<String, String>): DeviceProperties {
  return DeviceProperties.buildForTest {
    readCommonProperties(propertyMap)
    icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
  }
}

private val emptyDeviceProperties: DeviceProperties = createDeviceProperties(mapOf())

private val SKIN_FOLDER = DeviceArtDescriptor.getBundledDescriptorsFolder()!!.toPath()

