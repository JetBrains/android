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
package com.android.tools.idea.streaming.device

import com.android.adblib.DevicePropertyNames.RO_BUILD_CHARACTERISTICS
import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt
import com.android.testutils.TestUtils
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN_AND_UP
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_UP
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule.FakeDevice
import com.android.tools.idea.testing.registerServiceInstance
import com.android.tools.idea.ui.screenrecording.ScreenRecordingSupportedCache
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestDataProvider
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
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
  val ruleChain = RuleChain(agentRule, PortableUiFontRule(), EdtRule())

  private lateinit var device: FakeDevice
  private val panel: DeviceToolWindowPanel by lazy { createToolWindowPanel() }
  private val fakeUi: FakeUi by lazy { FakeUi(panel, createFakeWindow = true) } // Fake window is necessary for the toolbars to be rendered.
  private val project get() = agentRule.project
  private val testRootDisposable get() = agentRule.testRootDisposable
  private val agent: FakeScreenSharingAgent get() = device.agent

  private var savedClipboardSynchronizationState = false

  @Before
  fun setUp() {
    HeadlessDataManager.fallbackToProductionDataManager(testRootDisposable)
    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider(TestDataProvider(project), testRootDisposable)
    val mockScreenRecordingCache = MockitoKt.mock<ScreenRecordingSupportedCache>()
    MockitoKt.whenever(mockScreenRecordingCache.isScreenRecordingSupported(MockitoKt.any(), Mockito.anyInt())).thenReturn(true)
    project.registerServiceInstance(ScreenRecordingSupportedCache::class.java, mockScreenRecordingCache, testRootDisposable)
    savedClipboardSynchronizationState = DeviceMirroringSettings.getInstance().synchronizeClipboard
    DeviceMirroringSettings.getInstance().synchronizeClipboard = false
  }

  @After
  fun tearDown() {
    DeviceMirroringSettings.getInstance().synchronizeClipboard = savedClipboardSynchronizationState
  }

  @Test
  fun testAppearanceAndToolbarActions() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280), "arm64-v8a")
    assertThat(panel.deviceView).isNull()

    panel.createContent(false)
    assertThat(panel.deviceView).isNotNull()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(5, TimeUnit.SECONDS) { agent.isRunning && panel.isConnected }

    // Check appearance.
    fakeUi.updateToolbars()
    waitForFrame()
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
      val button = fakeUi.getComponent<ActionButton> { it.action.templateText == case.first }
      fakeUi.mousePressOn(button)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_DOWN, case.second, 0))
      fakeUi.mouseRelease()
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_UP, case.second, 0))
    }

    // Check keypress actions.
    val keypressCases = listOf(
      Pair("Back", AKEYCODE_BACK),
      Pair("Home", AKEYCODE_HOME),
      Pair("Overview", AKEYCODE_APP_SWITCH),
    )
    for (case in keypressCases) {
      val button = fakeUi.getComponent<ActionButton> { it.action.templateText == case.first }
      fakeUi.mouseClickOn(button)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_DOWN_AND_UP, case.second, 0))
    }

    // Check that the Wear OS-specific buttons are hidden.
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Button 1" }).isNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Button 2" }).isNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Palm" }).isNull()

    panel.destroyContent()
    assertThat(panel.deviceView).isNull()
    waitForCondition(2, TimeUnit.SECONDS) { !agent.isRunning }
  }

  @Test
  fun testWearToolbarActions() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    device = agentRule.connectDevice("Pixel Watch", 30, Dimension(454, 454), "arm64-v8a",
                                     mapOf(RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))
    panel.createContent(false)
    assertThat(panel.deviceView).isNotNull()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(5, TimeUnit.SECONDS) { agent.isRunning && panel.isConnected }

    // Check appearance.
    fakeUi.updateToolbars()
    fakeUi.layoutAndDispatchEvents()
    waitForFrame()
    assertThat(panel.preferredFocusableComponent).isEqualTo(panel.deviceView)
    assertThat(panel.isClosable).isFalse()
    assertThat(panel.icon).isNotNull()

    // Check push button actions.
    val pushButtonCases = listOf(
      Pair("Button 1", AKEYCODE_POWER)
    )
    for (case in pushButtonCases) {
      val button = fakeUi.getComponent<ActionButton> { it.action.templateText == case.first }
      fakeUi.mousePressOn(button)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_DOWN, case.second, 0))
      fakeUi.mouseRelease()
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_UP, case.second, 0))
    }

    // Check keypress actions.
    val keypressCases = listOf(
      Pair("Button 2", AKEYCODE_STEM_PRIMARY),
      Pair("Palm", AKEYCODE_SLEEP),
      Pair("Back", AKEYCODE_BACK),
    )
    for (case in keypressCases) {
      val button = fakeUi.getComponent<ActionButton> { it.action.templateText == case.first }
      fakeUi.mouseClickOn(button)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_DOWN_AND_UP, case.second, 0))
    }

    // Check that the buttons not applicable to Wear OS 3 are hidden.
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Power" }).isNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Volume Up" }).isNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Volume Down" }).isNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Rotate Left" }).isNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Rotate Right" }).isNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Home" }).isNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Overview" }).isNull()

    panel.destroyContent()
    assertThat(panel.deviceView).isNull()
    waitForCondition(2, TimeUnit.SECONDS) { !agent.isRunning }
  }

  @Test
  fun testZoom() {
    if (!isFFmpegAvailableToTest()) {
      return
    }
    device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280), "arm64-v8a")
    assertThat(panel.deviceView).isNull()

    panel.createContent(false)
    assertThat(panel.deviceView).isNotNull()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(10, TimeUnit.SECONDS) { agent.isRunning && panel.isConnected }

    fakeUi.updateToolbars()
    fakeUi.layoutAndDispatchEvents()
    waitForFrame()

    var deviceView = panel.deviceView!!
    // Zoom in.
    deviceView.zoom(ZoomType.IN)
    fakeUi.layoutAndDispatchEvents()
    assertThat(deviceView.scale).isWithin(0.0001).of(0.25)
    assertThat(deviceView.preferredSize).isEqualTo(Dimension(270, 570))
    val viewport = deviceView.parent as JViewport
    assertThat(viewport.viewSize).isEqualTo(Dimension(280, 570))
    // Scroll to the bottom.
    val scrollPosition = Point(viewport.viewPosition.x, viewport.viewSize.height - viewport.height)
    viewport.viewPosition = scrollPosition

    // Recreate panel content.
    val uiState = panel.destroyContent()
    assertThat(panel.deviceView).isNull()
    waitForCondition(2, TimeUnit.SECONDS) { !agent.isRunning }
    panel.createContent(false, uiState)
    assertThat(panel.deviceView).isNotNull()
    deviceView = panel.deviceView!!
    fakeUi.layoutAndDispatchEvents()
    waitForCondition(5, TimeUnit.SECONDS) { agent.isRunning && panel.isConnected }
    waitForFrame()

    // Check that zoom level and scroll position are restored.
    assertThat(deviceView.scale).isWithin(0.0001).of(0.25)
    assertThat(viewport.viewSize).isEqualTo(Dimension(280, 570))
    assertThat(viewport.viewPosition).isEqualTo(scrollPosition)

    panel.destroyContent()
    assertThat(panel.deviceView).isNull()
    waitForCondition(2, TimeUnit.SECONDS) { !agent.isRunning }
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
    val panel = DeviceToolWindowPanel(project, device.serialNumber, device.deviceState.cpuAbi, "Test device", device.properties)
    Disposer.register(testRootDisposable) {
      if (panel.deviceView != null) {
        panel.destroyContent()
      }
    }
    panel.size = Dimension(280, 300)
    panel.zoomToolbarVisible = true
    return panel
  }

  private fun getNextControlMessageAndWaitForFrame(): ControlMessage {
    val message = agent.getNextControlMessage(2, TimeUnit.SECONDS)
    waitForFrame()
    return message
  }

  /** Waits for all video frames to be received. */
  private fun waitForFrame() {
    waitForCondition(5, TimeUnit.SECONDS) { panel.isConnected && agent.frameNumber > 0 && renderAndGetFrameNumber() == agent.frameNumber }
  }

  private fun renderAndGetFrameNumber(): Int {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return panel.frameNumber
  }


  @Suppress("SameParameterValue")
  private fun assertAppearance(goldenImageName: String, maxPercentDifferent: Double = 0.0) {
    fakeUi.layoutAndDispatchEvents()
    fakeUi.updateToolbars()
    val image = fakeUi.render()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, maxPercentDifferent)
  }

  private fun getGoldenFile(name: String): Path {
    return TestUtils.resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/${name}.png")
  }

  private val DeviceToolWindowPanel.deviceView
    get() = getData(DEVICE_VIEW_KEY.name) as DeviceView?
  private val DeviceToolWindowPanel.isConnected
    get() = (getData(DEVICE_VIEW_KEY.name) as? DeviceView)?.isConnected ?: false
  private val DeviceToolWindowPanel.frameNumber
    get() = deviceView!!.frameNumber
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/DeviceToolWindowPanelTest/golden"
