/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ddms.screenshot

import com.android.SdkConstants
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.adtui.swing.findModelessDialog
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.DialogWrapper.CLOSE_EXIT_CODE
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
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

/**
 * Tests for [ScreenshotViewer].
 */
@RunsInEdt
class ScreenshotViewerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk("ScreenshotViewerTest").onEdt()

  @get:Rule
  val portableUiFontRule = PortableUiFontRule()

  private val testRootDisposable
    get() = projectRule.fixture.testRootDisposable

  private val testFrame = object : FramingOption {
    override val displayName = "Test frame"
  }

  @Before
  fun setUp() {
    enableHeadlessDialogs(testRootDisposable)
  }

  @After
  fun tearDown() {
    findModelessDialog { it is ScreenshotViewer }?.close(CLOSE_EXIT_CODE)
  }

  @Test
  fun testResizing() {
    val screenshotImage = DeviceScreenshotImage(createImage(100, 200), 0, false)
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
    val screenshotImage = DeviceScreenshotImage(createImage(100, 200), 0, false)
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
    val screenshotImage = DeviceScreenshotImage(createImage(200, 180), 0, true)
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
    val screenshotImage = DeviceScreenshotImage(createImage(200, 180), 0, true)
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
    val viewer = ScreenshotViewer(projectRule.project, screenshotImage, screenshotFile, null, screenshotPostprocessor,
                                  listOf(testFrame), 0, EnumSet.of(ScreenshotViewer.Option.ALLOW_IMAGE_ROTATION))
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
}