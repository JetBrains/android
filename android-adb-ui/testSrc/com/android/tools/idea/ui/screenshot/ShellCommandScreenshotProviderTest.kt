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

import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbSession
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.testutils.ImageDiffUtil.assertImageSimilar
import com.android.tools.adtui.ImageUtils
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.adblib.testing.TestAdbLibService
import com.android.tools.idea.testing.ProjectServiceRule
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.imageio.ImageIO

/** Test for [ShellCommandScreenshotProvider]. */
internal class ShellCommandScreenshotProviderTest {

  private val projectRule = ProjectRule()
  private val adbSession = FakeAdbSession()

  @get:Rule
  val rule = RuleChain(projectRule, ProjectServiceRule(projectRule, AdbLibService::class.java, TestAdbLibService(adbSession)))

  private val deviceServices = adbSession.deviceServices
  private val serialNumber = "123"
  private val device = DeviceSelector.fromSerialNumber(serialNumber)
  private lateinit var screenshotProvider: ShellCommandScreenshotProvider
  private val emptyByteBuffer = ByteBuffer.allocate(0)
  private val project
    get() = projectRule.project

  @Before
  fun setUp() {
    deviceServices.configureShellCommand(device, "dumpsys display", dumpsysOutput)
  }

  @After
  fun tearDown() {
    if (::screenshotProvider.isInitialized) {
      Disposer.dispose(screenshotProvider)
    }
    runBlocking { adbSession.closeAndJoin() }
  }

  @Test
  fun testPrimaryDisplay() {
    val testImage = createTestImage(1080, 600, Color.RED)
    deviceServices.configureShellV2Command(device, "screencap -p", testImage.toPngBytes(), emptyByteBuffer, 0)
    val options = ScreenshotOptions(serialNumber, "Pixel 9", DeviceType.HANDHELD, PRIMARY_DISPLAY_ID, null)
    screenshotProvider = ShellCommandScreenshotProvider(project, serialNumber, options)
    val image = screenshotProvider.captureScreenshot()
    assertImageSimilar("test image", testImage, image.image)
  }

  @Test
  fun testSecondaryDisplay() {
    val testImage = createTestImage(400, 600, Color.BLUE)
    deviceServices.configureShellV2Command(device, "screencap -p -d 4619827551948147201", testImage.toPngBytes(), emptyByteBuffer, 0)
    val options = ScreenshotOptions(serialNumber, "Pixel 9", DeviceType.HANDHELD, 2, null)
    screenshotProvider = ShellCommandScreenshotProvider(project, serialNumber, options)
    val image = screenshotProvider.captureScreenshot()
    assertImageSimilar("test image", testImage, image.image)
  }

  private fun createTestImage(width: Int, height: Int, color: Color): BufferedImage {
    val image = ImageUtils.createDipImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.paint = color
    graphics.fillRect(0, 0, image.width, image.height)
    graphics.dispose()
    return image
  }

  private fun BufferedImage.toPngBytes(): ByteBuffer {
    val stream = ByteArrayOutputStream()
    ImageIO.write(this, "png", stream)
    return ByteBuffer.wrap(stream.toByteArray())
  }
}

private val dumpsysOutput = """
  DisplayDeviceInfo{"Built-in Screen": uniqueId="local:4619827259835644672", 1080 x 600, modeId 1, defaultModeId 1, supportedModes [{id=1, width=1080, height=600, fps=60.000004, alternativeRefreshRates=[]}], colorMode 0, supportedColorModes [0], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, allmSupported false, gameContentTypeSupported false, density 120, 120.0 x 120.0 dpi, appVsyncOff 1000000, presDeadline 16666666, touch INTERNAL, rotation 0, type INTERNAL, address {port=0, model=0x401cec6a7a2b7b}, deviceProductInfo DeviceProductInfo{name=EMU_display_0, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, state ON, committedState ON, frameRateOverride , brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.39763778, FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY, FLAG_ROTATES_WITH_CONTENT, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, installOrientation 0}
    mCurrentLayerStack=0
    mPhysicalDisplayId=4619827259835644672

  DisplayDeviceInfo{"HDMI Screen": uniqueId="local:4619827551948147201", 400 x 600, modeId 2, defaultModeId 2, supportedModes [{id=2, width=400, height=600, fps=160.0, alternativeRefreshRates=[]}], colorMode 0, supportedColorModes [0], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, allmSupported false, gameContentTypeSupported false, density 120, 120.0 x 120.0 dpi, appVsyncOff 2000000, presDeadline 6250000, touch EXTERNAL, rotation 0, type EXTERNAL, address {port=1, model=0x401cecae7d6e8a}, deviceProductInfo DeviceProductInfo{name=EMU_display_1, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, state ON, committedState ON, frameRateOverride , brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.5, FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_PRIVATE, FLAG_PRESENTATION, FLAG_OWN_CONTENT_ONLY, installOrientation 0}
    mCurrentLayerStack=2
    mPhysicalDisplayId=4619827551948147201
"""