/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.testutils.TestUtils
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.setPortableUiFont
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.emulator.FakeEmulator.GrpcCallRecord
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JViewport

/**
 * Tests for [EmulatorToolWindowPanel] and some of the toolbar actions.
 */
@RunsInEdt
class EmulatorToolWindowPanelTest {
  companion object {
    @JvmField
    @ClassRule
    val iconRule = IconLoaderRule()
  }

  private val projectRule = AndroidProjectRule.inMemory()
  private val emulatorRule = FakeEmulatorRule()
  private var nullableEmulator: FakeEmulator? = null
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(emulatorRule).around(EdtRule())

  private var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  @Before
  fun setUp() {
    setPortableUiFont()
    // Necessary to properly update button states.
    HeadlessDataManager.fallbackToProductionDataManager(projectRule.fixture.testRootDisposable)
  }

  @Test
  fun testEmulatorToolWindowPanel() {
    val panel = createWindowPanel()
    val ui = FakeUi(panel)

    assertThat(panel.emulatorView).isNull()

    panel.createContent(true)
    val emulatorView = panel.emulatorView ?: throw AssertionError()

    // Check appearance.
    panel.zoomToolbarVisible = true
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    panel.size = Dimension(400, 600)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 363 height: 520")
    ui.updateToolbars()
    assertAppearance(ui, "image1")

    // Check EmulatorPowerButtonAction.
    var button = ui.getComponent<ActionButton> { it.action.templateText == "Power" }
    ui.mousePressOn(button)
    var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""key: "Power"""")
    ui.mouseRelease()
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keyup key: "Power"""")

    // Check EmulatorVolumeUpButtonAction.
    button = ui.getComponent<ActionButton> { it.action.templateText == "Volume Up" }
    ui.mousePressOn(button)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""key: "AudioVolumeUp"""")
    ui.mouseRelease()
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keyup key: "AudioVolumeUp"""")

    // Check EmulatorVolumeDownButtonAction.
    button = ui.getComponent<ActionButton> { it.action.templateText == "Volume Down" }
    ui.mousePressOn(button)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""key: "AudioVolumeDown"""")
    ui.mouseRelease()
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keyup key: "AudioVolumeDown"""")

    assertThat(streamScreenshotCall.completion.isCancelled).isFalse()

    panel.destroyContent()
    assertThat(panel.emulatorView).isNull()
    waitForCondition(2, TimeUnit.SECONDS) { streamScreenshotCall.completion.isCancelled }
  }

  @Test
  fun testZoom() {
    val panel = createWindowPanel()
    val ui = FakeUi(panel)

    assertThat(panel.emulatorView).isNull()

    panel.createContent(true)
    var emulatorView = panel.emulatorView ?: throw AssertionError()

    panel.zoomToolbarVisible = true
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    panel.size = Dimension(400, 600)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 363 height: 520")
    ui.updateToolbars()

    // Zoom in.
    emulatorView.zoom(ZoomType.IN)
    ui.layoutAndDispatchEvents()
    assertThat(emulatorView.scale).isWithin(0.0001).of(0.25)
    assertThat(emulatorView.preferredSize).isEqualTo(Dimension(396, 811))
    val viewport = emulatorView.parent as JViewport
    assertThat(viewport.viewSize).isEqualTo(Dimension(400, 811))
    // Scroll to the bottom.
    val scrollPosition = Point(viewport.viewPosition.x, viewport.viewSize.height - viewport.height)
    viewport.viewPosition = scrollPosition

    // Recreate panel content.
    panel.destroyContent()
    panel.createContent(true)
    emulatorView = panel.emulatorView ?: throw AssertionError()
    ui.layoutAndDispatchEvents()

    // Check that zoom level and scroll position are restored.
    assertThat(emulatorView.scale).isWithin(0.0001).of(0.25)
    assertThat(viewport.viewSize).isEqualTo(Dimension(400, 811))
    assertThat(viewport.viewPosition).isEqualTo(scrollPosition)

    panel.destroyContent()
  }

  private fun FakeUi.mousePressOn(component: Component) {
    val location: Point = getPosition(component)
    mouse.press(location.x, location.y)
    // Allow events to propagate.
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  private fun FakeUi.mouseRelease() {
    mouse.release()
    // Allow events to propagate.
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  private fun getStreamScreenshotCallAndWaitForFrame(panel: EmulatorToolWindowPanel, frameNumber: Int): GrpcCallRecord {
    val call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamScreenshot")
    panel.waitForFrame(frameNumber, 2, TimeUnit.SECONDS)
    return call
  }

  private fun createWindowPanel(): EmulatorToolWindowPanel {
    val catalog = RunningEmulatorCatalog.getInstance()
    val tempFolder = emulatorRule.root.toPath()
    emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), 8554)
    emulator.start()
    val emulators = catalog.updateNow().get()
    assertThat(emulators).hasSize(1)
    val emulatorController = emulators.first()
    val panel = EmulatorToolWindowPanel(emulatorController)
    Disposer.register(projectRule.fixture.testRootDisposable, panel::destroyContent)
    waitForCondition(2, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    emulator.getNextGrpcCall(2, TimeUnit.SECONDS) // Skip the initial "getStatus" call.
    return panel
  }

  @Throws(TimeoutException::class)
  private fun EmulatorToolWindowPanel.waitForFrame(frame: Int, timeout: Long, unit: TimeUnit) {
    waitForCondition(timeout, unit) { emulatorView!!.frameNumber >= frame }
  }

  private val EmulatorToolWindowPanel.emulatorView
    get() = getData(EMULATOR_VIEW_KEY.name) as EmulatorView?

  private fun assertAppearance(ui: FakeUi, goldenImageName: String) {
    val image = ui.render()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, 0.04)
  }

  private fun getGoldenFile(name: String): File {
    return TestUtils.getWorkspaceRoot().toPath().resolve("${GOLDEN_FILE_PATH}/${name}.png").toFile()
  }
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/emulator/testData/EmulatorToolWindowPanelTest/golden"
