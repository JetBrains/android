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
package com.android.tools.idea.device

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.SetPortableUiFontRule
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.device.AndroidKeyEventActionType.ACTION_DOWN
import com.android.tools.idea.device.AndroidKeyEventActionType.ACTION_DOWN_AND_UP
import com.android.tools.idea.device.AndroidKeyEventActionType.ACTION_UP
import com.android.tools.idea.emulator.DeviceMirroringSettings
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.swing.JViewport

/**
 * Tests for [DeviceToolWindowPanel], [DeviceDisplayPanel] and toolbar actions that produce Android key events.
 */
@RunsInEdt
class DeviceToolWindowPanelTest {
  companion object {
    @JvmField
    @ClassRule
    val iconRule = IconLoaderRule() // Enable icon loading in a headless test environment.
  }

  private val agentRule = FakeScreenSharingAgentRule()

  @get:Rule
  val ruleChain = RuleChain(agentRule, SetPortableUiFontRule(), EdtRule())

  private val device by lazy { agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280), "arm64-v8a") }
  private val panel: DeviceToolWindowPanel by lazy { createToolWindowPanel() }
  private val ui: FakeUi by lazy { FakeUi(panel, createFakeWindow = true) } // Fake window is necessary for the toolbars to be rendered.
  private val testRootDisposable get() = agentRule.testRootDisposable
  private val agent: FakeScreenSharingAgent get() = device.agent

  private var savedClipboardSynchronizationState = false

  @Before
  fun setUp() {
    HeadlessDataManager.fallbackToProductionDataManager(testRootDisposable)
    savedClipboardSynchronizationState = DeviceMirroringSettings.getInstance().synchronizeClipboard
    DeviceMirroringSettings.getInstance().synchronizeClipboard = false
  }

  @After
  fun tearDown() {
    DeviceMirroringSettings.getInstance().synchronizeClipboard = savedClipboardSynchronizationState
  }

  @Test
  fun testAppearanceAndToolbarActions() {
    if (SystemInfo.isWindows) {
      return // For some unclear reason the test fails on Windows with java.lang.UnsatisfiedLinkError: no jniavcodec in java.library.path.
    }
    assertThat(panel.deviceView).isNull()

    panel.createContent(false)
    waitForCondition(15, TimeUnit.SECONDS) { agent.running }
    assertThat(panel.deviceView).isNotNull()

    // Check appearance.
    ui.updateToolbars()
    ui.layoutAndDispatchEvents()
    getNextControlMessageAndWaitForFrame(panel)
    assertAppearance("AppearanceAndToolbarActions1", maxPercentDifferent = 0.08)
    assertThat(panel.preferredFocusableComponent).isEqualTo(panel.deviceView)
    assertThat(panel.isClosable).isFalse()
    assertThat(panel.icon).isNotNull()

    // Check push button actions.
    val pushButtonCases = listOf(
      Pair("Power", AKEYCODE_POWER),
      Pair("Volume Up", AKEYCODE_VOLUME_UP),
      Pair("Volume Down", AKEYCODE_VOLUME_DOWN),
    )
    for (case in pushButtonCases) {
      val button = ui.getComponent<ActionButton> { it.action.templateText == case.first }
      ui.mousePressOn(button)
      assertThat(getNextControlMessageAndWaitForFrame(panel)).isEqualTo(KeyEventMessage(ACTION_DOWN, case.second, 0))
      ui.mouseRelease()
      assertThat(getNextControlMessageAndWaitForFrame(panel)).isEqualTo(KeyEventMessage(ACTION_UP, case.second, 0))
    }

    // Check keypress actions.
    val keypressCases = listOf(
      Pair("Back", AKEYCODE_BACK),
      Pair("Home", AKEYCODE_HOME),
      Pair("Overview", AKEYCODE_APP_SWITCH),
    )
    for (case in keypressCases) {
      val button = ui.getComponent<ActionButton> { it.action.templateText == case.first }
      ui.mouseClickOn(button)
      assertThat(getNextControlMessageAndWaitForFrame(panel)).isEqualTo(KeyEventMessage(ACTION_DOWN_AND_UP, case.second, 0))
    }

    panel.destroyContent()
    assertThat(panel.deviceView).isNull()
    waitForCondition(2, TimeUnit.SECONDS) { !agent.running }
  }

  @Test
  fun testZoom() {
    if (SystemInfo.isWindows) {
      return // For some unclear reason the test fails on Windows with java.lang.UnsatisfiedLinkError: no jniavcodec in java.library.path.
    }
    assertThat(panel.deviceView).isNull()

    panel.createContent(false)
    assertThat(panel.deviceView).isNotNull()

    ui.updateToolbars()
    ui.layoutAndDispatchEvents()
    getNextControlMessageAndWaitForFrame(panel)

    var deviceView = panel.deviceView!!
    // Zoom in.
    deviceView.zoom(ZoomType.IN)
    ui.layoutAndDispatchEvents()
    assertThat(deviceView.scale).isWithin(0.0001).of(0.25)
    assertThat(deviceView.preferredSize).isEqualTo(Dimension(270, 570))
    val viewport = deviceView.parent as JViewport
    assertThat(viewport.viewSize).isEqualTo(Dimension(270, 570))
    // Scroll to the bottom.
    val scrollPosition = Point(viewport.viewPosition.x, viewport.viewSize.height - viewport.height)
    viewport.viewPosition = scrollPosition

    // Recreate panel content.
    val uiState = panel.destroyContent()
    assertThat(panel.deviceView).isNull()
    waitForCondition(2, TimeUnit.SECONDS) { !agent.running }
    panel.createContent(false, uiState)
    assertThat(panel.deviceView).isNotNull()
    deviceView = panel.deviceView!!
    ui.layoutAndDispatchEvents()
    getNextControlMessageAndWaitForFrame(panel)

    // Check that zoom level and scroll position are restored.
    assertThat(deviceView.scale).isWithin(0.0001).of(0.25)
    assertThat(viewport.viewSize).isEqualTo(Dimension(270, 570))
    assertThat(viewport.viewPosition).isEqualTo(scrollPosition)

    panel.destroyContent()
    assertThat(panel.deviceView).isNull()
    waitForCondition(2, TimeUnit.SECONDS) { !agent.running }
  }

  private fun FakeUi.mousePressOn(component: Component) {
    val location: Point = getPosition(component)
    mouse.press(location.x, location.y)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // Allow events to propagate.
  }

  private fun FakeUi.mouseRelease() {
    mouse.release()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // Allow events to propagate.
  }

  private fun FakeUi.mouseClickOn(component: Component) {
    val location: Point = getPosition(component)
    mouse.click(location.x, location.y)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // Allow events to propagate.
  }

  private fun createToolWindowPanel(): DeviceToolWindowPanel {
    val panel = DeviceToolWindowPanel(agentRule.project, device.serialNumber, device.deviceState.cpuAbi, "null")
    Disposer.register(testRootDisposable) {
      if (panel.deviceView != null) {
        panel.destroyContent()
      }
    }
    panel.size = Dimension(230, 300)
    panel.zoomToolbarVisible = true
    return panel
  }

  private fun getNextControlMessageAndWaitForFrame(devicePanel: DeviceToolWindowPanel): ControlMessage {
    val message = agent.getNextControlMessage(500, TimeUnit.SECONDS)
    // Wait for all video frames to be received.
    waitForCondition(2, TimeUnit.SECONDS) { ui.render(); devicePanel.frameNumber == agent.frameNumber }
    return message
  }

  @Suppress("SameParameterValue")
  private fun assertAppearance(goldenImageName: String, maxPercentDifferent: Double = 0.0) {
    ui.layoutAndDispatchEvents()
    ui.updateToolbars()
    val image = ui.render()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, maxPercentDifferent)
  }

  private fun getGoldenFile(name: String): Path {
    return TestUtils.resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/${name}.png")
  }

  private val DeviceToolWindowPanel.deviceView
    get() = getData(DEVICE_VIEW_KEY.name) as DeviceView?
  private val DeviceToolWindowPanel.frameNumber
    get() = deviceView!!.frameNumber
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/emulator/testData/DeviceToolWindowPanelTest/golden"
