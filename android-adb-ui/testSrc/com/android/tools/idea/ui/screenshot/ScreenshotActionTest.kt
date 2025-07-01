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
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.testutils.ImageDiffUtil.assertImageSimilar
import com.android.testutils.TestUtils.resolveWorkspacePathUnchecked
import com.android.testutils.waitForCondition
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.actions.executeAction
import com.android.tools.adtui.device.SkinDefinition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.findModelessDialog
import com.android.tools.idea.adblib.testing.FakeAdbSessionRule
import com.android.tools.idea.testing.WaitForIndexRule
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.override
import com.android.tools.idea.ui.DISPLAY_ID_KEY
import com.android.tools.idea.ui.DISPLAY_INFO_PROVIDER_KEY
import com.android.tools.idea.ui.DisplayInfoProvider
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper.CLOSE_EXIT_CODE
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TemporaryDirectory
import org.intellij.images.ui.ImageComponent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.seconds

/** Tests for [ScreenshotAction]. */
@RunsInEdt
class ScreenshotActionTest {

  private val projectRule = ProjectRule()
  private val fakeAdbRule = FakeAdbServerProviderRule { installDefaultCommandHandlers() }
  private val temporaryDirectoryRule = TemporaryDirectory()
  private val fakeAdbSessionRule = FakeAdbSessionRule(projectRule)

  @get:Rule
  val ruleChain = RuleChain(projectRule, WaitForIndexRule(projectRule), fakeAdbRule, temporaryDirectoryRule, fakeAdbSessionRule,
                            EdtRule(), HeadlessDialogRule())

  private val deviceServices = fakeAdbSessionRule.adbSession.deviceServices
  private val serialNumber = "123"
  private val device = DeviceSelector.fromSerialNumber(serialNumber)
  private val emptyByteBuffer = ByteBuffer.allocate(0)
  private val project get() = projectRule.project
  private val action = ScreenshotAction()

  @Before
  fun setUp() {
    service<DeviceScreenshotSettings>()::frameScreenshot.override(true, projectRule.disposable)
  }

  @After
  fun tearDown() {
    do {
      val dialog = findModelessDialog<ScreenshotViewer>()
      dialog?.close(CLOSE_EXIT_CODE)
    } while (dialog != null)
  }

  @Test
  fun testWithDisplayInfoProvider() {
    // Prepare.
    val testImage = createTestImage(1840, 2208, Color.CYAN)
    deviceServices.configureShellV2Command(device, "screencap -p", testImage.toPngBytes(), emptyByteBuffer, 0)
    deviceServices.configureShellCommand(device, "dumpsys display", getDumpsysOutput("PixelFoldRotated90"))

    val screenshotParameters = ScreenshotParameters(serialNumber, DeviceType.HANDHELD, "Pixel Fold")

    val displayInfoProvider = object: DisplayInfoProvider {

      override fun getIdsOfAllDisplays(): IntArray = intArrayOf(PRIMARY_DISPLAY_ID)

      override fun getDisplaySize(displayId: Int): Dimension {
        require(displayId == PRIMARY_DISPLAY_ID)
        return Dimension(2208, 1840)
      }

      override fun getDisplayOrientation(displayId: Int): Int {
        require(displayId == PRIMARY_DISPLAY_ID)
        return 1 // Landscape orientation.
      }

      override fun getScreenshotRotation(displayId: Int): Int {
        require(displayId == PRIMARY_DISPLAY_ID)
        return 0
      }

      override fun getSkin(displayId: Int): SkinDefinition? {
        require(displayId == PRIMARY_DISPLAY_ID)
        return null
      }
    }

    val dataSnapshotProvider = DataSnapshotProvider {
      it[ScreenshotParameters.DATA_KEY] = screenshotParameters
      it[DISPLAY_ID_KEY] = PRIMARY_DISPLAY_ID
      it[DISPLAY_INFO_PROVIDER_KEY] = displayInfoProvider
    }

    // Act.
    executeAction(action, project = project, extra = dataSnapshotProvider)

    // Assert.
    val screenshotViewer = waitForScreenshotViewer()
    val ui = FakeUi(screenshotViewer.rootPane)
    val imageComponent = ui.getComponent<ImageComponent>()
    waitForCondition(2.seconds) { imageComponent.document.value != null }
    assertMatchesGolden(imageComponent.document.value, "PixelFoldRotated90")
  }

  @Test
  fun testWithoutDisplayInfoProvider() {
    // Prepare.
    val testImage = createTestImage(1840, 2208, Color.CYAN)
    deviceServices.configureShellV2Command(device, "screencap -p", testImage.toPngBytes(), emptyByteBuffer, 0)
    deviceServices.configureShellCommand(device, "dumpsys display", getDumpsysOutput("PixelFoldRotated90"))

    val screenshotParameters = ScreenshotParameters(serialNumber, DeviceType.HANDHELD, "Pixel Fold")

    val dataSnapshotProvider = DataSnapshotProvider {
      it[ScreenshotParameters.DATA_KEY] = screenshotParameters
      it[DISPLAY_ID_KEY] = PRIMARY_DISPLAY_ID
    }

    // Act.
    executeAction(action, project = project, extra = dataSnapshotProvider)

    // Assert.
    val screenshotViewer = waitForScreenshotViewer()
    val ui = FakeUi(screenshotViewer.rootPane)
    val imageComponent = ui.getComponent<ImageComponent>()
    waitForCondition(2.seconds) { imageComponent.document.value != null }
    assertMatchesGolden(imageComponent.document.value, "PixelFoldRotated90")
  }

  private fun createTestImage(width: Int, height: Int, color: Color): BufferedImage {
    val image = ImageUtils.createDipImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.paint = color
    graphics.fillRect(0, 0, image.width, image.height)
    graphics.dispose()
    return image
  }

  private fun waitForScreenshotViewer(filter: (ScreenshotViewer) -> Boolean = { true }): ScreenshotViewer {
    var screenshotViewer: ScreenshotViewer? = null
    waitForCondition(2.seconds) {
      screenshotViewer = findScreenshotViewer(filter)
      screenshotViewer != null
    }
    return screenshotViewer!!
  }

  private fun findScreenshotViewer(filter: (ScreenshotViewer) -> Boolean = { true }): ScreenshotViewer? =
      findModelessDialog<ScreenshotViewer> { filter(it) }

  private fun assertMatchesGolden(image: BufferedImage, name: String) {
    assertImageSimilar(getGoldenFile(name), ImageUtils.scale(image, 0.125))
  }

  private fun getGoldenFile(name: String): Path =
      resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/$name.png")
}

private fun BufferedImage.toPngBytes(): ByteBuffer {
  val stream = ByteArrayOutputStream()
  ImageIO.write(this, "png", stream)
  return ByteBuffer.wrap(stream.toByteArray())
}

private fun getDumpsysOutput(filename: String): String =
    Files.readString(resolveWorkspacePathUnchecked("tools/adt/idea/android-adb-ui/testData/dumpsys/$filename.txt"))

private const val GOLDEN_FILE_PATH = "tools/adt/idea/android-adb-ui/testData/ScreenshotActionTest/golden"
