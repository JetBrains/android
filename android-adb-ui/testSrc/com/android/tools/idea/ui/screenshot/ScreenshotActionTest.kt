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
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
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
    service<ScreenshotConfiguration>()::frameScreenshot.override(true, projectRule.disposable)
  }

  @After
  fun tearDown() {
    do {
      val dialog = findModelessDialog<ScreenshotViewer>()
      dialog?.close(CLOSE_EXIT_CODE)
    } while (dialog != null)
  }

  @Ignore("b/428704440")
  @Test
  fun testWithDisplayInfoProvider() {
    // Prepare.
    val testImage = createTestImage(1840, 2208, Color.CYAN)
    deviceServices.configureShellV2Command(device, "screencap -p", testImage.toPngBytes(), emptyByteBuffer, 0)
    deviceServices.configureShellCommand(device, "dumpsys display", DUMPSYS_OUTPUT)

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

private const val GOLDEN_FILE_PATH = "tools/adt/idea/android-adb-ui/testData/ScreenshotActionTest/golden"

private const val DUMPSYS_OUTPUT = """
  DisplayDeviceInfo{"Inner Display": uniqueId="local:4619827677550801152", 2208 x 1840, modeId 2, renderFrameRate 120.00001, hasArrSupport false, frameRateCategoryRate FrameRateCategoryRate {normal=60.0, high=90.0}, supportedRefreshRates [60.0, 120.00001], defaultModeId 2, userPreferredModeId -1, supportedModes [{id=1, width=2208, height=1840, fps=60.0, vsync=60.0, synthetic=false, alternativeRefreshRates=[120.00001], supportedHdrTypes=[2, 3, 4]}, {id=2, width=2208, height=1840, fps=120.00001, vsync=120.00001, synthetic=false, alternativeRefreshRates=[60.0], supportedHdrTypes=[2, 3, 4]}], colorMode 0, supportedColorModes [0, 7, 9], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[2, 3, 4], mMaxLuminance=1000.0, mMaxAverageLuminance=120.0, mMinLuminance=5.0E-4}, isForceSdr false, allmSupported false, gameContentTypeSupported false, density 420, 378.94 x 379.967 dpi, appVsyncOff 6233332, presDeadline 11500000, touch INTERNAL, rotation 0, type INTERNAL, address {port=0, model=0x401ceccbbbeef1}, deviceProductInfo DeviceProductInfo{name=Common Panel, manufacturerPnpId=GGL, productId=0, modelYear=null, manufactureDate=ManufactureDate{week=1, year=1990}, connectionToSinkType=1}, state ON, committedState ON, frameRateOverride , brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.138, brightnessDim 0.05, hdrSdrRatio 1.0, roundedCorners RoundedCorners{[RoundedCorner{position=TopLeft, radius=52, center=Point(52, 52)}, RoundedCorner{position=TopRight, radius=52, center=Point(2156, 52)}, RoundedCorner{position=BottomRight, radius=48, center=Point(2160, 1792)}, RoundedCorner{position=BottomLeft, radius=48, center=Point(48, 1792)}]}, FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY, FLAG_ROTATES_WITH_CONTENT, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_TRUSTED, installOrientation 1, displayShape DisplayShape{ spec=1581572949 displayWidth=2208 displayHeight=1840 physicalPixelDisplaySizeRatio=1.0 rotation=0 offsetX=0 offsetY=0 scale=1.0}}
    mCurrentLayerStack=0
    mCurrentFlags=1
    mCurrentOrientation=1
    mPhysicalDisplayId=4619827677550801152
  DisplayDeviceInfo{"Outer Display": uniqueId="local:4619827677550801153", 1080 x 2092, modeId 4, renderFrameRate 120.00001, hasArrSupport false, frameRateCategoryRate FrameRateCategoryRate {normal=60.0, high=90.0}, supportedRefreshRates [60.0, 120.00001], defaultModeId 3, userPreferredModeId -1, supportedModes [{id=3, width=1080, height=2092, fps=60.0, vsync=60.0, synthetic=false, alternativeRefreshRates=[120.00001], supportedHdrTypes=[2, 3, 4]}, {id=4, width=1080, height=2092, fps=120.00001, vsync=120.00001, synthetic=false, alternativeRefreshRates=[60.0], supportedHdrTypes=[2, 3, 4]}], colorMode 0, supportedColorModes [0, 7, 9], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[2, 3, 4], mMaxLuminance=1000.0, mMaxAverageLuminance=120.0, mMinLuminance=5.0E-4}, isForceSdr false, allmSupported false, gameContentTypeSupported false, density 420, 409.432 x 408.744 dpi, appVsyncOff 6233332, presDeadline 11500000, cutout DisplayCutout{insets=Rect(0, 133 - 0, 0) waterfall=Insets{left=0, top=0, right=0, bottom=0} boundingRect={Bounds=[Rect(0, 0 - 0, 0), Rect(503, 0 - 577, 133), Rect(0, 0 - 0, 0), Rect(0, 0 - 0, 0)]} cutoutPathParserInfo={CutoutPathParserInfo{displayWidth=1080 displayHeight=2092 physicalDisplayWidth=1080 physicalDisplayHeight=2092 density={2.625} cutoutSpec={m 576.2,66.53 a 36.5,36.5 0 0 1 -36.5,36.5 36.5,36.5 0 0 1 -36.5,-36.5 36.5,36.5 0 0 1 36.5,-36.5 36.5,36.5 0 0 1 36.5,36.5 z @left} rotation={0} scale={1.0} physicalPixelDisplaySizeRatio={1.0}}} sideOverrides={}}, touch INTERNAL, rotation 0, type INTERNAL, address {port=1, model=0x401ceccbbbeef1}, deviceProductInfo DeviceProductInfo{name=Common Panel, manufacturerPnpId=GGL, productId=0, modelYear=null, manufactureDate=ManufactureDate{week=1, year=1990}, connectionToSinkType=1}, state OFF, committedState OFF, frameRateOverride , brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.11519199, brightnessDim 0.05, hdrSdrRatio 1.0, roundedCorners RoundedCorners{[RoundedCorner{position=TopLeft, radius=91, center=Point(91, 91)}, RoundedCorner{position=TopRight, radius=91, center=Point(989, 91)}, RoundedCorner{position=BottomRight, radius=91, center=Point(989, 2001)}, RoundedCorner{position=BottomLeft, radius=91, center=Point(91, 2001)}]}, FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY, FLAG_ROTATES_WITH_CONTENT, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_TRUSTED, installOrientation 0, displayShape DisplayShape{ spec=-901299633 displayWidth=1080 displayHeight=2092 physicalPixelDisplaySizeRatio=1.0 rotation=0 offsetX=0 offsetY=0 scale=1.0}}
    mCurrentLayerStack=-1
    mCurrentFlags=0
    mCurrentOrientation=0
    mPhysicalDisplayId=4619827677550801153
"""
