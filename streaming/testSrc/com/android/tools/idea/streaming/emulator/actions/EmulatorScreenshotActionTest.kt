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
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.EDT
import org.intellij.images.ui.ImageComponent
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS
import javax.swing.JComboBox

/**
 * Tests for [EmulatorScreenshotAction].
 */
@RunsInEdt
class EmulatorScreenshotActionTest {
  private val emulatorViewRule = EmulatorViewRule()

  @get:Rule
  val ruleChain = RuleChain(emulatorViewRule, EdtRule(), HeadlessDialogRule())

  private var nullableEmulator: FakeEmulator? = null
  private var nullableEmulatorView: EmulatorView? = null

  private var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  private var emulatorView: EmulatorView
    get() = nullableEmulatorView ?: throw IllegalStateException()
    set(value) { nullableEmulatorView = value }

  @get:Rule
  val portableUiFontRule = PortableUiFontRule()

  @Test
  fun testAction() {
    emulatorView = emulatorViewRule.newEmulatorView()
    emulator = emulatorViewRule.getFakeEmulator(emulatorView)

    emulatorViewRule.executeAction("android.device.screenshot", emulatorView)

    waitForCondition(500, SECONDS) { findScreenshotViewer() != null }
    val screenshotViewer = findScreenshotViewer()!!
    val rootPane = screenshotViewer.rootPane
    val ui = FakeUi(rootPane)
    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Display Shape")
    EDT.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    var image = ui.getComponent<ImageComponent>().document.value
    assertAppearance(image, "WithoutFrame")
    clipComboBox.selectFirstMatch("Show Device Frame")
    EDT.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    image = ui.getComponent<ImageComponent>().document.value
    assertAppearance(image, "WithFrame")
  }

  @Test
  fun testActionFoldableOpen() {
    emulatorView = emulatorViewRule.newEmulatorView { path -> FakeEmulator.createFoldableAvd(path) }
    emulator = emulatorViewRule.getFakeEmulator(emulatorView)

    emulatorViewRule.executeAction("android.device.screenshot", emulatorView)

    waitForCondition(500, SECONDS) { findScreenshotViewer() != null }
    val screenshotViewer = findScreenshotViewer()!!
    val rootPane = screenshotViewer.rootPane
    val ui = FakeUi(rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.optionsAsString()).contains("Show Device Frame")

    EDT.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val image = ui.getComponent<ImageComponent>().document.value
    assertAppearance(image, "FoldableOpen")
  }

  @Test
  fun testActionFoldableClosed() {
    emulatorView = emulatorViewRule.newEmulatorView { path -> FakeEmulator.createFoldableAvd(path) }
    emulator = emulatorViewRule.getFakeEmulator(emulatorView)
    emulator.setPosture(PostureValue.POSTURE_CLOSED)

    emulatorViewRule.executeAction("android.device.screenshot", emulatorView)

    waitForCondition(500, SECONDS) { findScreenshotViewer() != null }
    val screenshotViewer = findScreenshotViewer()!!
    val rootPane = screenshotViewer.rootPane
    val ui = FakeUi(rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.optionsAsString()).contains("Show Device Frame")

    EDT.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val image = ui.getComponent<ImageComponent>().document.value
    assertAppearance(image, "FoldableClosed")
  }

  @Test
  fun testWearEmulatorWithoutSkinHasPlayCompatibleOption() {
    emulatorView = emulatorViewRule.newEmulatorView { path -> FakeEmulator.createWatchAvd(path, skinFolder = null) }
    emulator = emulatorViewRule.getFakeEmulator(emulatorView)

    emulatorViewRule.executeAction("android.device.screenshot", emulatorView)

    waitForCondition(500, SECONDS) { findScreenshotViewer() != null }
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

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/EmulatorScreenshotActionTest/golden"
