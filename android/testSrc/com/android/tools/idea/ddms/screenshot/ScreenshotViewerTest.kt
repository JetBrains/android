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

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.adtui.swing.setPortableUiFont
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.CLOSE_EXIT_CODE
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Color
import java.awt.image.BufferedImage

/**
 * Tests for [ScreenshotViewer].
 */
@RunsInEdt
class ScreenshotViewerTest {
  private val projectRule = AndroidProjectRule.onDisk("ScreenshotViewerTest")
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private var screenshotViewer: ScreenshotViewer? = null

  private val testRootDisposable
    get() = projectRule.fixture.testRootDisposable

  @Before
  fun setUp() {
    setPortableUiFont()
    enableHeadlessDialogs(testRootDisposable)
    val file = VfsUtilCore.virtualToIoFile(projectRule.fixture.tempDirFixture.createFile("screenshot1.png"))
    val image = BufferedImage(150, 280, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.background = Color.WHITE
    graphics.clearRect(0, 0, image.width, image.height)
    graphics.dispose()
    val viewer = ScreenshotViewer(projectRule.project, image, file, null, null, true)
    Disposer.register(testRootDisposable) { viewer.close(CLOSE_EXIT_CODE) }
    viewer.show()
    screenshotViewer = viewer
  }

  @Test
  fun testResizing() {
    val viewer = screenshotViewer!!
    val ui = createFakeUi(viewer)

    val zoomModel = viewer.imageFileEditor.imageEditor.zoomModel
    val zoomFactor = zoomModel.zoomFactor

    viewer.rootPane.setSize(viewer.rootPane.width + 50, viewer.rootPane.width + 100)
    ui.layoutAndDispatchEvents()
    assertThat(zoomModel.zoomFactor).isWithin(1.0e-6).of(zoomFactor)
  }

  @Test
  fun testUpdateEditorImage() {
    val viewer = screenshotViewer!!
    val ui = createFakeUi(viewer)

    val zoomModel = viewer.imageFileEditor.imageEditor.zoomModel
    val zoomFactor = zoomModel.zoomFactor

    viewer.updateEditorImage();
    ui.layoutAndDispatchEvents()
    assertThat(zoomModel.zoomFactor).isWithin(1.0e-6).of(zoomFactor)
  }

  @Test
  fun testClipRoundScreenshot(){
    val viewer = screenshotViewer!!
    val ui = createFakeUi(viewer)
    ui.layoutAndDispatchEvents()

    val processedImage: BufferedImage = viewer.imageFileEditor.imageEditor.document.value
    assertThat(processedImage.getRGB(processedImage.width / 2, processedImage.height / 2)).isEqualTo(Color.WHITE.rgb)
    assertThat(processedImage.getRGB(5, 5)).isEqualTo(0x0)
  }

  private fun createFakeUi(viewer: DialogWrapper): FakeUi {
    return FakeUi(viewer.rootPane).apply { layoutAndDispatchEvents() }
  }
}