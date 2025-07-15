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

import com.android.emulator.control.DisplayConfiguration
import com.android.emulator.control.Posture
import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.swing.DataManagerRule
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.findModelessDialog
import com.android.tools.adtui.swing.optionsAsString
import com.android.tools.adtui.swing.selectFirstMatch
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.ClipboardSynchronizationDisablementRule
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorToolWindowPanel
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.streaming.emulator.FakeEmulatorRule
import com.android.tools.idea.streaming.emulator.RunningEmulatorCatalog
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.ui.screenshot.DeviceScreenshotSettings
import com.android.tools.idea.ui.screenshot.ScreenshotViewer
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper.CLOSE_EXIT_CODE
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.IndexingTestUtil.Companion.waitUntilIndexesAreReady
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.EDT
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JComboBox
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.intellij.images.editor.ImageFileEditor
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tests for [EmulatorScreenshotAction]. */
@Suppress("OPT_IN_USAGE")
@RunsInEdt
class EmulatorScreenshotActionTest {

  private val projectRule = ProjectRule()
  private val emulatorRule = FakeEmulatorRule()

  @get:Rule
  val ruleChain = RuleChain(projectRule, DataManagerRule(projectRule), emulatorRule, ClipboardSynchronizationDisablementRule(),
                            EdtRule(), HeadlessDialogRule())

  private lateinit var avdFolder: Path
  private val emulator: FakeEmulator by lazy { emulatorRule.newEmulator(avdFolder) }
  private val panel: EmulatorToolWindowPanel by lazy { createWindowPanel() }
  // Fake window is necessary for the toolbars to be rendered.
  private val fakeUi: FakeUi by lazy { FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable) }
  private val project get() = projectRule.project
  private val testRootDisposable get() = projectRule.disposable

  @Before
  fun setUp() {
    service<DeviceScreenshotSettings>().loadState(DeviceScreenshotSettings())
  }

  @After
  fun tearDown() {
    do {
      val dialog = findModelessDialog<ScreenshotViewer>()
      dialog?.close(CLOSE_EXIT_CODE)
    } while (dialog != null)

    waitUntilIndexesAreReady(project) // Closing a screenshot viewer triggers deletion of the backing file and indexing.
    service<DeviceScreenshotSettings>().loadState(DeviceScreenshotSettings())
  }

  @Test
  fun testAction() {
    avdFolder = FakeEmulator.createPhoneAvd(emulatorRule.avdRoot)
    waitForDisplayViews(1)

    fakeUi.getComponent<ActionButton> { it.action.templateText == "Take Screenshot" }.let { fakeUi.clickOn(it) }

    val screenshotViewer = waitForScreenshotViewer()
    val ui = FakeUi(screenshotViewer.rootPane)
    val clipComboBox = ui.getComponent<JComboBox<*>>()

    screenshotViewer.waitForUpdateAndGetImage()
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Display Shape")
    assertAppearance(screenshotViewer.waitForUpdateAndGetImage(), "WithoutFrame")

    clipComboBox.selectFirstMatch("Show Device Frame")
    assertAppearance(screenshotViewer.waitForUpdateAndGetImage(), "WithFrame")
  }

  @Test
  fun testActionFoldableOpen() {
    avdFolder = FakeEmulator.createFoldableAvd(emulatorRule.avdRoot)
    waitForDisplayViews(1)

    fakeUi.getComponent<ActionButton> { it.action.templateText == "Take Screenshot" }.let { fakeUi.clickOn(it) }

    val screenshotViewer = waitForScreenshotViewer()
    val ui = FakeUi(screenshotViewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.optionsAsString()).contains("Show Device Frame")
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Display Shape")
    assertAppearance(screenshotViewer.waitForUpdateAndGetImage(), "FoldableOpen")
  }

  @Test
  fun testActionFoldableClosed() {
    avdFolder = FakeEmulator.createFoldableAvd(emulatorRule.avdRoot)
    emulator.setPosture(Posture.PostureValue.POSTURE_CLOSED)
    waitForDisplayViews(1)

    fakeUi.getComponent<ActionButton> { it.action.templateText == "Take Screenshot" }.let { fakeUi.clickOn(it) }


    val screenshotViewer = waitForScreenshotViewer()
    val ui = FakeUi(screenshotViewer.rootPane)

    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.optionsAsString()).contains("Show Device Frame")
    assertThat(clipComboBox.selectedItem?.toString()).isEqualTo("Display Shape")
    assertAppearance(screenshotViewer.waitForUpdateAndGetImage(), "FoldableClosed")
  }

  @Test
  fun testWearEmulatorWithoutSkinHasPlayCompatibleOption() {
    avdFolder = FakeEmulator.createWatchAvd(emulatorRule.avdRoot, skinFolder = null)
    waitForDisplayViews(1)

    fakeUi.getComponent<ActionButton> { it.action.templateText == "Take Screenshot" }.let { fakeUi.clickOn(it) }

    val screenshotViewer = waitForScreenshotViewer()
    val ui = FakeUi(screenshotViewer.rootPane)

    // This AVD has no skin definition, so the combo box should not have a "Show Device Frame" option.
    // It should have a "Play compatible" option.
    val clipComboBox = ui.getComponent<JComboBox<*>>()
    assertThat(clipComboBox.optionsAsString()).doesNotContain("Show Device Frame")
    assertThat(clipComboBox.optionsAsString()).contains("Play Store Compatible")
  }

  @Test
  fun testMultipleDisplayScreenshotsDisabled() {
    StudioFlags.MULTI_DISPLAY_SCREENSHOTS.overrideForTest(false, testRootDisposable)
    avdFolder = FakeEmulator.createPhoneAvd(emulatorRule.avdRoot)
    val displayId = 1
    runBlocking {
      emulator.changeSecondaryDisplays(
        listOf(DisplayConfiguration.newBuilder().setDisplay(displayId).setWidth(1080).setHeight(2340).build()))
    }
    waitForDisplayViews(2)

    fakeUi.getComponent<ActionButton> { it.action.templateText == "Take Screenshot" }.let { fakeUi.clickOn(it) }

    val screenshotViewerPrimary = waitForScreenshotViewer { !it.title.contains("Display") }
    assertAppearance(screenshotViewerPrimary.waitForUpdateAndGetImage(false), "PrimaryDisplay")
    assertThat(findScreenshotViewer{ it.title.contains("Display 1") }).isNull()
  }

  @Test
  fun testMultipleDisplayScreenshotsEnabled() {
    StudioFlags.MULTI_DISPLAY_SCREENSHOTS.overrideForTest(true, testRootDisposable)
    avdFolder = FakeEmulator.createPhoneAvd(emulatorRule.avdRoot)
    val displayId = 1
    runBlocking {
      emulator.changeSecondaryDisplays(
          listOf(DisplayConfiguration.newBuilder().setDisplay(displayId).setWidth(1080).setHeight(2340).build()))
    }
    waitForDisplayViews(2)

    fakeUi.getComponent<ActionButton> { it.action.templateText == "Take Screenshot" }.let { fakeUi.clickOn(it) }

    val screenshotViewerPrimary = waitForScreenshotViewer { !it.title.contains("Display") }
    val screenshotViewerSecondary = waitForScreenshotViewer { it.title.contains("Display 1") }
    assertAppearance(screenshotViewerPrimary.waitForUpdateAndGetImage(false), "PrimaryDisplay")
    assertAppearance(screenshotViewerSecondary.waitForUpdateAndGetImage(false), "SecondaryDisplay")
  }

  // TODO(sprigogin): Add a test for the Take Screenshot action invoked from a context menu.

  private fun createWindowPanel(): EmulatorToolWindowPanel {
    emulator.start()
    val catalog = RunningEmulatorCatalog.getInstance()
    val emulators = runBlocking { catalog.updateNow().await() }
    assertThat(emulators).hasSize(1)
    val emulatorController = emulators.first()
    val panel = EmulatorToolWindowPanel(testRootDisposable, project, emulatorController)
    waitForCondition(5.seconds) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    panel.size = Dimension(400, 600)
    panel.createContent(true)
    return panel
  }

  private fun waitForDisplayViews(numViews: Int) {
    waitForCondition(2.seconds) { fakeUi.findAllComponents<EmulatorView>().size >= numViews }
    fakeUi.findAllComponents<EmulatorView>().forEach { it.waitForFrame(1U, 2.seconds) }
  }

  private fun EmulatorView.waitForFrame(frame: UInt, timeout: Duration = 2.seconds) {
    waitForCondition(timeout) { renderAndGetFrameNumber(this) >= frame }
  }

  private fun renderAndGetFrameNumber(emulatorView: EmulatorView): UInt {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return emulatorView.frameNumber
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

  private fun assertAppearance(image: BufferedImage, goldenImageName: String) {
    val scaledDownImage = ImageUtils.scale(image, 0.1)
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), scaledDownImage, 0.0)
  }

  @Suppress("SameParameterValue")
  private fun getGoldenFile(name: String): Path =
      TestUtils.resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/${name}.png")
}

private fun ScreenshotViewer.waitForUpdateAndGetImage(expectTransparentCorner: Boolean = true): BufferedImage {
  EDT.dispatchAllInvocationEvents()
  PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  val fileEditor = fileEditor()
  waitForCondition(2.seconds) {
    fileEditor.imageEditor.document.value?.let {
      (!expectTransparentCorner || it.isCornerTransparent()) && it.isSame(fileEditor.file.readImage())
    } ?: false
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
  catch (_: IOException) {
    null
  }
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/EmulatorScreenshotActionTest/golden"
