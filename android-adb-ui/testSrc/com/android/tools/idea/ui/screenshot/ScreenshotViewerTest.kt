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
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.testutils.dispatchInvocationEventsFor
import com.android.testutils.waitForCondition
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.findModelessDialog
import com.android.tools.adtui.swing.optionsAsString
import com.android.tools.adtui.swing.selectFirstMatch
import com.android.tools.analytics.UsageTrackerRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.DEVICE_SCREENSHOT_EVENT
import com.google.wireless.android.sdk.stats.DeviceScreenshotEvent
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.mock.Mock
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.CLOSE_EXIT_CODE
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.EDT
import org.intellij.images.ui.ImageComponent
import org.intellij.images.ui.ImageComponentDecorator
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import java.util.EnumSet
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.UIManager
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val TIMEOUT = 5.seconds

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
  private val usageTrackerRule = UsageTrackerRule()
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), PortableUiFontRule(), HeadlessDialogRule(), disposableRule, usageTrackerRule)

  private val testFrame = object : FramingOption {
    override val displayName = "Test frame"
  }

  private val fileNamePrompts = mutableListOf<String>()
  private val openedFiles = mutableListOf<String>()

  @After
  fun tearDown() {
    dispatchInvocationEventsFor(100.milliseconds)
    dispatchAllEventsInIdeEventQueue()
    findModelessDialog { it is ScreenshotViewer }?.close(CLOSE_EXIT_CODE)
    dispatchAllEventsInIdeEventQueue()
  }

  @Test
  fun testResizing() {
    val screenshotImage = ScreenshotImage(createImage(100, 200), 0, DeviceType.HANDHELD, DISPLAY_INFO_PHONE)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val zoomModel = ui.getComponent<ImageComponentDecorator>().zoomModel
    waitForCondition(TIMEOUT) {
      zoomModel.zoomFactor == 1.0
    }
    viewer.rootPane.setSize(viewer.rootPane.width + 50, viewer.rootPane.width + 100)
    ui.layoutAndDispatchEvents()
    assertThat(zoomModel.zoomFactor).isWithin(1.0e-6).of(1.0)
  }

  @Test
  fun testUpdateEditorImage() {
    val screenshotImage = ScreenshotImage(createImage(100, 200), 0, DeviceType.HANDHELD, DISPLAY_INFO_PHONE)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val zoomModel = ui.getComponent<ImageComponentDecorator>().zoomModel
    waitForCondition(TIMEOUT) {
      zoomModel.zoomFactor == 1.0
    }

    viewer.updateEditorImage()
    ui.layoutAndDispatchEvents()
    assertThat(zoomModel.zoomFactor).isWithin(1.0e-6).of(1.0)
  }

  @Test
  fun testRecapture() {
    val screenshotImage = ScreenshotImage(createImage(100, 200), 0, DeviceType.HANDHELD, DISPLAY_INFO_PHONE)
    val screenshotSupplier = object : ScreenshotSupplier {
      var captured = false

      override fun captureScreenshot(): ScreenshotImage {
        captured = true
        return screenshotImage
      }

      override fun dispose() {
      }
    }
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator(), screenshotSupplier)
    val ui = FakeUi(viewer.rootPane)

    val recaptureButton = ui.getComponent<JButton> { it.text == "Recapture" }
    ui.clickOn(recaptureButton)
    assertThat(screenshotSupplier.captured).isTrue()
    Disposer.dispose(screenshotSupplier)
    assertThat(recaptureButton.isEnabled).isFalse()
  }

  @Test
  fun testClipRoundScreenshot() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DeviceType.WEAR, DISPLAY_INFO_WATCH)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)
    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Display Shape")
    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    val processedImage: BufferedImage = ui.getComponent<ImageComponent>().document.value
    assertThat(processedImage.getRGB(screenshotImage.width / 2, screenshotImage.height / 2)).isEqualTo(Color.RED.rgb)
    assertThat(processedImage.getRGB(5, 5)).isEqualTo(0)
    assertThat(processedImage.getRGB(screenshotImage.width - 5, screenshotImage.height - 5)).isEqualTo(0)
  }

  @Test
  fun testClipRoundScreenshotWithBackgroundColor() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DeviceType.WEAR, DISPLAY_INFO_WATCH)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Rectangular")
    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    waitForCondition(TIMEOUT) {
      ui.getComponent<ImageComponent>().document.value.getRGB(0, 0) == Color.BLACK.rgb
    }
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
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DeviceType.WEAR, DISPLAY_INFO_WATCH)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)
    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Rectangular")
    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    waitForCondition(TIMEOUT) {
      ui.getComponent<ImageComponent>().document.value.getRGB(0, 0) == Color.BLACK.rgb
    }
    val processedImage: BufferedImage = ui.getComponent<ImageComponent>().document.value
    assertThat(processedImage.getRGB(screenshotImage.width / 2, screenshotImage.height / 2)).isEqualTo(Color.RED.rgb)
    assertThat(processedImage.getRGB(5, 5)).isEqualTo(Color.BLACK.rgb)
    assertThat(processedImage.getRGB(screenshotImage.width - 5, screenshotImage.height - 5)).isEqualTo(Color.BLACK.rgb)
  }

  @Test
  fun testPlayCompatibleScreenshotIsAvailable() {
    val screenshotImage = ScreenshotImage(createImage(360, 360), 0, DeviceType.WEAR, DISPLAY_INFO_WATCH)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.optionsAsString()).contains("Play Store Compatible")
  }

  @Test
  fun testPlayStoreCompatibleOptionIsSetByDefaultForWearDevices() {
    val screenshotImage = ScreenshotImage(createImage(360, 360), 0, DeviceType.WEAR, DISPLAY_INFO_WATCH)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Play Store Compatible")
  }

  @Test
  fun testPlayStoreCompatibleOptionIsNotSetByDefaultForNonWearDevices() {
    val screenshotImage = ScreenshotImage(createImage(360, 360), 0, DeviceType.HANDHELD, DISPLAY_INFO_PHONE)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.selectedItem?.toString()).isNotEqualTo("Play Store Compatible")
  }

  @Test
  fun testPlayCompatibleScreenshotIsNotAvailableWhenScreenshotIsNot1to1Ratio() {
    val screenshotImage = ScreenshotImage(createImage(384, 500), 0, DeviceType.WEAR, DISPLAY_INFO_WATCH)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.optionsAsString()).doesNotContain("Play Store Compatible")
  }

  @Test
  fun testPlayCompatibleScreenshot() {
    val screenshotImage = ScreenshotImage(createImage(384, 384), 0, DeviceType.WEAR, DISPLAY_INFO_WATCH)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Play Store Compatible")
    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    waitForCondition(TIMEOUT) {
      ui.getComponent<ImageComponent>().document.value.getRGB(0, 0) == Color.BLACK.rgb
    }
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
    val screenshotImage = ScreenshotImage(createImage(384, 384), 0, DeviceType.WEAR, DISPLAY_INFO_WATCH)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Play Store Compatible")
    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    waitForCondition(TIMEOUT) {
      ui.getComponent<ImageComponent>().document.value.getRGB(0, 0) == Color.BLACK.rgb
    }
    val processedImage: BufferedImage = ui.getComponent<ImageComponent>().document.value
    assertThat(processedImage.getRGB(screenshotImage.width / 2, screenshotImage.height / 2)).isEqualTo(Color.RED.rgb)
    assertThat(processedImage.getRGB(5, 5)).isEqualTo(Color.BLACK.rgb)
    assertThat(processedImage.getRGB(screenshotImage.width - 5, screenshotImage.height - 5)).isEqualTo(Color.BLACK.rgb)
  }

  @Test
  fun testComboBoxDefaultsToDisplayShapeIfAvailable() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DeviceType.WEAR, DISPLAY_INFO_WATCH)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Display Shape")
  }

  @Test
  fun testComboBoxDefaultsToPlayStoreCompatibleIfDisplayShapeIsNotAvailable() {
    val screenshotImage = ScreenshotImage(createImage(360, 360), 0, DeviceType.WEAR, DISPLAY_INFO_WATCH_SQUARE)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Play Store Compatible")
  }

  @Test
  fun testComboBoxDefaultsToRectangularIfPlayStoreCompatibleAndDisplayShapeAreNotAvailable() {
    val screenshotImage = ScreenshotImage(createImage(360, 360), 0, DeviceType.HANDHELD, DISPLAY_INFO_PHONE)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Rectangular")
  }

  @Test
  fun testSave_Phone() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DeviceType.HANDHELD, DISPLAY_INFO_PHONE)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val tempFile = FileUtil.createTempFile("saved_screenshot", SdkConstants.DOT_PNG)
    overrideSaveFileDialog(tempFile)

    viewer.doOKAction()

    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    assertThat(fileNamePrompts).hasSize(1)
    val parentPrefix = tempFile.parent.toString().replace(File.separatorChar, '/')
    assertThat(fileNamePrompts[0]).matches("file://$parentPrefix/Screenshot_\\d\\d\\d\\d\\d\\d\\d\\d_\\d\\d\\d\\d\\d\\d")
    assertThat(openedFiles).containsExactly("file://${tempFile.toString().replace(File.separatorChar, '/')}")
    assertThat(usageTrackerRule.screenshotEvents()).containsExactly(
      DeviceScreenshotEvent.newBuilder()
        .setDeviceType(DeviceScreenshotEvent.DeviceType.PHONE)
        .setDecorationOption(DeviceScreenshotEvent.DecorationOption.RECTANGULAR)
        .build()
    )
  }

  @Test
  fun testSave_Wear() {
    val screenshotImage = ScreenshotImage(createImage(384, 384), 0, DeviceType.WEAR, DISPLAY_INFO_WATCH)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)
    val clipComboBox = ui.getComponent<JComboBox<*>>()
    val tempFile = FileUtil.createTempFile("saved_screenshot", SdkConstants.DOT_PNG)
    overrideSaveFileDialog(tempFile)

    clipComboBox.selectFirstMatch("Play Store Compatible")
    viewer.doOKAction()

    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    assertThat(fileNamePrompts).hasSize(1)
    val parentPrefix = tempFile.parent.toString().replace(File.separatorChar, '/')
    assertThat(fileNamePrompts[0]).matches("file://$parentPrefix/Screenshot_\\d\\d\\d\\d\\d\\d\\d\\d_\\d\\d\\d\\d\\d\\d")
    assertThat(openedFiles).containsExactly("file://${tempFile.toString().replace(File.separatorChar, '/')}")
    assertThat(usageTrackerRule.screenshotEvents()).containsExactly(
      DeviceScreenshotEvent.newBuilder()
        .setDeviceType(DeviceScreenshotEvent.DeviceType.WEAR)
        .setDecorationOption(DeviceScreenshotEvent.DecorationOption.PLAY_COMPATIBLE)
        .build()
    )
  }

  @Test
  fun testScreenshotUsageIsTracked_CopyClipboard_Phone() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DeviceType.HANDHELD, DISPLAY_INFO_PHONE)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)
    val copyClipboardButton = ui.getComponent<JButton> { it.text == "Copy to Clipboard" }

    waitForCondition(TIMEOUT) {
      ui.getComponent<ImageComponent>().document.value != null
    }
    copyClipboardButton.doClick()

    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    assertThat(usageTrackerRule.screenshotEvents()).containsExactly(
      DeviceScreenshotEvent.newBuilder()
        .setDeviceType(DeviceScreenshotEvent.DeviceType.PHONE)
        .setDecorationOption(DeviceScreenshotEvent.DecorationOption.RECTANGULAR)
        .build()
    )
  }

  @Test
  fun testScreenshotUsageIsTracked_CopyClipboard_Wear() {
    val screenshotImage = ScreenshotImage(createImage(384, 384), 0, DeviceType.WEAR, DISPLAY_INFO_WATCH)
    val viewer = createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)
    val copyClipboardButton = ui.getComponent<JButton> { it.text == "Copy to Clipboard" }
    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Display Shape")
    copyClipboardButton.doClick()

    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    assertThat(usageTrackerRule.screenshotEvents()).containsExactly(
      DeviceScreenshotEvent.newBuilder()
        .setDeviceType(DeviceScreenshotEvent.DeviceType.WEAR)
        .setDecorationOption(DeviceScreenshotEvent.DecorationOption.DISPLAY_SHAPE_CLIP)
        .build()
    )
  }

  @Test
  fun testScreenshotViewerWithoutFramingOptionsDoesNotAttemptToSelectFrameOption() {
    val screenshotImage = ScreenshotImage(createImage(384, 384), 0, DeviceType.WEAR, DISPLAY_INFO_WATCH)
    ScreenshotViewer.PersistentState.getInstance(projectRule.project).frameScreenshot = true

    // test that no exceptions are thrown
    createScreenshotViewer(screenshotImage, DeviceArtScreenshotDecorator(), framingOptions = listOf())
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
                                     screenshotDecorator: ScreenshotDecorator,
                                     screenshotSupplier: ScreenshotSupplier? = null,
                                     framingOptions: List<FramingOption> = listOf(testFrame)): ScreenshotViewer {
    val backingFile = FileUtil.createTempFile("screenshot", SdkConstants.DOT_PNG).toPath()
    val screenshotFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(backingFile)!!
    val viewer = ScreenshotViewer(projectRule.project, screenshotImage, screenshotFile, screenshotSupplier, screenshotDecorator,
                                  framingOptions, 0, EnumSet.of(ScreenshotViewer.Option.ALLOW_IMAGE_ROTATION))
    viewer.show()
    return viewer
  }

  private fun overrideSaveFileDialog(file: File) {
    val virtualFileWrapper = VirtualFileWrapper(file)
    val factory = object : FileChooserFactoryImpl() {
      override fun createSaveFileDialog(descriptor: FileSaverDescriptor, project: Project?): FileSaverDialog {
        return object : FileSaverDialog {
          override fun save(baseDir: VirtualFile?, filename: String?): VirtualFileWrapper {
            fileNamePrompts.add("$baseDir/$filename")
            return virtualFileWrapper
          }
          override fun save(baseDir: Path?, filename: String?) = virtualFileWrapper
        }
      }
    }
    ApplicationManager.getApplication().replaceService(FileChooserFactory::class.java, factory, disposableRule.disposable)

    val fileEditorManager = object : Mock.MyFileEditorManager() {
      @Suppress("UnstableApiUsage")
      override fun openFile(file: VirtualFile, window: EditorWindow?, options: FileEditorOpenOptions): FileEditorComposite {
        openedFiles.add(file.toString())
        return super.openFile(file, window, options)
      }
    }
    projectRule.project.replaceService(FileEditorManager::class.java, fileEditorManager, disposableRule.disposable)
  }

  private fun UsageTrackerRule.screenshotEvents(): List<DeviceScreenshotEvent> =
    usages.filter { it.studioEvent.kind == DEVICE_SCREENSHOT_EVENT }.map { it.studioEvent.deviceScreenshotEvent }
}
