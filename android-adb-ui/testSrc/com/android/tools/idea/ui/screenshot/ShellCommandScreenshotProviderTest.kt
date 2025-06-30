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
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.testutils.ImageDiffUtil.assertImageSimilar
import com.android.testutils.TestUtils.resolveWorkspacePathUnchecked
import com.android.tools.adtui.ImageUtils
import com.android.tools.idea.adblib.testing.FakeAdbSessionRule
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
import java.nio.file.Files
import javax.imageio.ImageIO

/** Test for [ShellCommandScreenshotProvider]. */
internal class ShellCommandScreenshotProviderTest {

  private val projectRule = ProjectRule()
  private val fakeAdbSessionRule = FakeAdbSessionRule(projectRule)

  @get:Rule
  val rule = RuleChain(projectRule, fakeAdbSessionRule)

  private val deviceServices = fakeAdbSessionRule.adbSession.deviceServices
  private val serialNumber = "123"
  private val device = DeviceSelector.fromSerialNumber(serialNumber)
  private lateinit var screenshotProvider: ShellCommandScreenshotProvider
  private val emptyByteBuffer = ByteBuffer.allocate(0)
  private val project
    get() = projectRule.project

  @Before
  fun setUp() {
    deviceServices.configureShellCommand(device, "dumpsys display", getDumpsysOutput("AutomotiveWithDistantDisplays"))
  }

  @After
  fun tearDown() {
    if (::screenshotProvider.isInitialized) {
      Disposer.dispose(screenshotProvider)
    }
  }

  @Test
  fun testPrimaryDisplay() {
    val testImage = createTestImage(1080, 600, Color.RED)
    deviceServices.configureShellV2Command(device, "screencap -p", testImage.toPngBytes(), emptyByteBuffer, 0)
    val parameters = ScreenshotParameters(serialNumber, DeviceType.HANDHELD, "Pixel 9")
    screenshotProvider = ShellCommandScreenshotProvider(project, serialNumber, parameters.deviceType, parameters.deviceName, PRIMARY_DISPLAY_ID)
    val image = runBlocking { screenshotProvider.captureScreenshot() }
    assertImageSimilar("test image", testImage, image.image)
  }

  @Test
  fun testSecondaryDisplay() {
    val testImage = createTestImage(400, 600, Color.BLUE)
    deviceServices.configureShellV2Command(device, "screencap -p -d 4619827551948147201", testImage.toPngBytes(), emptyByteBuffer, 0)
    val parameters = ScreenshotParameters(serialNumber, DeviceType.HANDHELD, "Pixel 9")
    screenshotProvider = ShellCommandScreenshotProvider(project, serialNumber, parameters.deviceType, parameters.deviceName, 2)
    val image = runBlocking { screenshotProvider.captureScreenshot() }
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

private fun getDumpsysOutput(filename: String): String =
    Files.readString(resolveWorkspacePathUnchecked("tools/adt/idea/android-adb-ui/testData/dumpsys/$filename.txt"))
