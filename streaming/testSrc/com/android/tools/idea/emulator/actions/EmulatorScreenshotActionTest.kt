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
package com.android.tools.idea.emulator.actions

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.SetPortableUiFontRule
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.adtui.swing.findModelessDialog
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.ddms.screenshot.ScreenshotViewer
import com.android.tools.idea.emulator.EmulatorView
import com.android.tools.idea.emulator.EmulatorViewRule
import com.android.tools.idea.emulator.FakeEmulator
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.EdtInvocationManager
import org.intellij.images.ui.ImageComponent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
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
  val ruleChain: RuleChain = RuleChain.outerRule(emulatorViewRule).around(EdtRule())

  private var nullableEmulator: FakeEmulator? = null
  private var nullableEmulatorView: EmulatorView? = null

  private var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  private var emulatorView: EmulatorView
    get() = nullableEmulatorView ?: throw IllegalStateException()
    set(value) { nullableEmulatorView = value }

  private val testRootDisposable
    get() = emulatorViewRule.testRootDisposable

  @get:Rule
  val portableUiFontRule = SetPortableUiFontRule()

  @Before
  fun setUp() {
    enableHeadlessDialogs(testRootDisposable)
    emulatorView = emulatorViewRule.newEmulatorView()
    emulator = emulatorViewRule.getFakeEmulator(emulatorView)
  }

  @Test
  fun testAction() {
    emulatorViewRule.executeAction("android.emulator.screenshot", emulatorView)

    waitForCondition(500, TimeUnit.SECONDS) { findScreenshotViewer() != null }
    val screenshotViewer = findScreenshotViewer()!!
    val rootPane = screenshotViewer.rootPane
    val ui = FakeUi(rootPane)
    val clipComboBox = ui.getComponent<JComboBox<*>>()

    clipComboBox.selectFirstMatch("Display Shape")
    EdtInvocationManager.dispatchAllInvocationEvents()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    var image = ui.getComponent<ImageComponent>().document.value
    assertAppearance(image, "WithoutFrame")
    clipComboBox.selectFirstMatch("Show Device Frame")
    EdtInvocationManager.dispatchAllInvocationEvents()
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
