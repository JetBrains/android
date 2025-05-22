/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator.actions

import com.android.emulator.control.Posture.PostureValue
import com.android.testutils.ImageDiffUtil
import com.android.test.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.findModelessDialog
import com.android.tools.adtui.swing.optionsAsString
import com.android.tools.adtui.swing.selectFirstMatch
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.ui.screenshot.ScreenshotViewer
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.EDT
import org.intellij.images.editor.ImageFileEditor
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JComboBox
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [EmulatorScreenshotAction].
 */
@RunsInEdt
class EmulatorScreenshotActionTest {
  private val emulatorViewRule = EmulatorViewRule()

  @get:Rule
  val ruleChain = RuleChain(emulatorViewRule, EdtRule(), HeadlessDialogRule())
  @get:Rule
  val portableUiFontRule = PortableUiFontRule()

  private var nullableEmulator: FakeEmulator? = null
  private var nullableEmulatorView: EmulatorView? = null

  private var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  private var emulatorView: EmulatorView
    get() = nullableEmulatorView ?: throw IllegalStateException()
    set(value) { nullableEmulatorView = value }

  @Test
  fun testAction() {
    emulatorView = emulatorViewRule.newEmulatorView()
    emulator = emulatorViewRule.getFakeEmulator(emulatorView)

    emulatorViewRule.executeAction("android.device.screenshot", emulatorView)

    waitForCondition(2.seconds) { findScreenshotViewer() != null }
    val screenshotViewer = findScreenshotViewer()!!
    val rootPane = screenshotViewer.rootPane
    val ui = FakeUi(rootPane)
    val clipComboBox = ui.getComponent<JComboBox<*>>()

    screenshotViewer.waitForUpdateAndGetImage()
    clipComboBox.selectFirstMatch("Display Shape")
    assertAppearance(screenshotViewer.waitForUpdateAndGetImage(), "WithoutFrame")

    clipComboBox.selectFirstMatch("Show Device Frame")
    assertAppearance(screenshotViewer.waitForUpdateAndGetImage(), "WithFrame")
  }

  @Test
  fun testActionFoldableOpen() {
    emulatorView = emulatorViewRule.newEmulatorView { path -> FakeEmulator.createFoldableAvd(path) }
    emulator = emulatorViewRule.getFakeEmulator(emulatorView)

    emulatorViewRule.executeAction("android.device.screenshot", emulatorView)

    waitForCondition(2.seconds) { findScreenshotViewer() != null }
    val screenshotViewer = findScreenshotViewer()!!
    val rootPane = screenshotViewer.rootPane
    val ui = FakeUi(rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.optionsAsString()).contains("Show Device Frame")
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Display Shape")
    assertAppearance(screenshotViewer.waitForUpdateAndGetImage(), "FoldableOpen")
  }

  @Test
  fun testActionFoldableClosed() {
    emulatorView = emulatorViewRule.newEmulatorView { path -> FakeEmulator.createFoldableAvd(path) }
    emulator = emulatorViewRule.getFakeEmulator(emulatorView)
    emulator.setPosture(PostureValue.POSTURE_CLOSED)

    emulatorViewRule.executeAction("android.device.screenshot", emulatorView)

    waitForCondition(2.seconds) { findScreenshotViewer() != null }
    val screenshotViewer = findScreenshotViewer()!!
    val rootPane = screenshotViewer.rootPane
    val ui = FakeUi(rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.optionsAsString()).contains("Show Device Frame")
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Display Shape")
    assertAppearance(screenshotViewer.waitForUpdateAndGetImage(), "FoldableClosed")
  }

  @Test
  fun testWearEmulatorWithoutSkinHasPlayCompatibleOption() {
    emulatorView = emulatorViewRule.newEmulatorView { path -> FakeEmulator.createWatchAvd(path, skinFolder = null) }
    emulator = emulatorViewRule.getFakeEmulator(emulatorView)

    emulatorViewRule.executeAction("android.device.screenshot", emulatorView)

    waitForCondition(2.seconds) { findScreenshotViewer() != null }
    val screenshotViewer = findScreenshotViewer()!!
    val rootPane = screenshotViewer.rootPane
    val ui = FakeUi(rootPane)

    // This AVD has no skin definition, so the combo box should not have a "Show Device Frame" option.
    // It should have a "Play compatible" option.
    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.optionsAsString()).doesNotContain("Show Device Frame")
    assertThat(clipComboBox.optionsAsString()).contains("Play Store Compatible")
  }

  private fun findScreenshotViewer(): ScreenshotViewer? {
    return findModelessDialog { it is ScreenshotViewer } as ScreenshotViewer?
  }

  private fun assertAppearance(image: BufferedImage, goldenImageName: String) {
    val scaledDownImage = ImageUtils.scale(image, 0.1)
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), scaledDownImage, 0.0)
  }

  @Suppress("SameParameterValue")
  private fun getGoldenFile(name: String): Path {
    return TestUtils.resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/${name}.png")
  }
}

private fun ScreenshotViewer.waitForUpdateAndGetImage(): BufferedImage {
  EDT.dispatchAllInvocationEvents()
  PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  val fileEditor = fileEditor()
  waitForCondition(2.seconds) {
    fileEditor.imageEditor.document.value?.let { it.isCornerTransparent() && it.isSame(fileEditor.file.readImage()) } ?: false
  }
  return fileEditor.imageEditor.document.value
}

private fun BufferedImage.isSame(other: BufferedImage?): Boolean {
  if (other == null) {
    return false
  }
  if (width != other.width || height != other.height) {
    return false
  }
  for (y in 0 until height) {
    for (x in 0 until width) {
      if (getRGB(x, y) != other.getRGB(x, y)) {
        return false
      }
    }
  }
  return true
}

private fun BufferedImage.isCornerTransparent(): Boolean =
    getRGB(0, 0) == 0

private fun ScreenshotViewer.fileEditor(): ImageFileEditor =
    PlatformCoreDataKeys.FILE_EDITOR.getData(this) as ImageFileEditor

private fun VirtualFile.readImage(): BufferedImage? {
  return try {
    ImageIO.read(inputStream)
  }
  catch (e: IOException) {
    null
  }
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/EmulatorScreenshotActionTest/golden"
