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
import com.android.test.testutils.TestUtils
import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.waitForCondition
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.core.DisplayType
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.createTestEvent
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN_AND_UP
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_UP
import com.android.tools.idea.streaming.device.DeviceState.Property.PROPERTY_POLICY_CANCEL_WHEN_REQUESTER_NOT_ON_TOP
import com.android.tools.idea.streaming.device.FakeScreenSharingAgent.ControlMessageFilter
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule.FakeDevice
import com.android.tools.idea.streaming.device.actions.DeviceFoldingAction
import com.android.tools.idea.streaming.executeStreamingAction
import com.android.tools.idea.streaming.updateAndGetActionPresentation
import com.android.tools.idea.testing.override
import com.android.tools.idea.testing.registerServiceInstance
import com.android.tools.idea.ui.screenrecording.ScreenRecordingSupportedCache
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.replaceService
import com.intellij.ui.LayeredIcon
import icons.StudioIcons
import it.unimi.dsi.fastutil.bytes.ByteArrayList
import kotlinx.coroutines.runBlocking
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.Control
import javax.sound.sampled.Line
import javax.sound.sampled.LineListener
import javax.sound.sampled.SourceDataLine
import javax.swing.JViewport
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [DeviceToolWindowPanel], [DeviceDisplayPanel] and toolbar actions that produce Android key events.
 */
@RunsInEdt
class DeviceToolWindowPanelTest {
  companion object {
    @JvmField
    @ClassRule
    val iconRule = IconLoaderRule() // Enable icon loading in a headless test environment.

    private val controlMessageFilter = ControlMessageFilter(DisplayConfigurationRequest.TYPE, SetMaxVideoResolutionMessage.TYPE)
  }

  private val agentRule = FakeScreenSharingAgentRule()

  @get:Rule
  val ruleChain = RuleChain(agentRule, ClipboardSynchronizationDisablementRule(), PortableUiFontRule(), EdtRule())

  private lateinit var device: FakeDevice
  private val panel: DeviceToolWindowPanel by lazy { createToolWindowPanel() }
  // Fake window is necessary for the toolbars to be rendered.
  private val fakeUi: FakeUi by lazy { FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable) }
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
    waitForCondition(5.seconds) { agent.isRunning && panel.isConnected }

    // Check appearance.
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
    waitForCondition(2.seconds) { !agent.videoStreamActive }
  }

  @Test
  fun testWearToolbarActions() {
    device = agentRule.connectDevice("Pixel Watch", 30, Dimension(454, 454),
                                     additionalDeviceProperties = mapOf(RO_BUILD_CHARACTERISTICS to "nosdcard,watch"))
    panel.createContent(false)
    assertThat(panel.primaryDisplayView).isNotNull()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(5.seconds) { agent.isRunning && panel.isConnected }

    // Check appearance.
    waitForFrame()
    assertThat(panel.preferredFocusableComponent).isEqualTo(panel.primaryDisplayView)
    assertThat((panel.icon as LayeredIcon).getIcon(0)).isEqualTo(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR)

    // Check push button actions.
    val pushButtonCases = listOf(
      Pair("Button 1", AKEYCODE_STEM_PRIMARY),
      Pair("Button 2", AKEYCODE_POWER),
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
    waitForCondition(2.seconds) { !agent.videoStreamActive }
  }

  @Test
  fun testFolding() {
    device = agentRule.connectDevice("Pixel Fold", 33, Dimension(2208, 1840), foldedSize = Dimension(1080, 2092))

    panel.createContent(false)
    val deviceView = panel.primaryDisplayView!!

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(5.seconds) { agent.isRunning && panel.isConnected }

    // Check appearance.
    waitForFrame()

    val foldingGroup = ActionManager.getInstance().getAction("android.device.postures") as ActionGroup
    val event = createTestEvent(deviceView, project, ActionPlaces.TOOLBAR)
    waitForCondition(2.seconds) { deviceView.deviceController?.currentFoldingState != null }
    waitForCondition(2.seconds) { foldingGroup.update(event); event.presentation.isVisible }
    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(event.presentation.text).isEqualTo("Fold/Unfold (currently Open)")
    val foldingActions = foldingGroup.getChildren(event)
    assertThat(foldingActions).asList().containsExactly(
      DeviceFoldingAction(FoldingState(0, "Closed")),
      DeviceFoldingAction(FoldingState(1, "Tent")),
      DeviceFoldingAction(FoldingState(2, "Half-Open")),
      DeviceFoldingAction(FoldingState(3, "Open")),
      DeviceFoldingAction(FoldingState(4, "Rear Display Mode")),
      DeviceFoldingAction(FoldingState(5, "Dual Display Mode", setOf(PROPERTY_POLICY_CANCEL_WHEN_REQUESTER_NOT_ON_TOP))),
      DeviceFoldingAction(FoldingState(6, "Rear Dual Mode", setOf(PROPERTY_POLICY_CANCEL_WHEN_REQUESTER_NOT_ON_TOP))),
      DeviceFoldingAction(FoldingState(7, "Flipped")))
    val disabledModes = setOf("Dual Display Mode", "Rear Dual Mode")
    for (action in foldingActions) {
      action.update(event)
      assertWithMessage("Unexpected enablement state of the ${action.templateText} action")
          .that(event.presentation.isEnabled).isEqualTo(action.templateText !in disabledModes)
      assertWithMessage("Unexpected visibility of the ${action.templateText} action").that(event.presentation.isVisible).isTrue()
    }
    assertThat(deviceView.deviceDisplaySize).isEqualTo(Dimension(2208, 1840))

    val nextFrameNumber = panel.primaryDisplayView!!.frameNumber + 1u
    val closingAction = foldingActions[0]
    closingAction.actionPerformed(event)
    waitForCondition(2.seconds) { foldingGroup.update(event); event.presentation.text == "Fold/Unfold (currently Closed)" }
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
    waitForCondition(10.seconds) { agent.isRunning && panel.isConnected }

    waitForFrame()

    val externalDisplayId = 1
    agent.addDisplay(externalDisplayId, 1080, 1920, DisplayType.EXTERNAL)
    waitForCondition(2.seconds) { fakeUi.findAllComponents<DeviceView>().size == 2 }
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
    waitForCondition(2.seconds) { fakeUi.findAllComponents<DeviceView>().size == 1 }
  }

  @Test
  fun testZoom() {
    device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280))
    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(false)
    assertThat(panel.primaryDisplayView).isNotNull()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(10.seconds) { agent.isRunning && panel.isConnected }

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
    waitForCondition(2.seconds) { !agent.videoStreamActive }
    panel.createContent(false, uiState)
    assertThat(panel.primaryDisplayView).isNotNull()
    deviceView = panel.primaryDisplayView!!
    fakeUi.layoutAndDispatchEvents()
    waitForCondition(5.seconds) { agent.videoStreamActive && panel.isConnected }
    waitForFrame()

    // Check that zoom level and scroll position are restored.
    assertThat(deviceView.scale).isWithin(0.0001).of(0.25)
    assertThat(viewport.viewSize).isEqualTo(Dimension(280, 570))
    assertThat(viewport.viewPosition).isEqualTo(scrollPosition)

    panel.destroyContent()
    assertThat(panel.primaryDisplayView).isNull()
    waitForCondition(2.seconds) { !agent.videoStreamActive }
  }


  @Test
  fun testAudio() {
    DeviceMirroringSettings.getInstance()::redirectAudio.override(true, testRootDisposable)
    val testDataLine = TestDataLine()
    val testAudioSystemService = object : AudioSystemService() {
      override fun getSourceDataLine(audioFormat: AudioFormat): SourceDataLine = testDataLine
    }
    ApplicationManager.getApplication().replaceService(AudioSystemService::class.java, testAudioSystemService, testRootDisposable)

    device = agentRule.connectDevice("Pixel 7 Pro", 33, Dimension(1440, 3120))
    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(false)
    assertThat(panel.primaryDisplayView).isNotNull()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(10.seconds) { agent.isRunning && panel.isConnected }
    waitForFrame()

    val frequencyHz = 440.0
    val durationMillis = 500
    runBlocking { agent.beep(frequencyHz, durationMillis) }
    waitForCondition(2.seconds) {
      testDataLine.dataSize >= AUDIO_SAMPLE_RATE * AUDIO_CHANNEL_COUNT * AUDIO_BYTES_PER_SAMPLE_FMT_S16 * durationMillis / 1000
    }
    val buf = testDataLine.dataAsByteBuffer()
    var volumeReached = false
    var previousValue = 0.0
    var start = Double.NaN
    for (i in 0 until buf.limit() / (AUDIO_CHANNEL_COUNT * AUDIO_BYTES_PER_SAMPLE_FMT_S16)) {
      for (channel in 1..AUDIO_CHANNEL_COUNT) {
        val v = buf.getShort().toDouble()
        when {
          start.isFinite() && i * 1000 / AUDIO_SAMPLE_RATE < durationMillis -> {
            val expected = sin((i - start) * 2 * PI * frequencyHz / AUDIO_SAMPLE_RATE) * Short.MAX_VALUE
            assertEquals(expected, v, Short.MAX_VALUE * 0.03,
                         "Unexpected signal value in channel $channel at ${i * 1000.0 / AUDIO_SAMPLE_RATE} ms")
          }
          volumeReached -> {
            if (channel == 1 && v >= 0 && previousValue < 0) {
              start = i - v / (v - previousValue)
            }
            previousValue = v
          }
          else -> {
            if (channel == 1 && v <= Short.MIN_VALUE * 0.99) {
              volumeReached = true
            }
          }
        }
      }
    }
  }

  @Test
  fun testAudioEnablementDisablement() {
    val testDataLine = TestDataLine()
    val testAudioSystemService = object : AudioSystemService() {
      override fun getSourceDataLine(audioFormat: AudioFormat): SourceDataLine = testDataLine
    }
    ApplicationManager.getApplication().replaceService(AudioSystemService::class.java, testAudioSystemService, testRootDisposable)

    device = agentRule.connectDevice("Pixel 7 Pro", 33, Dimension(1440, 3120))
    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(false)
    assertThat(panel.primaryDisplayView).isNotNull()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(10.seconds) { agent.isRunning && panel.isConnected }
    waitForFrame()

    DeviceMirroringSettings.getInstance()::redirectAudio.override(true, testRootDisposable)
    assertThat(agent.getNextControlMessage(1.seconds)).isEqualTo(StartAudioStreamMessage())
    waitForCondition(1.seconds) { agent.audioStreamActive }

    DeviceMirroringSettings.getInstance().redirectAudio = false
    assertThat(agent.getNextControlMessage(1.seconds)).isEqualTo(StopAudioStreamMessage())
    waitForCondition(1.seconds) { !agent.audioStreamActive }
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
    val message = agent.getNextControlMessage(2.seconds, filter = controlMessageFilter)
    waitForFrame(displayId)
    return message
  }

  /** Waits for all video frames to be received after the given one. */
  private fun waitForFrame(displayId: Int = PRIMARY_DISPLAY_ID, minFrameNumber: UInt = 1u) {
    waitForCondition(2.seconds) {
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
    get() = IdeUiService.getInstance().createUiDataContext(this).getData(DEVICE_VIEW_KEY)?.isConnected == true

  private fun DeviceToolWindowPanel.findDisplayView(displayId: Int): DeviceView? =
    if (displayId == PRIMARY_DISPLAY_ID) primaryDisplayView else findDescendant<DeviceView> { it.displayId == displayId }
}


private class TestDataLine : SourceDataLine {

  private val data = ByteArrayList()
  private var open = false

  val dataSize: Int
    get() = synchronized(data) { data.size }

  fun dataAsByteBuffer(): ByteBuffer =
    synchronized(data) { ByteBuffer.allocate(data.size).order(ByteOrder.LITTLE_ENDIAN).put(data.elements(), 0, data.size).flip() }

  override fun close() {
    open = false
  }

  override fun getLineInfo(): Line.Info {
    TODO("Not yet implemented")
  }

  override fun open(format: AudioFormat, bufferSize: Int) {
    open()
  }

  override fun open(format: AudioFormat) {
    open()
  }

  override fun open() {
    data.clear()
    open = true
  }

  override fun isOpen(): Boolean  = open

  override fun getControls(): Array<Control> {
    TODO("Not yet implemented")
  }

  override fun isControlSupported(control: Control.Type): Boolean {
    TODO("Not yet implemented")
  }

  override fun getControl(control: Control.Type): Control {
    TODO("Not yet implemented")
  }

  override fun addLineListener(listener: LineListener) {
    TODO("Not yet implemented")
  }

  override fun removeLineListener(listener: LineListener) {
    TODO("Not yet implemented")
  }

  override fun drain() {
    TODO("Not yet implemented")
  }

  override fun flush() {
  }

  override fun start() {
  }

  override fun stop() {
  }

  override fun isRunning(): Boolean {
    TODO("Not yet implemented")
  }

  override fun isActive(): Boolean {
    TODO("Not yet implemented")
  }

  override fun getFormat(): AudioFormat {
    TODO("Not yet implemented")
  }

  override fun getBufferSize(): Int {
    TODO("Not yet implemented")
  }

  override fun available(): Int {
    TODO("Not yet implemented")
  }

  override fun getFramePosition(): Int {
    TODO("Not yet implemented")
  }

  override fun getLongFramePosition(): Long {
    TODO("Not yet implemented")
  }

  override fun getMicrosecondPosition(): Long {
    TODO("Not yet implemented")
  }

  override fun getLevel(): Float {
    TODO("Not yet implemented")
  }

  override fun write(bytes: ByteArray, offset: Int, len: Int): Int {
    synchronized(data) { data.addElements(data.size, bytes, offset, len) }
    return len
  }
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/DeviceToolWindowPanelTest/golden"
