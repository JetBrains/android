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

import com.android.SdkConstants.DOT_PNG
import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.io.writeImage
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.testutils.dispatchInvocationEventsFor
import com.android.testutils.waitForCondition
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.findModelessDialog
import com.android.tools.adtui.swing.optionsAsString
import com.android.tools.adtui.swing.selectFirstMatch
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.ui.save.PostSaveAction
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.DEVICE_SCREENSHOT_EVENT
import com.google.wireless.android.sdk.stats.DeviceScreenshotEvent
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper.CLOSE_EXIT_CODE
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.IndexingTestUtil.Companion.waitUntilIndexesAreReady
import com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.EDT
import org.intellij.images.ui.ImageComponent
import org.intellij.images.ui.ImageComponentDecorator
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.UIManager
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Tests for [ScreenshotViewer]. */
@RunsInEdt
class ScreenshotViewerTest {
  private val projectRule = ProjectRule()
  private val usageTrackerRule = UsageTrackerRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), PortableUiFontRule(), HeadlessDialogRule(), usageTrackerRule)

  private val testFrame = DeviceFramingOption("Test Frame", SKIN_FOLDER.resolve("pixel_4_xl"))

  private val testRootDisposable
    get() = projectRule.disposable
  private val settings by lazy { DeviceScreenshotSettings.getInstance() }

  @Before
  fun setUp() {
    UIManager.setLookAndFeel(DarculaLaf())
    settings.loadState(DeviceScreenshotSettings())
  }

  @After
  fun tearDown() {
    dispatchInvocationEventsFor(100.milliseconds)
    dispatchAllEventsInIdeEventQueue()
    findModelessDialog<ScreenshotViewer>()?.close(CLOSE_EXIT_CODE)
    waitUntilIndexesAreReady(projectRule.project) // Closing a screenshot viewer triggers deletion of the backing file and indexing.
    settings.loadState(DeviceScreenshotSettings())
  }

  @Test
  fun testResizing() {
    assumeFalse(SystemInfo.isWindows) // b/356410902
    val screenshotImage =
        ScreenshotImage(createImage(100, 200), 0, DeviceType.HANDHELD, "Phone", PRIMARY_DISPLAY_ID, Dimension(1080, 2400), 420)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
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
  fun testResolutionChange() {
    val settings = DeviceScreenshotSettings.getInstance()
    assertThat(settings.scale == 1.0)
    settings.scale = 0.5
    val screenshotImage =
        ScreenshotImage(createImage(100, 200), 0, DeviceType.HANDHELD, "Phone", PRIMARY_DISPLAY_ID, Dimension(1080, 2400), 420)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)
    val imageComponent = ui.getComponent<ImageComponent>()
    waitForCondition(2.seconds) { imageComponent.document.value?.width == 50 }
    val image = imageComponent.document.value
    assertThat(image.width).isEqualTo(50)
    assertThat(image.height).isEqualTo(100)
    @Suppress("UNCHECKED_CAST") val resolutionComboBox = ui.getComponent<ComboBox<*>> { it.item is Int } as ComboBox<Int>
    assertThat(resolutionComboBox.item).isEqualTo(50)
    resolutionComboBox.item = 25
    assertThat(settings.scale == 0.25)
    waitForCondition(2.seconds) { imageComponent.document.value?.width == 25 }
    assertThat(imageComponent.document.value.height).isEqualTo(50)
  }

  @Test
  fun testRecapture() {
    val screenshotImage = ScreenshotImage(createImage(100, 200), 0, DeviceType.HANDHELD, "Phone", PRIMARY_DISPLAY_ID, Dimension(1080, 2400),
                                          420)
    val screenshotProvider = TestScreenshotProvider(screenshotImage, testRootDisposable)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator(), screenshotProvider)
    val ui = FakeUi(viewer.rootPane)

    val recaptureButton = ui.getComponent<JButton> { it.text == "Recapture" }
    ui.clickOn(recaptureButton)
    waitForCondition(2.seconds) { screenshotProvider.captured }
    Disposer.dispose(screenshotProvider)
    assertThat(recaptureButton.isEnabled).isFalse()
  }

  @Test
  fun testClipRoundScreenshot() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DeviceType.WEAR, "Phone", PRIMARY_DISPLAY_ID, Dimension(454, 454), 320,
                                          isRoundDisplay = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)
    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Display Shape")
    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    waitForCondition(2.seconds) { ui.getComponent<ImageComponent>().document.value != null }
    val processedImage: BufferedImage = ui.getComponent<ImageComponent>().document.value
    assertThat(processedImage.getRGB(screenshotImage.width / 2, screenshotImage.height / 2)).isEqualTo(Color.RED.rgb)
    assertThat(processedImage.getRGB(5, 5)).isEqualTo(0)
    assertThat(processedImage.getRGB(screenshotImage.width - 5, screenshotImage.height - 5)).isEqualTo(0)
  }

  @Test
  fun testClipRoundScreenshotWithBackgroundColor() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DeviceType.WEAR, "Watch", PRIMARY_DISPLAY_ID, Dimension(454, 454), 320,
                                          isRoundDisplay = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Rectangular")
    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    waitForCondition(TIMEOUT) {
      ui.getComponent<ImageComponent>().document.value?.getRGB(0, 0) == Color.BLACK.rgb
    }
    val processedImage: BufferedImage = ui.getComponent<ImageComponent>().document.value
    assertThat(processedImage.getRGB(screenshotImage.width / 2, screenshotImage.height / 2)).isEqualTo(Color.RED.rgb)
    assertThat(processedImage.getRGB(5, 5)).isEqualTo(Color.BLACK.rgb)
    assertThat(processedImage.getRGB(screenshotImage.width - 5, screenshotImage.height - 5)).isEqualTo(Color.BLACK.rgb)
  }

  @Test
  fun testClipRoundScreenshotWithBackgroundColorInDarkMode() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DeviceType.WEAR, "Watch", PRIMARY_DISPLAY_ID, Dimension(454, 454), 320,
                                          isRoundDisplay = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)
    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Rectangular")
    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    waitForCondition(TIMEOUT) {
      ui.getComponent<ImageComponent>().document.value?.getRGB(0, 0) == Color.BLACK.rgb
    }
    val processedImage: BufferedImage = ui.getComponent<ImageComponent>().document.value
    assertThat(processedImage.getRGB(screenshotImage.width / 2, screenshotImage.height / 2)).isEqualTo(Color.RED.rgb)
    assertThat(processedImage.getRGB(5, 5)).isEqualTo(Color.BLACK.rgb)
    assertThat(processedImage.getRGB(screenshotImage.width - 5, screenshotImage.height - 5)).isEqualTo(Color.BLACK.rgb)
  }

  @Test
  fun testPlayCompatibleScreenshotIsAvailable() {
    val screenshotImage = ScreenshotImage(createImage(360, 360), 0, DeviceType.WEAR, "Watch", PRIMARY_DISPLAY_ID, Dimension(454, 454), 320,
                                          isRoundDisplay = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.optionsAsString()).contains("Play Store Compatible")
  }

  @Test
  fun testPlayStoreCompatibleOptionIsSetByDefaultForWearDevices() {
    val screenshotImage = ScreenshotImage(createImage(360, 360), 0, DeviceType.WEAR, "Watch", PRIMARY_DISPLAY_ID, Dimension(454, 454), 320,
                                          isRoundDisplay = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Play Store Compatible")
  }

  @Test
  fun testPlayStoreCompatibleOptionIsNotSetByDefaultForNonWearDevices() {
    val screenshotImage = ScreenshotImage(createImage(360, 360), 0, DeviceType.HANDHELD, "Watch", PRIMARY_DISPLAY_ID, Dimension(1080, 2400), 420)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.selectedItem?.toString()).isNotEqualTo("Play Store Compatible")
  }

  @Test
  fun testPlayCompatibleScreenshotIsNotAvailableWhenScreenshotIsNot1to1Ratio() {
    val screenshotImage = ScreenshotImage(createImage(384, 500), 0, DeviceType.WEAR, "Watch", PRIMARY_DISPLAY_ID, Dimension(454, 454), 320,
                                          isRoundDisplay = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.optionsAsString()).doesNotContain("Play Store Compatible")
  }

  @Test
  fun testPlayCompatibleScreenshot() {
    val screenshotImage = ScreenshotImage(createImage(384, 384), 0, DeviceType.WEAR, "Watch", PRIMARY_DISPLAY_ID, Dimension(454, 454), 320,
                                          isRoundDisplay = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Play Store Compatible")
    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    waitForCondition(TIMEOUT) {
      ui.getComponent<ImageComponent>().document.value?.getRGB(0, 0) == Color.BLACK.rgb
    }
    val processedImage: BufferedImage = ui.getComponent<ImageComponent>().document.value
    assertThat(processedImage.getRGB(screenshotImage.width / 2, screenshotImage.height / 2)).isEqualTo(Color.RED.rgb)
    assertThat(processedImage.getRGB(5, 5)).isEqualTo(Color.BLACK.rgb)
    assertThat(processedImage.getRGB(screenshotImage.width - 5, screenshotImage.height - 5)).isEqualTo(Color.BLACK.rgb)
  }

  @Test
  fun testPlayCompatibleScreenshotInDarkMode() {
    val screenshotImage = ScreenshotImage(createImage(384, 384), 0, DeviceType.WEAR, "Watch", PRIMARY_DISPLAY_ID, Dimension(454, 454), 320,
                                          isRoundDisplay = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Play Store Compatible")
    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    waitForCondition(TIMEOUT) {
      ui.getComponent<ImageComponent>().document.value?.getRGB(0, 0) == Color.BLACK.rgb
    }
    val processedImage: BufferedImage = ui.getComponent<ImageComponent>().document.value
    assertThat(processedImage.getRGB(screenshotImage.width / 2, screenshotImage.height / 2)).isEqualTo(Color.RED.rgb)
    assertThat(processedImage.getRGB(5, 5)).isEqualTo(Color.BLACK.rgb)
    assertThat(processedImage.getRGB(screenshotImage.width - 5, screenshotImage.height - 5)).isEqualTo(Color.BLACK.rgb)
  }

  @Test
  fun testComboBoxDefaultsToDisplayShapeIfAvailable() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DeviceType.WEAR, "Watch", PRIMARY_DISPLAY_ID, Dimension(454, 454), 320,
                                          isRoundDisplay = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Display Shape")
  }

  @Test
  fun testComboBoxDefaultsToPlayStoreCompatibleIfDisplayShapeIsNotAvailable() {
    val screenshotImage = ScreenshotImage(createImage(360, 360), 0, DeviceType.WEAR, "Watch", PRIMARY_DISPLAY_ID, Dimension(454, 454), 320)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Play Store Compatible")
  }

  @Test
  fun testComboBoxDefaultsToRectangularIfPlayStoreCompatibleAndDisplayShapeAreNotAvailable() {
    val screenshotImage = ScreenshotImage(createImage(360, 360), 0, DeviceType.HANDHELD, "Phone", PRIMARY_DISPLAY_ID, Dimension(1080, 2400), 420)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Rectangular")
  }

  @Test
  fun testSave_Phone() {
    val screenshotImage = ScreenshotImage(createImage(200, 180), 0, DeviceType.HANDHELD, "Phone", PRIMARY_DISPLAY_ID, Dimension(1080, 2400), 420)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    service<DeviceScreenshotSettings>().saveConfig.postSaveAction = PostSaveAction.NONE

    viewer.clickDefaultButton()

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
  fun testSave_Wear() {
    val screenshotImage = ScreenshotImage(createImage(384, 384), 0, DeviceType.WEAR, "Watch", PRIMARY_DISPLAY_ID, Dimension(454, 454), 320,
                                          isRoundDisplay = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
    val ui = FakeUi(viewer.rootPane)
    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Play Store Compatible")
    viewer.clickDefaultButton()

    EDT.dispatchAllInvocationEvents()
    dispatchAllEventsInIdeEventQueue()
    assertThat(usageTrackerRule.screenshotEvents()).containsExactly(
      DeviceScreenshotEvent.newBuilder()
        .setDeviceType(DeviceScreenshotEvent.DeviceType.WEAR)
        .setDecorationOption(DeviceScreenshotEvent.DecorationOption.PLAY_COMPATIBLE)
        .build()
    )
  }

  @Test
  fun testScreenshotUsageIsTracked_CopyClipboard_Phone() {
    val screenshotImage = ScreenshotImage(createImage(1080, 2280), 0, DeviceType.HANDHELD, "Phone", PRIMARY_DISPLAY_ID, Dimension(1080, 2400), 420)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
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
    val screenshotImage = ScreenshotImage(createImage(384, 384), 0, DeviceType.WEAR, "Watch", PRIMARY_DISPLAY_ID, Dimension(454, 454), 320,
                                          isRoundDisplay = true)
    val viewer = createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator())
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
    val screenshotImage = ScreenshotImage(createImage(384, 384), 0, DeviceType.WEAR, "Watch", PRIMARY_DISPLAY_ID, Dimension(454, 454), 320,
                                          isRoundDisplay = true)
    service<DeviceScreenshotSettings>().frameScreenshot = true

    // test that no exceptions are thrown
    createScreenshotViewer(screenshotImage, DeviceScreenshotDecorator(), framingOptions = listOf())
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
                                     screenshotProvider: ScreenshotProvider = TestScreenshotProvider(screenshotImage, testRootDisposable),
                                     framingOptions: List<FramingOption> = listOf(testFrame)): ScreenshotViewer {
    val decoration = ScreenshotViewer.getDefaultDecoration(screenshotImage, screenshotDecorator, framingOptions.firstOrNull())
    val processedImage = ImageUtils.scale(screenshotDecorator.decorate(screenshotImage, decoration), getScreenshotScale())
    val backingFile = FileUtil.createTempFile("screenshot", DOT_PNG).toPath()
    processedImage.writeImage("PNG", backingFile)
    val screenshotFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(backingFile)!!
    val viewer = ScreenshotViewer(projectRule.project, screenshotImage, processedImage, screenshotFile, screenshotProvider,
                                  screenshotDecorator, framingOptions, 0, allowImageRotation = true)
    viewer.show()
    return viewer
  }

  private fun UsageTrackerRule.screenshotEvents(): List<DeviceScreenshotEvent> =
    usages.filter { it.studioEvent.kind == DEVICE_SCREENSHOT_EVENT }.map { it.studioEvent.deviceScreenshotEvent }

  private class TestScreenshotProvider(private val screenshotImage: ScreenshotImage, parentDisposable: Disposable) : ScreenshotProvider {
    var captured = false

    init {
      Disposer.register(parentDisposable, this)
    }

    override suspend fun captureScreenshot(): ScreenshotImage {
      captured = true
      return screenshotImage
    }

    override fun dispose() {
    }
  }
}

private val TIMEOUT = 5.seconds

private val SKIN_FOLDER = DeviceArtDescriptor.getBundledDescriptorsFolder()!!.toPath()
