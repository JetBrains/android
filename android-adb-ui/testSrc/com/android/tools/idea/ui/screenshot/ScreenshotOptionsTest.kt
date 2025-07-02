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

import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.adblib.DevicePropertyNames
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.testutils.ImageDiffUtil.assertImageSimilar
import com.android.test.testutils.TestUtils
import com.android.tools.adtui.ImageUtils.scale
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.adtui.webp.WebpMetadata
import com.google.common.truth.Truth.assertThat
import icons.StudioIcons
import org.junit.Before
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Path

/** Tests for [ScreenshotParameters]. */
class ScreenshotOptionsTest {

  private val serialNumber = "serial number"

  @Before
  fun setUp() {
    WebpMetadata.ensureWebpRegistered()
  }

  @Test
  fun testGetFramingOptionsKnownPhone() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel 4 XL"))
    val screenshotOptions = ScreenshotParameters(serialNumber, DeviceType.HANDHELD, deviceProperties.model)
    val image = createImage(1440, 3040, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1440 x 3040, ..., density 560, ...}"
    val screenshotImage = ScreenshotImage(image, 0, screenshotOptions.deviceType, "Phone", PRIMARY_DISPLAY_ID, displayInfo)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions).containsExactly(DeviceFramingOption("Pixel 4 XL", SKIN_FOLDER.resolve("pixel_4_xl")))
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsUnknownPhone() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Samsung Galaxy S22"))
    val screenshotOptions = ScreenshotParameters(serialNumber, DeviceType.HANDHELD, deviceProperties.model)
    val image = createImage(1080, 2340, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1080 x 2340, ..., density 420, ...}"
    val screenshotImage = ScreenshotImage(image, 0, screenshotOptions.deviceType, "Phone", PRIMARY_DISPLAY_ID, displayInfo)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Generic Phone")
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsTablet() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Xiaomi Pad 5"))
    val screenshotOptions = ScreenshotParameters(serialNumber, DeviceType.HANDHELD, deviceProperties.model)
    val image = createImage(1600, 2560, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1600 x 2560, ..., density 280, ...}"
    val screenshotImage = ScreenshotImage(image, 0, screenshotOptions.deviceType, "Phone", PRIMARY_DISPLAY_ID, displayInfo)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Generic Tablet")
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testFramingFoldable() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel Fold"))
    val screenshotOptions = ScreenshotParameters(serialNumber, DeviceType.HANDHELD, deviceProperties.model)
    val image = createImage(1080, 2092, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1080 x 2092, ..., density 420, ...}"
    val screenshotImage = ScreenshotImage(image, 0, screenshotOptions.deviceType, "Phone", PRIMARY_DISPLAY_ID, displayInfo)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Pixel Fold")
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
    val framingOption = framingOptions[screenshotOptions.getDefaultFramingOption()]
    val decoratedImage = screenshotOptions.screenshotDecorator.decorate(screenshotImage, ScreenshotDecorationOption(framingOption))
    assertImageSimilar(getGoldenFile("FramingFoldable"), scale(decoratedImage, 0.125))
  }

  @Test
  fun testGetFramingOptionsAutomotiveMatchingAspectRatio() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "automotive"))
    val screenshotOptions = ScreenshotParameters(serialNumber, DeviceType.AUTOMOTIVE, deviceProperties.model)
    val image = createImage(1280, 960, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1280 x 960, ..., density 180, ...}"
    val screenshotImage = ScreenshotImage(image, 0, screenshotOptions.deviceType, "Phone", PRIMARY_DISPLAY_ID, displayInfo)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Automotive Large Portrait", "Generic Tablet")
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsAutomotiveGeneric() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "automotive"))
    val screenshotOptions = ScreenshotParameters(serialNumber, DeviceType.AUTOMOTIVE, deviceProperties.model)
    val image = createImage(1280, 768, Color.WHITE)
    val displayInfo = "DisplayDeviceInfo{..., 1280 x 768, ..., density 180, ...}"
    val screenshotImage = ScreenshotImage(image, 0, screenshotOptions.deviceType, "Phone", PRIMARY_DISPLAY_ID, displayInfo)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName))
        .containsExactly("Automotive Large Portrait", "Automotive Ultrawide", "Generic Tablet")
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsTv() {
    val screenshotOptions = ScreenshotParameters(serialNumber, DeviceType.TV, emptyDeviceProperties.model)
    val image = createImage(1920, 1080, Color.GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 1920 x 1080, ..., density 480, ...}"
    val screenshotImage = ScreenshotImage(image, 0, screenshotOptions.deviceType, "Phone", PRIMARY_DISPLAY_ID, displayInfo)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Television (1080p)")
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsWatch() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))
    val screenshotOptions = ScreenshotParameters(serialNumber, DeviceType.WEAR, deviceProperties.model)
    val image = createImage(384, 384, Color.DARK_GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 384 x 384, ..., density 200, ..., FLAG_ROUND}"
    val screenshotImage = ScreenshotImage(image, 0, screenshotOptions.deviceType, "Watch", PRIMARY_DISPLAY_ID, displayInfo)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Wear OS Small Round")
    assertThat(screenshotOptions.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsWatchSquare() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))
    val screenshotOptions = ScreenshotParameters(serialNumber, DeviceType.WEAR, deviceProperties.model)
    val image = createImage(384, 384, Color.DARK_GRAY)
    val displayInfo = "DisplayDeviceInfo{..., 384 x 384, ..., density 200, ...}"
    val screenshotImage = ScreenshotImage(image, 0, screenshotOptions.deviceType, "Watch", PRIMARY_DISPLAY_ID, displayInfo)
    val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
    assertThat(framingOptions).isEmpty()
  }

  private fun createImage(width: Int, height: Int, color: Color): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2 = image.createGraphics()
    g2.color = color
    g2.fillRect(0, 0, width, height)
    g2.dispose()
    return image
  }

  private fun getGoldenFile(name: String): Path =
      TestUtils.resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/${name}.png")
}

private fun createDeviceProperties(propertyMap: Map<String, String>): DeviceProperties {
  return DeviceProperties.buildForTest {
    readCommonProperties(propertyMap)
    icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
  }
}

private val emptyDeviceProperties: DeviceProperties = createDeviceProperties(mapOf())

private val SKIN_FOLDER = DeviceArtDescriptor.getBundledDescriptorsFolder()!!.toPath()

private const val GOLDEN_FILE_PATH = "tools/adt/idea/android-adb-ui/testData/ScreenshotOptionsTest/golden"
