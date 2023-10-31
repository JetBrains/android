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
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.createTestEvent
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN_AND_UP
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_UP
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule.FakeDevice
import com.android.tools.idea.streaming.device.actions.DeviceFoldingAction
import com.android.tools.idea.streaming.updateAndGetActionPresentation
import com.android.tools.idea.testing.registerServiceInstance
import com.android.tools.idea.ui.screenrecording.ScreenRecordingSupportedCache
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestDataProvider
import com.intellij.ui.LayeredIcon
import icons.StudioIcons
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.anyInt
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.KEY_RELEASED
import java.awt.event.KeyEvent.VK_P
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS
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
  val ruleChain = RuleChain(agentRule, ClipboardSynchronizationDisablementRule(), PortableUiFontRule(), EdtRule())

  private lateinit var device: FakeDevice
  private val panel: DeviceToolWindowPanel by lazy { createToolWindowPanel() }
  private val fakeUi: FakeUi by lazy { FakeUi(panel, createFakeWindow = true) } // Fake window is necessary for the toolbars to be rendered.
  private val project get() = agentRule.project
  private val testRootDisposable get() = agentRule.disposable
  private val agent: FakeScreenSharingAgent get() = device.agent

  @Before
  fun setUp() {
    HeadlessDataManager.fallbackToProductionDataManager(testRootDisposable) // Necessary to properly update toolbar button states.
    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider(TestDataProvider(project), testRootDisposable)
    val mockScreenRecordingCache = mock<ScreenRecordingSupportedCache>()
    whenever(mockScreenRecordingCache.isScreenRecordingSupported(any(), anyInt())).thenReturn(true)
    project.registerServiceInstance(ScreenRecordingSupportedCache::class.java, mockScreenRecordingCache, testRootDisposable)
  }

  @Test
  fun testAppearanceAndToolbarActions() {
    assumeFFmpegAvailable()
    device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    assertThat(panel.deviceView).isNull()

    panel.createContent(false)
    assertThat(panel.deviceView).isNotNull()
    assertThat((panel.icon as LayeredIcon).getIcon(0)).isEqualTo(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(5, SECONDS) { agent.isRunning && panel.isConnected }

    // Check appearance.
    fakeUi.updateToolbars()
    waitForFrame()
    assertAppearance("AppearanceAndToolbarActions1", maxPercentDifferentMac = 0.06, maxPercentDifferentWindows = 0.06)
    assertThat(panel.preferredFocusableComponent).isEqualTo(panel.deviceView)
    if (StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.get()) {
      assertThat(panel.isClosable).isTrue()
    }
    else {
      assertThat(panel.isClosable).isFalse()
    }
    assertThat(panel.icon).isNotNull()

    // Check push button actions.
    val pushButtonCases = listOf(
      Pair("Power", AKEYCODE_POWER),
      Pair("Volume Up", AKEYCODE_VOLUME_UP),
      Pair("Volume Down", AKEYCODE_VOLUME_DOWN),
      Pair("Back", AKEYCODE_BACK),
      Pair("Home", AKEYCODE_HOME),
      Pair("Overview", AKEYCODE_APP_SWITCH),
    )
    for (case in pushButtonCases) {
      val button = fakeUi.getComponent<ActionButton> { it.action.templateText == case.first }
      fakeUi.mousePressOn(button)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_DOWN, case.second, 0))
      fakeUi.mouseRelease()
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_UP, case.second, 0))
    }

    // Check DevicePowerButtonAction invoked by a keyboard shortcut.
    var action = ActionManager.getInstance().getAction("android.device.power.button")
    var keyEvent = KeyEvent(panel, KEY_RELEASED, System.currentTimeMillis(), CTRL_DOWN_MASK, VK_P, KeyEvent.CHAR_UNDEFINED)
    val dataContext = DataManager.getInstance().getDataContext(panel.deviceView)
    action.actionPerformed(AnActionEvent.createFromAnAction(action, keyEvent, ActionPlaces.KEYBOARD_SHORTCUT, dataContext))
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_DOWN_AND_UP, AKEYCODE_POWER, 0))

    // Check DevicePowerAndVolumeUpButtonAction invoked by a keyboard shortcut.
    action = ActionManager.getInstance().getAction("android.device.power.and.volume.up.button")
    keyEvent = KeyEvent(panel, KEY_RELEASED, System.currentTimeMillis(), CTRL_DOWN_MASK or SHIFT_DOWN_MASK, VK_P, KeyEvent.CHAR_UNDEFINED)
    action.actionPerformed(AnActionEvent.createFromAnAction(action, keyEvent, ActionPlaces.KEYBOARD_SHORTCUT, dataContext))
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_DOWN, AKEYCODE_VOLUME_UP, 0))
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_DOWN_AND_UP, AKEYCODE_POWER, 0))
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_UP, AKEYCODE_VOLUME_UP, 0))

    // Check that the Wear OS-specific buttons are hidden.
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Button 1" }).isNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Button 2" }).isNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Palm" }).isNull()

    panel.destroyContent()
    assertThat(panel.deviceView).isNull()
    waitForCondition(2, SECONDS) { !agent.videoStreamActive }
  }

  @Test
  fun testWearToolbarActions() {
    assumeFFmpegAvailable()
    device = agentRule.connectDevice("Pixel Watch", 30, Dimension(454, 454),
                                     additionalDeviceProperties = mapOf(RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))
    panel.createContent(false)
    assertThat(panel.deviceView).isNotNull()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(5, SECONDS) { agent.isRunning && panel.isConnected }

    // Check appearance.
    fakeUi.updateToolbars()
    fakeUi.layoutAndDispatchEvents()
    waitForFrame()
    assertThat(panel.preferredFocusableComponent).isEqualTo(panel.deviceView)
    if (StudioFlags.DEVICE_MIRRORING_ADVANCED_TAB_CONTROL.get()) {
      assertThat(panel.isClosable).isTrue()
    }
    else {
      assertThat(panel.isClosable).isFalse()
    }
    assertThat((panel.icon as LayeredIcon).getIcon(0)).isEqualTo(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR)

    // Check push button actions.
    val pushButtonCases = listOf(
      Pair("Button 1", AKEYCODE_POWER),
      Pair("Button 2", AKEYCODE_STEM_PRIMARY),
      Pair("Back", AKEYCODE_BACK),
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
      Pair("Palm", AKEYCODE_SLEEP),
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

    // Check that the actions not applicable to Wear OS 3 cannot be invoked by keyboard shortcuts.
    assertThat(updateAndGetActionPresentation("android.device.power.button", panel.deviceView!!, project).isEnabled).isFalse()
    assertThat(updateAndGetActionPresentation("android.device.volume.up.button", panel.deviceView!!, project).isEnabled).isFalse()
    assertThat(updateAndGetActionPresentation("android.device.volume.down.button", panel.deviceView!!, project).isEnabled).isFalse()
    assertThat(updateAndGetActionPresentation("android.device.rotate.left", panel.deviceView!!, project).isEnabled).isFalse()
    assertThat(updateAndGetActionPresentation("android.device.rotate.right", panel.deviceView!!, project).isEnabled).isFalse()
    assertThat(updateAndGetActionPresentation("android.device.home.button", panel.deviceView!!, project).isEnabled).isFalse()
    assertThat(updateAndGetActionPresentation("android.device.overview.button", panel.deviceView!!, project).isEnabled).isFalse()

    panel.destroyContent()
    assertThat(panel.deviceView).isNull()
    waitForCondition(2, SECONDS) { !agent.videoStreamActive }
  }

  @Test
  fun testFolding() {
    assumeFFmpegAvailable()
    device = agentRule.connectDevice("Pixel Fold", 33, Dimension(2208, 1840), foldedSize = Dimension(1080, 2092))

    panel.createContent(false)
    val deviceView = panel.deviceView!!

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(5, SECONDS) { agent.isRunning && panel.isConnected }

    // Check appearance.
    fakeUi.updateToolbars()
    waitForFrame()

    val foldingGroup = ActionManager.getInstance().getAction("android.device.postures") as ActionGroup
    val event = createTestEvent(deviceView, project, ActionPlaces.TOOLBAR)
    waitForCondition(2, SECONDS) { foldingGroup.update(event); event.presentation.isVisible}
    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(event.presentation.text).isEqualTo("Fold/Unfold (currently Open)")
    val foldingActions = foldingGroup.getChildren(event)
    assertThat(foldingActions).asList().containsExactly(
        DeviceFoldingAction(FoldingState(0, "Closed", true)),
        DeviceFoldingAction(FoldingState(1, "Tent", true)),
        DeviceFoldingAction(FoldingState(2, "Half-Open", true)),
        DeviceFoldingAction(FoldingState(3, "Open", true)),
        DeviceFoldingAction(FoldingState(4, "Rear Display", true)),
        DeviceFoldingAction(FoldingState(5, "Both Displays", true)),
        DeviceFoldingAction(FoldingState(6, "Flipped", true)))
    for (action in foldingActions) {
      action.update(event)
      assertThat(event.presentation.isEnabled).isTrue()
      assertThat(event.presentation.isVisible).isTrue()
    }
    assertThat(deviceView.deviceDisplaySize).isEqualTo(Dimension(2208, 1840))

    val nextFrameNumber = panel.frameNumber + 1
    val closingAction = foldingActions[0]
    closingAction.actionPerformed(event)
    waitForCondition(2, SECONDS) { foldingGroup.update(event); event.presentation.text == "Fold/Unfold (currently Closed)"}
    waitForFrame(nextFrameNumber)
    assertThat(deviceView.deviceDisplaySize).isEqualTo(Dimension(1080, 2092))
  }

  @Test
  fun testZoom() {
    assumeFFmpegAvailable()
    device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    assertThat(panel.deviceView).isNull()

    panel.createContent(false)
    assertThat(panel.deviceView).isNotNull()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(10, SECONDS) { agent.isRunning && panel.isConnected }

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
    waitForCondition(2, SECONDS) { !agent.videoStreamActive }
    panel.createContent(false, uiState)
    assertThat(panel.deviceView).isNotNull()
    deviceView = panel.deviceView!!
    fakeUi.layoutAndDispatchEvents()
    waitForCondition(5, SECONDS) { agent.videoStreamActive && panel.isConnected }
    waitForFrame()

    // Check that zoom level and scroll position are restored.
    assertThat(deviceView.scale).isWithin(0.0001).of(0.25)
    assertThat(viewport.viewSize).isEqualTo(Dimension(280, 570))
    assertThat(viewport.viewPosition).isEqualTo(scrollPosition)

    panel.destroyContent()
    assertThat(panel.deviceView).isNull()
    waitForCondition(2, SECONDS) { !agent.videoStreamActive }
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
    val deviceClient =
        DeviceClient(testRootDisposable, device.serialNumber, device.handle, device.configuration, device.deviceState.cpuAbi, project)
    val panel = DeviceToolWindowPanel(project, deviceClient)
    // The panel has to be destroyed before disposal of DeviceClient.
    val disposable = Disposer.newDisposable()
    Disposer.register(testRootDisposable, disposable)
    Disposer.register(disposable) {
      if (panel.deviceView != null) {
        panel.destroyContent()
      }
    }
    panel.size = Dimension(280, 300)
    panel.zoomToolbarVisible = true
    return panel
  }

  private fun getNextControlMessageAndWaitForFrame(): ControlMessage {
    val message = agent.getNextControlMessage(2, SECONDS)
    waitForFrame()
    return message
  }

  /** Waits for all video frames to be received after the given one. */
  private fun waitForFrame(minFrameNumber: Int = 1) {
    waitForCondition(2, SECONDS) {
      panel.isConnected && agent.frameNumber >= minFrameNumber && renderAndGetFrameNumber() == agent.frameNumber
    }
  }

  private fun renderAndGetFrameNumber(): Int {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return panel.frameNumber
  }

  @Suppress("SameParameterValue")
  private fun assertAppearance(goldenImageName: String,
                               maxPercentDifferentLinux: Double = 0.0003,
                               maxPercentDifferentMac: Double = 0.0003,
                               maxPercentDifferentWindows: Double = 0.0003) {
    fakeUi.layoutAndDispatchEvents()
    fakeUi.updateToolbars()
    val image = fakeUi.render()
    val maxPercentDifferent = when {
      SystemInfo.isMac -> maxPercentDifferentMac
      SystemInfo.isWindows -> maxPercentDifferentWindows
      else -> maxPercentDifferentLinux
    }
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
