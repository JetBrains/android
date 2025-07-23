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
import com.android.sdklib.deviceprovisioner.DeviceType.HANDHELD
import com.android.sdklib.internal.avd.AvdInfo
import com.android.testutils.ImageDiffUtil.assertImageSimilar
import com.android.testutils.TestUtils
import com.android.tools.adtui.ImageUtils.scale
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.adtui.webp.WebpMetadata
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RuleChain
import icons.StudioIcons
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.Color
import java.awt.Color.WHITE
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.file.Path

/** Tests for [ScreenshotParameters]. */
class ScreenshotParametersTest {

  private val serialNumber = "serial number"

  @get:Rule
  val rule = RuleChain(ApplicationRule())

  @Before
  fun setUp() {
    WebpMetadata.ensureWebpRegistered()
  }

  @Test
  fun testGetFramingOptionsKnownPhone() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel 4 XL"))
    val screenshotParameters = ScreenshotParameters(serialNumber, HANDHELD, deviceProperties.model)
    val image = createImage(1440, 3040, WHITE)
    val screenshotImage =
        ScreenshotImage(image, 0, screenshotParameters.deviceType, "Phone", PRIMARY_DISPLAY_ID, Dimension(1440, 3040), 560)
    val framingOptions = screenshotParameters.getFramingOptions(screenshotImage)
    assertThat(framingOptions).containsExactly(DeviceFramingOption("Pixel 4 XL", SKIN_FOLDER.resolve("pixel_4_xl")))
    assertThat(screenshotParameters.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsUnknownPhone() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Samsung Galaxy S22"))
    val screenshotParameters = ScreenshotParameters(serialNumber, HANDHELD, deviceProperties.model)
    val image = createImage(1080, 2340, WHITE)
    val screenshotImage =
        ScreenshotImage(image, 0, screenshotParameters.deviceType, "Phone", PRIMARY_DISPLAY_ID, Dimension(1080, 2340), 420)
    val framingOptions = screenshotParameters.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Pixel 4a", "Pixel 5", "Generic Phone")
    assertThat(screenshotParameters.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsTablet() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Xiaomi Pad 5"))
    val screenshotParameters = ScreenshotParameters(serialNumber, HANDHELD, deviceProperties.model)
    val image = createImage(1600, 2560, WHITE)
    val screenshotImage =
        ScreenshotImage(image, 0, screenshotParameters.deviceType, "Phone", PRIMARY_DISPLAY_ID, Dimension(1600, 2560), 280)
    val framingOptions = screenshotParameters.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Generic Tablet")
    assertThat(screenshotParameters.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testFramingFoldable() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel Fold"))
    val screenshotParameters = ScreenshotParameters(serialNumber, HANDHELD, deviceProperties.model)
    val image = createImage(1080, 2092, WHITE)
    val screenshotImage =
        ScreenshotImage(image, 0, screenshotParameters.deviceType, "Phone", PRIMARY_DISPLAY_ID, Dimension(1080, 2092), 420)
    val framingOptions = screenshotParameters.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Pixel Fold")
    assertThat(screenshotParameters.getDefaultFramingOption()).isEqualTo(0)
    val framingOption = framingOptions[screenshotParameters.getDefaultFramingOption()]
    val decoratedImage = screenshotParameters.screenshotDecorator.decorate(screenshotImage, ScreenshotDecorationOption(framingOption))
    assertImageSimilar(getGoldenFile("FramingFoldable"), scale(decoratedImage, 0.125))
  }

  @Test
  fun testGetFramingOptionsAutomotiveMatchingAspectRatio() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "automotive"))
    val screenshotParameters = ScreenshotParameters(serialNumber, DeviceType.AUTOMOTIVE, deviceProperties.model)
    val image = createImage(960, 1280, WHITE)
    val screenshotImage = ScreenshotImage(image, 0, screenshotParameters.deviceType, "Supercar", PRIMARY_DISPLAY_ID, Dimension(960, 1280), 180)
    val framingOptions = screenshotParameters.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Automotive Large Portrait", "Generic Tablet")
    assertThat(screenshotParameters.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsAutomotiveGeneric() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "automotive"))
    val screenshotParameters = ScreenshotParameters(serialNumber, DeviceType.AUTOMOTIVE, deviceProperties.model)
    val image = createImage(1280, 768, WHITE)
    val screenshotImage = ScreenshotImage(image, 0, screenshotParameters.deviceType, "Supercar", PRIMARY_DISPLAY_ID, Dimension(1280, 768), 180)
    val framingOptions = screenshotParameters.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName))
        .containsExactly("Generic Tablet")
    assertThat(screenshotParameters.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsTv() {
    val screenshotParameters = ScreenshotParameters(serialNumber, DeviceType.TV, emptyDeviceProperties.model)
    val image = createImage(1920, 1080, Color.GRAY)
    val screenshotImage =
        ScreenshotImage(image, 0, screenshotParameters.deviceType, "TV", PRIMARY_DISPLAY_ID, Dimension(1920, 1080), 480)
    val framingOptions = screenshotParameters.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Television (1080p)")
    assertThat(screenshotParameters.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsWatch() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))
    val screenshotParameters = ScreenshotParameters(serialNumber, DeviceType.WEAR, deviceProperties.model)
    val image = createImage(384, 384, Color.DARK_GRAY)
    val screenshotImage = ScreenshotImage(image, 0, screenshotParameters.deviceType, "Watch", PRIMARY_DISPLAY_ID, Dimension(384, 384), 200,
                                          isRoundDisplay = true)
    val framingOptions = screenshotParameters.getFramingOptions(screenshotImage)
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Wear OS Small Round")
    assertThat(screenshotParameters.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun testGetFramingOptionsWatchSquare() {
    val deviceProperties = createDeviceProperties(mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))
    val screenshotParameters = ScreenshotParameters(serialNumber, DeviceType.WEAR, deviceProperties.model)
    val image = createImage(384, 384, Color.DARK_GRAY)
    val screenshotImage = ScreenshotImage(image, 0, screenshotParameters.deviceType, "Watch", PRIMARY_DISPLAY_ID, Dimension(384, 384), 200)
    val framingOptions = screenshotParameters.getFramingOptions(screenshotImage)
    assertThat(framingOptions).isEmpty()
  }

  @Test
  fun constructFromAvdFolder_folderNotFound() {
    val avdFolder = Path.of("test.avd")
    val avdManager = TestAvdManagerConnection(null)

    val screenshotParameters = ScreenshotParameters(serialNumber, HANDHELD, avdFolder, avdManager)

    val framingOptions = screenshotParameters.getFramingOptions(createScreenshot(width = 1080, height = 2600))
    assertThat(framingOptions.map(FramingOption::displayName)).containsExactly("Generic Phone")
    assertThat(screenshotParameters.getDefaultFramingOption()).isEqualTo(0)
  }

  @Test
  fun constructFromAvdFolder_missingDeviceName() {
    val avdFolder = Path.of("test.avd")
    val avdManager = TestAvdManagerConnection(
      mapOf(
        "skin.path" to "/skins/my-skin",
      )
    )

    val screenshotParameters = ScreenshotParameters(serialNumber, HANDHELD, avdFolder, avdManager)

    assertThat(screenshotParameters.deviceName).isEqualTo("Unknown")
  }

  @Test
  fun constructFromAvdFolder() {
    val avdFolder = Path.of("test.avd")
    val avdManager = TestAvdManagerConnection(
      mapOf(
        "avd.ini.displayname" to "My Device",
        "skin.path" to "/skins/my-skin",
      )
    )

    val screenshotParameters = ScreenshotParameters(serialNumber, HANDHELD, avdFolder, avdManager)

    assertThat(screenshotParameters.deviceName).isEqualTo("My Device")
    val framingOptions = screenshotParameters.getFramingOptions(createScreenshot())
    assertThat(framingOptions).containsExactly(DeviceFramingOption("Show Device Frame", Path.of("/skins/my-skin")))
    assertThat(screenshotParameters.getDefaultFramingOption()).isEqualTo(0)
  }

  private fun getGoldenFile(name: String): Path =
      TestUtils.resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/$name.png")

  private class TestAvdManagerConnection(private val properties: Map<String, String>?)
    : AvdManagerConnection(null, null, Dispatchers.Unconfined) {

    override fun findAvdWithFolder(avdFolder: Path): AvdInfo? {
      if (properties == null) {
        return null
      }
      val mockAvdInfo = mock<AvdInfo>()
      whenever(mockAvdInfo.properties).thenReturn(properties)
      return mockAvdInfo
    }
  }
}

private fun createDeviceProperties(propertyMap: Map<String, String>): DeviceProperties {
  return DeviceProperties.buildForTest {
    readCommonProperties(propertyMap)
    icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
  }
}

private val emptyDeviceProperties: DeviceProperties = createDeviceProperties(mapOf())

private val SKIN_FOLDER = DeviceArtDescriptor.getBundledDescriptorsFolder()!!.toPath()

private const val GOLDEN_FILE_PATH = "tools/adt/idea/android-adb-ui/testData/ScreenshotParametersTest/golden"

private fun createScreenshot(
  deviceName: String = "Phone",
  deviceType: DeviceType = HANDHELD,
  width: Int = 1440,
  height: Int = 3040,
  density: Int = 560,
  color: Color = WHITE
): ScreenshotImage {
  val image = createImage(width, height, color)
  return ScreenshotImage(image, 0, deviceType, deviceName, PRIMARY_DISPLAY_ID, Dimension(width, height), density)
}

private fun createImage(width: Int, height: Int, color: Color): BufferedImage {
  val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
  val g2 = image.createGraphics()
  g2.color = color
  g2.fillRect(0, 0, width, height)
  g2.dispose()
  return image
}
