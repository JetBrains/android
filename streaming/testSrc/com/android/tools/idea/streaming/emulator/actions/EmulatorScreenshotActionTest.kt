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

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.findModelessDialog
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.ui.screenshot.ScreenshotViewer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.EDT
import org.intellij.images.ui.ImageComponent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.concurrent.TimeUnit
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

  @Before
  fun setUp() {
    emulatorView = emulatorViewRule.newEmulatorView()
    emulator = emulatorViewRule.getFakeEmulator(emulatorView)
  }

  @Test
  fun testAction() {
    emulatorViewRule.executeAction("android.device.screenshot", emulatorView)

    waitForCondition(500, TimeUnit.SECONDS) { findScreenshotViewer() != null }
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

  private fun <E> JComboBox<E>.selectFirstMatch(text: String) {
    for (i in 0 until model.size) {
      if (model.getElementAt(i).toString() == text) {
        selectedIndex = i
        return
      }
    }
  }
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/EmulatorScreenshotActionTest/golden"
