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

import com.android.SdkConstants
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.findModelessDialog
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.ui.DialogWrapper.CLOSE_EXIT_CODE
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.EDT
import org.intellij.images.ui.ImageComponent
import org.intellij.images.ui.ImageComponentDecorator
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.EnumSet
import javax.swing.JComboBox
import javax.swing.UIManager

private const val DISPLAY_INFO_PHONE =
  "DisplayDeviceInfo{\"Built-in Screen\": uniqueId=\"local:4619827259835644672\", 1080 x 2400, modeId 1, defaultModeId 1," +
  " supportedModes [{id=1, width=1080, height=2400, fps=60.000004, alternativeRefreshRates=[]}], colorMode 0, supportedColorModes [0]," +
  " hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}," +
  " allmSupported false, gameContentTypeSupported false, density 420, 420.0 x 420.0 dpi, appVsyncOff 1000000, presDeadline 16666666," +
  " cutout DisplayCutout{insets=Rect(0, 136 - 0, 0) waterfall=Insets{left=0, top=0, right=0, bottom=0}" +
  " boundingRect={Bounds=[Rect(0, 0 - 0, 0), Rect(0, 0 - 136, 136), Rect(0, 0 - 0, 0), Rect(0, 0 - 0, 0)]}" +
  " cutoutPathParserInfo={CutoutPathParserInfo{displayWidth=1080 displayHeight=2400 stableDisplayHeight=1080 stableDisplayHeight=2400" +
  " density={2.625} cutoutSpec={M 128,83 A 44,44 0 0 1 84,127 44,44 0 0 1 40,83 44,44 0 0 1 84,39 44,44 0 0 1 128,83 Z @left}" +
  " rotation={0} scale={1.0} physicalPixelDisplaySizeRatio={1.0}}}}, touch INTERNAL, rotation 0, type INTERNAL," +
  " address {port=0, model=0x401cec6a7a2b7b}," +
  " deviceProductInfo DeviceProductInfo{name=EMU_display_0, manufacturerPnpId=GGL, productId=1, modelYear=null," +
  " manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, state ON, frameRateOverride , brightnessMinimum 0.0," +
  " brightnessMaximum 1.0, brightnessDefault 0.39763778," +
  " FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY, FLAG_ROTATES_WITH_CONTENT, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, installOrientation 0}"

private const val DISPLAY_INFO_WATCH =
  "DisplayDeviceInfo{\"Built-in Screen\": uniqueId=\"local:8141603649153536\", 454 x 454, modeId 1, defaultModeId 1," +
  " supportedModes [{id=1, width=454, height=454, fps=60.000004}], colorMode 0, supportedColorModes [0]," +
  " HdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}," +
  " allmSupported false, gameContentTypeSupported false, density 320, 320.0 x 320.0 dpi, appVsyncOff 1000000, presDeadline 16666666," +
  " touch INTERNAL, rotation 0, type INTERNAL, address {port=0, model=0x1cecbed168ea}," +
  " deviceProductInfo DeviceProductInfo{name=EMU_display_0, manufacturerPnpId=GGL, productId=1, modelYear=null," +
  " manufactureDate=ManufactureDate{week=27, year=2006}, relativeAddress=null}, state ON," +
  " FLAG_DEFAULT_DISPLAY, FLAG_ROTATES_WITH_CONTENT, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_ROUND}"

private const val DISPLAY_INFO_WATCH_SQUARE =
  "DisplayDeviceInfo{\"Built-in Screen\": uniqueId=\"local:8141603649153536\", 454 x 454, modeId 1, defaultModeId 1," +
  " supportedModes [{id=1, width=454, height=454, fps=60.000004}], colorMode 0, supportedColorModes [0]," +
  " HdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}," +
  " allmSupported false, gameContentTypeSupported false, density 320, 320.0 x 320.0 dpi, appVsyncOff 1000000, presDeadline 16666666," +
  " touch INTERNAL, rotation 0, type INTERNAL, address {port=0, model=0x1cecbed168ea}," +
  " deviceProductInfo DeviceProductInfo{name=EMU_display_0, manufacturerPnpId=GGL, productId=1, modelYear=null," +
  " manufactureDate=ManufactureDate{week=27, year=2006}, relativeAddress=null}, state ON," +
  " FLAG_DEFAULT_DISPLAY, FLAG_ROTATES_WITH_CONTENT, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS}"

/**
 * Tests for [ScreenshotViewer].
 */
@RunsInEdt
class ScreenshotViewerTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), PortableUiFontRule(), HeadlessDialogRule())

  private val testFrame = object : FramingOption {
    override val displayName = "Test frame"
  }

  @Before
  fun setup() {
    StudioFlags.PLAY_COMPATIBLE_WEAR_SCREENSHOTS_ENABLED.override(true)
  }

  @After
  fun tearDown() {
    findModelessDialog { it is ScreenshotViewer }?.close(CLOSE_EXIT_CODE)
  }

  @Test
  fun testResizing() {
    val screenshotImage = ScreenshotImage(createImage(100, 200), 0, DISPLAY_INFO_PHONE, isTv = false)
    val viewer = createScreenshotViewer(screenshotImage, null)
    val ui = FakeUi(viewer.rootPane)

    val zoomModel = ui.getComponent<ImageComponentDecorator>().zoomModel
    val zoomFactor = zoomModel.zoomFactor

    viewer.rootPane.setSize(viewer.rootPane.width + 50, viewer.rootPane.width + 100)
    ui.layoutAndDispatchEvents()
    assertThat(zoomModel.zoomFactor).isWithin(1.0e-6).of(zoomFactor)
  }

  @Test
  fun testUpdateEditorImage() {
    val screenshotImage = ScreenshotImage(createImage(100, 200), 0, DISPLAY_INFO_PHONE, isTv = false)
    val viewer = createScreenshotViewer(screenshotImage, null)
    val ui = FakeUi(viewer.rootPane)

    val zoomModel = ui.getComponent<ImageComponentDecorator>().zoomModel
    val zoomFactor = zoomModel.zoomFactor

    viewer.updateEditorImage()
    ui.layoutAndDispatchEvents()
    assertThat(zoomModel.zoomFactor).isWithin(1.0e-6).of(zoomFactor)
  }

  @Test
  fun testClipRoundScreenshot() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DISPLAY_INFO_WATCH, isTv = false)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotPostprocessor())
    val ui = FakeUi(viewer.rootPane)
    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Display Shape")
    EDT.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val processedImage: BufferedImage = ui.getComponent<ImageComponent>().document.value
    assertThat(processedImage.getRGB(screenshotImage.width / 2, screenshotImage.height / 2)).isEqualTo(Color.RED.rgb)
    assertThat(processedImage.getRGB(5, 5)).isEqualTo(0)
    assertThat(processedImage.getRGB(screenshotImage.width - 5, screenshotImage.height - 5)).isEqualTo(0)
  }

  @Test
  fun testClipRoundScreenshotWithBackgroundColor() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DISPLAY_INFO_WATCH, isTv = false)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotPostprocessor())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Rectangular")
    EDT.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val processedImage: BufferedImage = ui.getComponent<ImageComponent>().document.value
    assertThat(processedImage.getRGB(screenshotImage.width / 2, screenshotImage.height / 2)).isEqualTo(Color.RED.rgb)
    assertThat(processedImage.getRGB(5, 5)).isEqualTo(Color.BLACK.rgb)
    assertThat(processedImage.getRGB(screenshotImage.width - 5, screenshotImage.height - 5)).isEqualTo(Color.BLACK.rgb)
  }

  @Test
  fun testClipRoundScreenshotWithBackgroundColorInDarkMode() {
    runInEdt {
      UIManager.setLookAndFeel(DarculaLaf())
    }
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DISPLAY_INFO_WATCH, isTv = false)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotPostprocessor())
    val ui = FakeUi(viewer.rootPane)
    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Rectangular")
    EDT.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val processedImage: BufferedImage = ui.getComponent<ImageComponent>().document.value
    assertThat(processedImage.getRGB(screenshotImage.width / 2, screenshotImage.height / 2)).isEqualTo(Color.RED.rgb)
    assertThat(processedImage.getRGB(5, 5)).isEqualTo(Color.BLACK.rgb)
    assertThat(processedImage.getRGB(screenshotImage.width - 5, screenshotImage.height - 5)).isEqualTo(Color.BLACK.rgb)
  }

  @Test
  fun testPlayCompatibleScreenshot_Disabled() {
    StudioFlags.PLAY_COMPATIBLE_WEAR_SCREENSHOTS_ENABLED.override(false)
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DISPLAY_INFO_WATCH, isWear = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotPostprocessor())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.options()).doesNotContain("Play Store Compatible")
  }

  @Test
  fun testPlayCompatibleScreenshotIsAvailable() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DISPLAY_INFO_WATCH, isWear = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotPostprocessor())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.options()).contains("Play Store Compatible")
  }

  @Test
  fun testPlayCompatibleScreenshot() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DISPLAY_INFO_WATCH, isTv = false, isWear = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotPostprocessor())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Play Store Compatible")
    EDT.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val processedImage: BufferedImage = ui.getComponent<ImageComponent>().document.value
    assertThat(processedImage.getRGB(screenshotImage.width / 2, screenshotImage.height / 2)).isEqualTo(Color.RED.rgb)
    assertThat(processedImage.getRGB(5, 5)).isEqualTo(Color.BLACK.rgb)
    assertThat(processedImage.getRGB(screenshotImage.width - 5, screenshotImage.height - 5)).isEqualTo(Color.BLACK.rgb)
  }

  @Test
  fun testPlayCompatibleScreenshotInDarkMode() {
    runInEdt {
      UIManager.setLookAndFeel(DarculaLaf())
    }
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DISPLAY_INFO_WATCH, isTv = false, isWear = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotPostprocessor())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Play Store Compatible")
    EDT.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val processedImage: BufferedImage = ui.getComponent<ImageComponent>().document.value
    assertThat(processedImage.getRGB(screenshotImage.width / 2, screenshotImage.height / 2)).isEqualTo(Color.RED.rgb)
    assertThat(processedImage.getRGB(5, 5)).isEqualTo(Color.BLACK.rgb)
    assertThat(processedImage.getRGB(screenshotImage.width - 5, screenshotImage.height - 5)).isEqualTo(Color.BLACK.rgb)
  }

  @Test
  fun testComboBoxDefaultsToDisplayShapeIfAvailable() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DISPLAY_INFO_WATCH, isWear = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotPostprocessor())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Display Shape")
  }

  @Test
  fun testComboBoxDefaultsToPlayStoreCompatibleIfDisplayShapeIsNotAvailable() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DISPLAY_INFO_WATCH_SQUARE, isWear = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotPostprocessor())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Play Store Compatible")
  }

  @Test
  fun testComboBoxDefaultsToRectangularIfPlayStoreCompatibleAndDisplayShapeAreNotAvailable() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DISPLAY_INFO_PHONE, isWear = false)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotPostprocessor())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Rectangular")
  }

  private fun createImage(width: Int, height: Int): BufferedImage {
    val image = ImageUtils.createDipImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.paint = Color.RED
    graphics.fillRect(0, 0, image.width, image.height)
    graphics.dispose()
    return image
  }

  private fun createScreenshotViewer(screenshotImage: ScreenshotImage,
                                     screenshotPostprocessor: ScreenshotPostprocessor?): ScreenshotViewer {
    val screenshotFile = FileUtil.createTempFile("screenshot", SdkConstants.DOT_PNG).toPath()
    val frames = screenshotPostprocessor?.let { listOf(testFrame) } ?: listOf()
    val viewer = ScreenshotViewer(projectRule.project, screenshotImage, screenshotFile, null, screenshotPostprocessor,
                                  frames, 0, EnumSet.of(ScreenshotViewer.Option.ALLOW_IMAGE_ROTATION))
    viewer.show()
    return viewer
  }

  private fun <E> JComboBox<E>.selectFirstMatch(text: String) {
    for (i in 0 until model.size) {
      if (model.getElementAt(i).toString() == text) {
        this.selectedIndex = i
        return
      }
    }
  }

  private fun <E> JComboBox<E>.options(): List<String> =
    (0 until model.size).map {
      model.getElementAt(it).toString()
    }
}
