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
import com.android.test.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.idea.streaming.core.DisplayType
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.createTestEvent
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN_AND_UP
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_UP
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule.FakeDevice
import com.android.tools.idea.streaming.device.actions.DeviceFoldingAction
import com.android.tools.idea.streaming.executeStreamingAction
import com.android.tools.idea.streaming.updateAndGetActionPresentation
import com.android.tools.idea.testing.registerServiceInstance
import com.android.tools.idea.ui.screenrecording.ScreenRecordingSupportedCache
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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
import java.util.EnumSet
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
    device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(false)
    assertThat(panel.primaryDisplayView).isNotNull()
    assertThat((panel.icon as LayeredIcon).getIcon(0)).isEqualTo(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(5, SECONDS) { agent.isRunning && panel.isConnected }

    // Check appearance.
    fakeUi.updateToolbars()
    waitForFrame()
    assertAppearance("AppearanceAndToolbarActions1", maxPercentDifferentMac = 0.06, maxPercentDifferentWindows = 0.06)
    assertThat(panel.preferredFocusableComponent).isEqualTo(panel.primaryDisplayView)
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
    val dataContext = DataManager.getInstance().getDataContext(panel.primaryDisplayView)
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
    assertThat(panel.primaryDisplayView).isNull()
    waitForCondition(2, SECONDS) { !agent.videoStreamActive }
  }

  @Test
  fun testWearToolbarActions() {
    device = agentRule.connectDevice("Pixel Watch", 30, Dimension(454, 454),
                                     additionalDeviceProperties = mapOf(RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))
    panel.createContent(false)
    assertThat(panel.primaryDisplayView).isNotNull()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(5, SECONDS) { agent.isRunning && panel.isConnected }

    // Check appearance.
    fakeUi.updateToolbars()
    fakeUi.layoutAndDispatchEvents()
    waitForFrame()
    assertThat(panel.preferredFocusableComponent).isEqualTo(panel.primaryDisplayView)
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
    assertThat(updateAndGetActionPresentation("android.device.power.button", panel.primaryDisplayView!!, project).isEnabled).isFalse()
    assertThat(updateAndGetActionPresentation("android.device.volume.up.button", panel.primaryDisplayView!!, project).isEnabled).isFalse()
    assertThat(updateAndGetActionPresentation("android.device.volume.down.button", panel.primaryDisplayView!!, project).isEnabled).isFalse()
    assertThat(updateAndGetActionPresentation("android.device.rotate.left", panel.primaryDisplayView!!, project).isEnabled).isFalse()
    assertThat(updateAndGetActionPresentation("android.device.rotate.right", panel.primaryDisplayView!!, project).isEnabled).isFalse()
    assertThat(updateAndGetActionPresentation("android.device.home.button", panel.primaryDisplayView!!, project).isEnabled).isFalse()
    assertThat(updateAndGetActionPresentation("android.device.overview.button", panel.primaryDisplayView!!, project).isEnabled).isFalse()

    panel.destroyContent()
    assertThat(panel.primaryDisplayView).isNull()
    waitForCondition(2, SECONDS) { !agent.videoStreamActive }
  }

  @Test
  fun testFolding() {
    device = agentRule.connectDevice("Pixel Fold", 33, Dimension(2208, 1840), foldedSize = Dimension(1080, 2092))

    panel.createContent(false)
    val deviceView = panel.primaryDisplayView!!

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(5, SECONDS) { agent.isRunning && panel.isConnected }

    // Check appearance.
    fakeUi.updateToolbars()
    waitForFrame()

    val foldingGroup = ActionManager.getInstance().getAction("android.device.postures") as ActionGroup
    val event = createTestEvent(deviceView, project, ActionPlaces.TOOLBAR)
    waitForCondition(2, SECONDS) { deviceView.deviceController?.currentFoldingState != null }
    waitForCondition(2, SECONDS) { foldingGroup.update(event); event.presentation.isVisible }
    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(event.presentation.text).isEqualTo("Fold/Unfold (currently Open)")
    val foldingActions = foldingGroup.getChildren(event)
    assertThat(foldingActions).asList().containsExactly(
      DeviceFoldingAction(FoldingState(0, "Closed", EnumSet.of(FoldingState.Flag.APP_ACCESSIBLE))),
      DeviceFoldingAction(FoldingState(1, "Tent", EnumSet.of(FoldingState.Flag.APP_ACCESSIBLE))),
      DeviceFoldingAction(FoldingState(2, "Half-Open", EnumSet.of(FoldingState.Flag.APP_ACCESSIBLE))),
      DeviceFoldingAction(FoldingState(3, "Open", EnumSet.of(FoldingState.Flag.APP_ACCESSIBLE))),
      DeviceFoldingAction(FoldingState(4, "Rear Display Mode", EnumSet.of(FoldingState.Flag.APP_ACCESSIBLE))),
      DeviceFoldingAction(FoldingState(5, "Dual Display Mode",
                                       EnumSet.of(FoldingState.Flag.APP_ACCESSIBLE, FoldingState.Flag.CANCEL_WHEN_REQUESTER_NOT_ON_TOP))),
      DeviceFoldingAction(FoldingState(6, "Flipped", EnumSet.of(FoldingState.Flag.APP_ACCESSIBLE))))
    for (action in foldingActions) {
      action.update(event)
      assertWithMessage("Unexpected enablement state of the ${action.templateText} action")
          .that(event.presentation.isEnabled).isEqualTo(action.templateText != "Dual Display Mode")
      assertWithMessage("Unexpected visibility of the ${action.templateText} action").that(event.presentation.isVisible).isTrue()
    }
    assertThat(deviceView.deviceDisplaySize).isEqualTo(Dimension(2208, 1840))

    val nextFrameNumber = panel.primaryDisplayView!!.frameNumber + 1u
    val closingAction = foldingActions[0]
    closingAction.actionPerformed(event)
    waitForCondition(2, SECONDS) { foldingGroup.update(event); event.presentation.text == "Fold/Unfold (currently Closed)" }
    waitForFrame(minFrameNumber = nextFrameNumber)
    assertThat(deviceView.deviceDisplaySize).isEqualTo(Dimension(1080, 2092))
  }

  @Test
  fun testMultipleDisplays() {
    device = agentRule.connectDevice("Pixel 7 Pro", 33, Dimension(1440, 3120))
    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(false)
    assertThat(panel.primaryDisplayView).isNotNull()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(10, SECONDS) { agent.isRunning && panel.isConnected }

    fakeUi.updateToolbars()
    fakeUi.layoutAndDispatchEvents()
    waitForFrame()

    val externalDisplayId = 1
    agent.addDisplay(externalDisplayId, 1080, 1920, DisplayType.EXTERNAL)
    waitForCondition(2, SECONDS) { fakeUi.findAllComponents<DeviceView>().size == 2 }
    waitForFrame(PRIMARY_DISPLAY_ID)
    waitForFrame(externalDisplayId)
    assertAppearance("MultipleDisplays1", maxPercentDifferentMac = 0.06, maxPercentDifferentWindows = 0.06)

    agent.clearCommandLog()
    // Rotating the device. Only the internal display should rotate.
    executeStreamingAction("android.device.rotate.left", panel.primaryDisplayView!!, project)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(SetDeviceOrientationMessage(orientation=1))
    waitForFrame(externalDisplayId)
    assertAppearance("MultipleDisplays2", maxPercentDifferentMac = 0.06, maxPercentDifferentWindows = 0.06)

    agent.removeDisplay(externalDisplayId)
    waitForCondition(2, SECONDS) { fakeUi.findAllComponents<DeviceView>().size == 1 }
  }

  @Test
  fun testZoom() {
    device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(false)
    assertThat(panel.primaryDisplayView).isNotNull()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(10, SECONDS) { agent.isRunning && panel.isConnected }

    fakeUi.updateToolbars()
    fakeUi.layoutAndDispatchEvents()
    waitForFrame()

    var deviceView = panel.primaryDisplayView!!
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
    assertThat(panel.primaryDisplayView).isNull()
    waitForCondition(2, SECONDS) { !agent.videoStreamActive }
    panel.createContent(false, uiState)
    assertThat(panel.primaryDisplayView).isNotNull()
    deviceView = panel.primaryDisplayView!!
    fakeUi.layoutAndDispatchEvents()
    waitForCondition(5, SECONDS) { agent.videoStreamActive && panel.isConnected }
    waitForFrame()

    // Check that zoom level and scroll position are restored.
    assertThat(deviceView.scale).isWithin(0.0001).of(0.25)
    assertThat(viewport.viewSize).isEqualTo(Dimension(280, 570))
    assertThat(viewport.viewPosition).isEqualTo(scrollPosition)

    panel.destroyContent()
    assertThat(panel.primaryDisplayView).isNull()
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
    val deviceClient = DeviceClient(device.serialNumber, device.configuration, device.deviceState.cpuAbi)
    Disposer.register(testRootDisposable, deviceClient)
    val panel = DeviceToolWindowPanel(testRootDisposable, project, device.handle, deviceClient)
    panel.size = Dimension(280, 300)
    panel.zoomToolbarVisible = true
    return panel
  }

  private fun getNextControlMessageAndWaitForFrame(displayId: Int = PRIMARY_DISPLAY_ID): ControlMessage {
    val message = agent.getNextControlMessage(2, SECONDS)
    waitForFrame(displayId)
    return message
  }

  /** Waits for all video frames to be received after the given one. */
  private fun waitForFrame(displayId: Int = PRIMARY_DISPLAY_ID, minFrameNumber: UInt = 1u) {
    waitForCondition(2, SECONDS) {
      panel.isConnected &&
      agent.getFrameNumber(displayId) >= minFrameNumber &&
      renderAndGetFrameNumber(displayId) == agent.getFrameNumber(displayId)
    }
  }

  private fun renderAndGetFrameNumber(displayId: Int = PRIMARY_DISPLAY_ID): UInt {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return panel.findDisplayView(displayId)!!.frameNumber
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

  private val DeviceToolWindowPanel.isConnected
    get() = (getData(DEVICE_VIEW_KEY.name) as? DeviceView)?.isConnected ?: false

  private fun DeviceToolWindowPanel.findDisplayView(displayId: Int): DeviceView? =
    if (displayId == PRIMARY_DISPLAY_ID) primaryDisplayView else findDescendant<DeviceView> { it.displayId == displayId }
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/DeviceToolWindowPanelTest/golden"
