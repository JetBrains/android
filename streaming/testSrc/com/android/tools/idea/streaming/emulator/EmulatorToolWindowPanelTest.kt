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
package com.android.tools.idea.streaming.emulator

import com.android.emulator.control.DisplayConfiguration
import com.android.emulator.control.Posture.PostureValue
import com.android.emulator.control.ThemingStyle
import com.android.mockito.kotlin.whenever
import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessRootPaneContainer
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.idea.editors.liveedit.ui.LiveEditNotificationGroup
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.streaming.ClipboardSynchronizationDisablementRule
import com.android.tools.idea.streaming.actions.HardwareInputStateStorage
import com.android.tools.idea.streaming.core.FloatingToolbarContainer
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.core.SplitPanel
import com.android.tools.idea.streaming.createTestEvent
import com.android.tools.idea.streaming.emulator.EmulatorConfiguration.PostureDescriptor
import com.android.tools.idea.streaming.emulator.EmulatorToolWindowPanel.MultiDisplayStateStorage
import com.android.tools.idea.streaming.emulator.FakeEmulator.Companion.IGNORE_SCREENSHOT_CALL_FILTER
import com.android.tools.idea.streaming.emulator.FakeEmulator.GrpcCallRecord
import com.android.tools.idea.streaming.emulator.actions.EmulatorFoldingAction
import com.android.tools.idea.streaming.emulator.actions.EmulatorShowVirtualSensorsAction
import com.android.tools.idea.streaming.emulator.actions.ToggleFloatingXrToolbarAction
import com.android.tools.idea.streaming.emulator.xr.EmulatorXrInputController
import com.android.tools.idea.streaming.emulator.xr.XrInputMode
import com.android.tools.idea.streaming.executeStreamingAction
import com.android.tools.idea.streaming.updateAndGetActionPresentation
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.testing.mockStatic
import com.android.tools.idea.testing.registerServiceInstance
import com.android.tools.idea.ui.screenrecording.ScreenRecordingSupportedCache
import com.google.common.truth.Truth.assertThat
import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfoImpl
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.replaceService
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.LayeredIcon
import icons.StudioIcons
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.Component
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.awt.PointerInfo
import java.awt.event.FocusEvent
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.KEY_RELEASED
import java.awt.event.KeyEvent.VK_A
import java.awt.event.KeyEvent.VK_D
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_E
import java.awt.event.KeyEvent.VK_END
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_HOME
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_P
import java.awt.event.KeyEvent.VK_PAGE_DOWN
import java.awt.event.KeyEvent.VK_PAGE_UP
import java.awt.event.KeyEvent.VK_Q
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_S
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.KeyEvent.VK_W
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.MOUSE_MOVED
import java.nio.file.Path
import java.util.concurrent.TimeoutException
import javax.swing.JViewport
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [EmulatorToolWindowPanel] and some of its toolbar actions.
 */
@Suppress("OPT_IN_USAGE", "OverrideOnly")
@RunsInEdt
class EmulatorToolWindowPanelTest {

  companion object {
    @JvmField
    @ClassRule
    val iconRule = IconLoaderRule()
  }

  private val projectRule = ProjectRule()
  private val emulatorRule = FakeEmulatorRule()
  @get:Rule
  val ruleChain: RuleChain =
      RuleChain(projectRule, emulatorRule, ClipboardSynchronizationDisablementRule(), PortableUiFontRule(), EdtRule())

  private var nullableEmulator: FakeEmulator? = null

  private var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  private val project get() = projectRule.project
  private val testRootDisposable get() = projectRule.disposable

  @Before
  fun setUp() {
    StudioFlags.EMBEDDED_EMULATOR_ALLOW_XR_AVD.overrideForTest(true, testRootDisposable)
    StudioFlags.EMBEDDED_EMULATOR_XR_HAND_TRACKING.overrideForTest(true, testRootDisposable)
    StudioFlags.EMBEDDED_EMULATOR_XR_EYE_TRACKING.overrideForTest(true, testRootDisposable)
    HeadlessDataManager.fallbackToProductionDataManager(testRootDisposable) // Necessary to properly update toolbar button states.
    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider(TestDataProvider(project), testRootDisposable)
    val mockScreenRecordingCache = mock<ScreenRecordingSupportedCache>()
    whenever(mockScreenRecordingCache.isScreenRecordingSupported(any(), any())).thenReturn(true)
    projectRule.project.registerServiceInstance(ScreenRecordingSupportedCache::class.java, mockScreenRecordingCache, testRootDisposable)
  }

  @Test
  fun testAppearanceAndToolbarActions() {
    val panel = createWindowPanelForPhone()
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryDisplayView ?: fail()
    assertThat((panel.icon as LayeredIcon).getIcon(0)).isEqualTo(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)

    // Check appearance.
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0u)
    panel.size = Dimension(400, 600)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 363 height: 515")
    assertAppearance(ui, "AppearanceAndToolbarActions1", maxPercentDifferentMac = 0.03, maxPercentDifferentWindows = 0.3)

    // Check EmulatorPowerButtonAction.
    var button = ui.getComponent<ActionButton> { it.action.templateText == "Power" }
    ui.mousePressOn(button)
    val streamInputCall = emulator.getNextGrpcCall(2.seconds)
    assertThat(streamInputCall.methodName).isEqualTo("android.emulation.control.EmulatorController/streamInputEvent")
    assertThat(shortDebugString(streamInputCall.request)).isEqualTo("key_event { key: \"Power\" }")
    ui.mouseRelease()
    assertThat(shortDebugString(streamInputCall.request)).isEqualTo("key_event { eventType: keyup key: \"Power\" }")

    // Check EmulatorPowerButtonAction invoked by a keyboard shortcut.
    var action = ActionManager.getInstance().getAction("android.device.power.button")
    var keyEvent = KeyEvent(panel, KEY_RELEASED, System.currentTimeMillis(), CTRL_DOWN_MASK, VK_P, KeyEvent.CHAR_UNDEFINED)
    val dataContext = DataManager.getInstance().getDataContext(panel.primaryDisplayView)
    action.actionPerformed(AnActionEvent.createFromAnAction(action, keyEvent, ActionPlaces.KEYBOARD_SHORTCUT, dataContext))
    assertThat(shortDebugString(streamInputCall.request)).isEqualTo("key_event { eventType: keypress key: \"Power\" }")

    // Check EmulatorPowerAndVolumeUpButtonAction invoked by a keyboard shortcut.
    action = ActionManager.getInstance().getAction("android.device.power.and.volume.up.button")
    keyEvent = KeyEvent(panel, KEY_RELEASED, System.currentTimeMillis(), CTRL_DOWN_MASK or SHIFT_DOWN_MASK, VK_P, KeyEvent.CHAR_UNDEFINED)
    action.actionPerformed(AnActionEvent.createFromAnAction(action, keyEvent, ActionPlaces.KEYBOARD_SHORTCUT, dataContext))
    assertThat(shortDebugString(streamInputCall.request)).isEqualTo("key_event { key: \"VolumeUp\" }")
    assertThat(shortDebugString(streamInputCall.request)).isEqualTo("key_event { eventType: keypress key: \"Power\" }")
    assertThat(shortDebugString(streamInputCall.request)).isEqualTo("key_event { eventType: keyup key: \"VolumeUp\" }")

    // Check EmulatorVolumeUpButtonAction.
    button = ui.getComponent { it.action.templateText == "Volume Up" }
    ui.mousePressOn(button)
    assertThat(shortDebugString(streamInputCall.request)).isEqualTo("key_event { key: \"AudioVolumeUp\" }")
    ui.mouseRelease()
    assertThat(shortDebugString(streamInputCall.request)).isEqualTo("key_event { eventType: keyup key: \"AudioVolumeUp\" }")

    // Check EmulatorVolumeDownButtonAction.
    button = ui.getComponent { it.action.templateText == "Volume Down" }
    ui.mousePressOn(button)
    assertThat(shortDebugString(streamInputCall.request)).isEqualTo("key_event { key: \"AudioVolumeDown\" }")
    ui.mouseRelease()
    assertThat(shortDebugString(streamInputCall.request)).isEqualTo("key_event { eventType: keyup key: \"AudioVolumeDown\" }")

    // Check that the Fold/Unfold action is hidden because the device is not foldable.
    assertThat(updateAndGetActionPresentation("android.device.postures", emulatorView, project).isVisible).isFalse()

    // Ensures that LiveEditAction starts off hidden since it's disconnected.
    assertThat(ui.findComponent<LiveEditNotificationGroup>()).isNull()

    assertThat(streamScreenshotCall.completion.isCancelled).isFalse()

    panel.destroyContent()
    assertThat(panel.primaryDisplayView).isNull()
    streamScreenshotCall.waitForCancellation(2.seconds)
  }

  @Test
  fun testWearToolbarActionsApi30() {
    val avdFolder = FakeEmulator.createWatchAvd(emulatorRule.avdRoot, api = 30)
    val panel = createWindowPanel(avdFolder)
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryDisplayView ?: fail()
    assertThat((panel.icon as LayeredIcon).getIcon(0)).isEqualTo(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR)

    // Check appearance.
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0u)
    panel.size = Dimension(430, 450)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 320 height: 320")
    assertAppearance(ui, "WearToolbarActions1", maxPercentDifferentMac = 0.04, maxPercentDifferentWindows = 0.15)

    // Check Wear1ButtonAction.
    var button = ui.getComponent<ActionButton> { it.action.templateText == "Button 1" }
    ui.mouseClickOn(button)
    val streamInputCall = emulator.getNextGrpcCall(2.seconds)
    assertThat(streamInputCall.methodName).isEqualTo("android.emulation.control.EmulatorController/streamInputEvent")
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds))).isEqualTo("key_event { key: \"GoHome\" }")
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds))).isEqualTo("key_event { eventType: keyup key: \"GoHome\" }")

    // Check Wear2ButtonAction.
    button = ui.getComponent { it.action.templateText == "Button 2" }
    ui.mousePressOn(button)
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds))).isEqualTo("key_event { key: \"Power\" }")
    ui.mouseRelease()
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds))).isEqualTo("key_event { eventType: keyup key: \"Power\" }")

    // Check PalmAction.
    button = ui.getComponent { it.action.templateText == "Palm" }
    ui.mouseClickOn(button)
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds))).isEqualTo("key_event { eventType: keypress key: \"Standby\" }")

    // Check TiltAction.
    button = ui.getComponent { it.action.templateText == "Tilt" }
    ui.mouseClickOn(button)
    val call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setPhysicalModel")
    assertThat(shortDebugString(call.request)).isEqualTo("target: WRIST_TILT value { data: 1.0 }")

    // Check that the buttons not applicable to Wear OS 3 are hidden.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Power" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Up" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Down" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Left" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Right" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Home" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Overview" }).isNull()

    panel.destroyContent()
    assertThat(panel.primaryDisplayView).isNull()
    streamScreenshotCall.waitForCancellation(2.seconds)
  }
  @Test
  fun testWearToolbarActionsApi28() {
    val avdFolder = FakeEmulator.createWatchAvd(emulatorRule.avdRoot, api = 28)
    val panel = createWindowPanel(avdFolder)
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    panel.size = Dimension(430, 450)
    ui.layoutAndDispatchEvents()

    // Check toolbar buttons.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Button 1" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Button 2" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Palm" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Tilt" }).isNotNull()

    // Check that the buttons not applicable to Wear OS are hidden.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Power" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Up" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Down" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Left" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Right" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Home" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Overview" }).isNull()

    val streamScreenshotCall = emulator.getNextGrpcCall(2.seconds)
    panel.destroyContent()
    assertThat(panel.primaryDisplayView).isNull()
    streamScreenshotCall.waitForCancellation(2.seconds)
  }

  @Test
  fun testWearToolbarActionsApi26() {
    val avdFolder = FakeEmulator.createWatchAvd(emulatorRule.avdRoot, api = 26)
    val panel = createWindowPanel(avdFolder)
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    panel.size = Dimension(430, 450)
    ui.layoutAndDispatchEvents()

    // Check that the buttons specific to API 30 and API 28 are hidden.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Button 1" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Button 2" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Palm" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Tilt" }).isNull()

    // Check that the generic Android buttons are visible on API 26.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Power" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Home" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Overview" }).isNotNull()

    // Check that the buttons not applicable to Wear OS are hidden.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Up" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Down" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Left" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Right" }).isNull()

    val streamScreenshotCall = emulator.getNextGrpcCall(2.seconds)
    panel.destroyContent()
    assertThat(panel.primaryDisplayView).isNull()
    streamScreenshotCall.waitForCancellation(2.seconds)
  }

  @Test
  fun testXrToolbarActions() {
    val panel = createWindowPanelForXr()
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryDisplayView ?: fail()
    assertThat((panel.icon as LayeredIcon).getIcon(0)).isEqualTo(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_HEADSET)

    // Check appearance.
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0u)
    panel.size = Dimension(600, 600)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 600 height: 565")
    assertAppearance(ui, "XrToolbarActions1", maxPercentDifferentMac = 0.04, maxPercentDifferentWindows = 0.15)

    // Check that the buttons not applicable to XR devices are hidden.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Power" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Up" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Down" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Left" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Right" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Back" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Home" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Overview" }).isNotNull()

    // Check XR-specific actions.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Reset View" }).isNotNull()

    val xrInputController = EmulatorXrInputController.getInstance(project, emulatorView.emulator)
    assertThat(xrInputController.inputMode).isEqualTo(XrInputMode.HAND)
    val modes = mapOf(
      "Hand Tracking" to XrInputMode.HAND,
      "Eye Tracking" to XrInputMode.EYE,
      "Hardware Input" to XrInputMode.HARDWARE,
      "View Direction" to XrInputMode.VIEW_DIRECTION,
      "Move Right/Left and Up/Down" to XrInputMode.LOCATION_IN_SPACE_XY,
      "Move Forward/Backward" to XrInputMode.LOCATION_IN_SPACE_Z,
    )
    val hardwareInputStateStorage = project.service<HardwareInputStateStorage>()
    for ((actionName, mode) in modes) {
      ui.mouseClickOn(ui.getComponent<ActionButton> { it.action.templateText == actionName })
      assertThat(xrInputController.inputMode).isEqualTo(mode)
      assertThat(hardwareInputStateStorage.isHardwareInputEnabled(emulatorView.deviceId)).isEqualTo(mode == XrInputMode.HARDWARE)
    }

    ui.mouseClickOn(ui.getComponent<ActionButton> { it.action.templateText == "Reset View" })
    val streamInputCall = getNextGrpcCallIgnoringStreamScreenshot()
    assertThat(streamInputCall.methodName).isEqualTo("android.emulation.control.EmulatorController/streamInputEvent")
    assertThat(shortDebugString(streamInputCall.request)).isEqualTo("xr_command { }")

    val toggleAction = ToggleFloatingXrToolbarAction()
    toggleAction.actionPerformed(createTestEvent(emulatorView, project, ActionPlaces.TOOLWINDOW_POPUP))
    assertAppearance(ui, "XrToolbarActions2", maxPercentDifferentMac = 0.04, maxPercentDifferentWindows = 0.15)

    panel.destroyContent()
    assertThat(panel.primaryDisplayView).isNull()
    streamScreenshotCall.waitForCancellation(2.seconds)
  }

  @Test
  fun testXrMouseInput() {
    val panel = createWindowPanelForXr()
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryDisplayView ?: fail()
    assertThat((panel.icon as LayeredIcon).getIcon(0)).isEqualTo(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_HEADSET)

    // Check appearance.
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0u)
    panel.size = Dimension(600, 600)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 600 height: 565")

    val xrInputController = EmulatorXrInputController.getInstance(project, emulatorView.emulator)
    val testCases = mapOf(
      XrInputMode.HAND to "xr_hand_event",
      XrInputMode.EYE to "xr_eye_event",
      XrInputMode.HARDWARE to "mouse_event",
    )
    var streamInputCall: GrpcCallRecord? = null
    for ((inputMode, expectedEvent) in testCases) {
      xrInputController.inputMode = inputMode
      ui.mouse.moveTo(100, 100)
      val call = streamInputCall ?: getNextGrpcCallIgnoringStreamScreenshot().also { streamInputCall = it }
      assertThat(shortDebugString(call.getNextRequest(1.seconds))).isEqualTo("$expectedEvent { x: 428 y: 258 }")
      ui.mouse.press(100, 100)
      assertThat(shortDebugString(call.getNextRequest(1.seconds))).isEqualTo("$expectedEvent { x: 428 y: 258 buttons: 1 }")
      ui.mouse.dragTo(500, 200)
      assertThat(shortDebugString(call.getNextRequest(1.seconds))).isEqualTo("$expectedEvent { x: 2135 y: 684 buttons: 1 }")
      ui.mouse.release()
      assertThat(shortDebugString(call.getNextRequest(1.seconds))).isEqualTo("$expectedEvent { x: 2135 y: 684 }")
    }
  }

  @Test
  fun testXrKeyboardNavigation() {
    val panel = createWindowPanelForXr()
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryDisplayView ?: fail()
    assertThat((panel.icon as LayeredIcon).getIcon(0)).isEqualTo(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_HEADSET)

    // Check appearance.
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0u)
    panel.size = Dimension(600, 600)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 600 height: 565")

    val xrInputController = EmulatorXrInputController.getInstance(project, emulatorView.emulator)
    xrInputController.inputMode = XrInputMode.VIEW_DIRECTION
    ui.keyboard.setFocus(emulatorView)
    ui.keyboard.press(VK_ENTER)
    val streamInputCall = getNextGrpcCallIgnoringStreamScreenshot()
    // Keys that are not used for navigation produce keypress events.
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds))).isEqualTo("key_event { eventType: keypress key: \"Enter\" }")
    ui.keyboard.release(VK_ENTER)
    val keys = mapOf(
      VK_W to "forward_held: true",
      VK_A to "left_held: true",
      VK_S to "backward_held: true",
      VK_D to "right_held: true",
      VK_Q to "down_held: true",
      VK_E to "up_held: true",
      VK_RIGHT to "rotate_right_held: true",
      VK_LEFT to "rotate_left_held: true",
      VK_UP to "rotate_up_held: true",
      VK_DOWN to "rotate_down_held: true",
      VK_PAGE_UP to "rotate_right_held: true rotate_up_held: true",
      VK_PAGE_DOWN to "rotate_right_held: true rotate_up_held: true",
      VK_HOME to "rotate_left_held: true rotate_up_held: true",
      VK_END to "rotate_left_held: true rotate_up_held: true",
    )
    for ((k, event) in keys) {
      ui.keyboard.press(k)
      assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds))).isEqualTo("xr_input_event { nav_event { $event } }")
      ui.keyboard.release(k)
      assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds))).isEqualTo("xr_input_event { nav_event { } }")
    }

    // Two keys pressed together.
    ui.keyboard.press(VK_D)
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds)))
        .isEqualTo("xr_input_event { nav_event { right_held: true } }")
    ui.keyboard.press(VK_E)
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds)))
        .isEqualTo("xr_input_event { nav_event { right_held: true up_held: true } }")

    // Switching to Hardware Input resets state of the navigation keys.
    val toolbar = ui.getComponent<FloatingToolbarContainer>()
    // Trigger expansion of the floating toolbar.
    ui.mouse.moveTo(toolbar.locationOnScreen.x + toolbar.width / 2, toolbar.locationOnScreen.y + toolbar.height - toolbar.width / 2)
    ui.layoutAndDispatchEvents()
    waitForCondition(1.seconds) { toolbar.activationFactor == 1.0 }
    ui.layoutAndDispatchEvents()

    ui.mouseClickOn(ui.getComponent<ActionButton> { it.action.templateText == "Hardware Input" })
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds))).isEqualTo("xr_input_event { nav_event { } }")
    ui.keyboard.release(VK_D)
    ui.keyboard.release(VK_E)
  }

  @Test
  fun testXrMouseViewRotation() {
    val panel = createWindowPanelForXr()
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryDisplayView ?: fail()
    assertThat((panel.icon as LayeredIcon).getIcon(0)).isEqualTo(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_HEADSET)

    // Check appearance.
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0u)
    panel.size = Dimension(600, 600)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 600 height: 565")

    val xrInputController = EmulatorXrInputController.getInstance(project, emulatorView.emulator)
    xrInputController.inputMode = XrInputMode.VIEW_DIRECTION
    ui.mouse.press(100, 100)
    ui.mouse.dragTo(500, 100)
    val streamInputCall = getNextGrpcCallIgnoringStreamScreenshot()
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds)))
        .isEqualTo("xr_input_event { move_event { intent: XR_MOVE_EVENT_INTENT_VIEWPORT_ROTATE rel_x: 1707 } }")
    ui.mouse.dragTo(500, 500)
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds)))
        .isEqualTo("xr_input_event { move_event { intent: XR_MOVE_EVENT_INTENT_VIEWPORT_ROTATE rel_y: 1707 } }")
    ui.mouse.dragTo(500, 10) // Exit the EmulatorView component.
    ui.mouse.dragTo(300, 35) // Enter the EmulatorView component in a different location.
    ui.mouse.dragTo(100, 435)
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds)))
        .isEqualTo("xr_input_event { move_event { intent: XR_MOVE_EVENT_INTENT_VIEWPORT_ROTATE rel_x: -853 rel_y: 1707 } }")
  }

  @Test
  fun testXrMouseMovementInSpace() {
    val panel = createWindowPanelForXr()
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryDisplayView ?: fail()
    assertThat((panel.icon as LayeredIcon).getIcon(0)).isEqualTo(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_HEADSET)

    // Check appearance.
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0u)
    panel.size = Dimension(600, 600)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 600 height: 565")

    val xrInputController = EmulatorXrInputController.getInstance(project, emulatorView.emulator)
    // Moving in the view plane dragging the mouse.
    xrInputController.inputMode = XrInputMode.LOCATION_IN_SPACE_XY
    ui.mouse.press(100, 100)
    ui.mouse.dragTo(500, 100)
    val streamInputCall = getNextGrpcCallIgnoringStreamScreenshot()
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds))).isEqualTo("xr_input_event { move_event { rel_x: 1707 } }")
    ui.mouse.dragTo(500, 500)
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds))).isEqualTo("xr_input_event { move_event { rel_y: 1707 } }")
    ui.mouse.dragTo(500, 10) // Exit the EmulatorView component.
    ui.mouse.dragTo(300, 35) // Enter the EmulatorView component in a different location.
    ui.mouse.dragTo(100, 435)
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds)))
        .isEqualTo("xr_input_event { move_event { rel_x: -853 rel_y: 1707 } }")
    ui.mouse.release()

    // Moving forward and backward by rotating the mouse wheel.
    ui.mouse.wheel(10, 100, 1)
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds)))
        .isEqualTo("xr_input_event { move_event { intent: XR_MOVE_EVENT_INTENT_VIEWPORT_ZOOM rel_y: -512 } }")
    ui.mouse.wheel(10, 100, -3)
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds)))
        .isEqualTo("xr_input_event { move_event { intent: XR_MOVE_EVENT_INTENT_VIEWPORT_ZOOM rel_y: 1536 } }")

    // Moving forward by dragging the mouse.
    xrInputController.inputMode = XrInputMode.LOCATION_IN_SPACE_Z
    ui.mouse.press(100, 100)
    ui.mouse.dragTo(500, 500)
    assertThat(shortDebugString(streamInputCall.getNextRequest(1.seconds)))
        .isEqualTo("xr_input_event { move_event { intent: XR_MOVE_EVENT_INTENT_VIEWPORT_ZOOM rel_y: 1707 } }")
    ui.mouse.release()
  }

  @Test
  fun testAutomotiveToolbarActions() {
    val avdFolder = FakeEmulator.createAutomotiveAvd(emulatorRule.avdRoot, api = 32)
    val panel = createWindowPanel(avdFolder)
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryDisplayView ?: fail()
    assertThat((panel.icon as LayeredIcon).getIcon(0)).isEqualTo(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR)

    // Check appearance.
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0u)
    panel.size = Dimension(400, 300)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 400 height: 265")
    assertAppearance(ui, "AutomotiveToolbarActions1", maxPercentDifferentMac = 0.04, maxPercentDifferentWindows = 0.15)

    // Check that the buttons not applicable to Automotive devices are hidden.
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Power" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Up" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Volume Down" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Left" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Rotate Right" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Back" }).isNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Home" }).isNotNull()
    assertThat(ui.findComponent<ActionButton> { it.action.templateText == "Overview" }).isNull()

    panel.destroyContent()
    assertThat(panel.primaryDisplayView).isNull()
    streamScreenshotCall.waitForCancellation(2.seconds)
  }

  @Test
  fun testDisplayModes() {
    val avdFolder = FakeEmulator.createResizableAvd(emulatorRule.avdRoot)
    val panel = createWindowPanel(avdFolder)
    panel.zoomToolbarVisible = false
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    val project = projectRule.project

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryDisplayView ?: fail()

    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0u)
    panel.size = Dimension(500, 600)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 500 height: 565")
    assertAppearance(ui, "DisplayModesPhone", maxPercentDifferentMac = 0.002, maxPercentDifferentWindows = 0.05)

    // Set the foldable display mode.
    executeStreamingAction("android.emulator.display.mode.foldable", emulatorView, project)
    val setDisplayModeCall = emulator.getNextGrpcCall(2.seconds)
    assertThat(setDisplayModeCall.methodName).isEqualTo("android.emulation.control.EmulatorController/setDisplayMode")
    assertThat(shortDebugString(setDisplayModeCall.request)).isEqualTo("value: FOLDABLE")

    panel.waitForFrame(ui, ++frameNumber, 2.seconds)
    assertAppearance(ui, "DisplayModesFoldable", maxPercentDifferentMac = 0.002, maxPercentDifferentWindows = 0.05)

    val foldingGroup = ActionManager.getInstance().getAction("android.device.postures") as ActionGroup
    val event = createTestEvent(emulatorView, project, ActionPlaces.TOOLBAR)
    waitForCondition(2.seconds) { foldingGroup.update(event); event.presentation.isVisible }
    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(event.presentation.text).isEqualTo("Fold/Unfold (currently Open)")
    val foldingActions = foldingGroup.getChildren(event)
    assertThat(foldingActions).asList().containsExactly(
      EmulatorFoldingAction(PostureDescriptor(PostureValue.POSTURE_CLOSED, PostureDescriptor.ValueType.HINGE_ANGLE, 0.0, 30.0)),
      EmulatorFoldingAction(PostureDescriptor(PostureValue.POSTURE_HALF_OPENED, PostureDescriptor.ValueType.HINGE_ANGLE, 30.0, 150.0)),
      EmulatorFoldingAction(PostureDescriptor(PostureValue.POSTURE_OPENED, PostureDescriptor.ValueType.HINGE_ANGLE, 150.0, 180.0)),
      Separator.getInstance(),
      ActionManager.getInstance().getAction(EmulatorShowVirtualSensorsAction.ID))
    for (action in foldingActions) {
      action.update(event)
      assertThat(event.presentation.isEnabled).isTrue()
      assertThat(event.presentation.isVisible).isTrue()
    }
  }

  @Test
  fun testFolding() {
    val avdFolder = FakeEmulator.createFoldableAvd(emulatorRule.avdRoot)
    val panel = createWindowPanel(avdFolder)
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryDisplayView ?: fail()

    // Check appearance.
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0u)
    panel.size = Dimension(200, 400)
    ui.layoutAndDispatchEvents()
    var call = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGB888 width: 186 height: 321")

    val foldingGroup = ActionManager.getInstance().getAction("android.device.postures") as ActionGroup
    val event = createTestEvent(emulatorView, project, ActionPlaces.TOOLBAR)
    waitForCondition(2.seconds) { foldingGroup.update(event); event.presentation.isVisible }
    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(event.presentation.text).isEqualTo("Fold/Unfold (currently Open)")
    val foldingActions = foldingGroup.getChildren(event)
    assertThat(foldingActions).asList().containsExactly(
        EmulatorFoldingAction(PostureDescriptor(PostureValue.POSTURE_CLOSED, PostureDescriptor.ValueType.HINGE_ANGLE, 0.0, 30.0)),
        EmulatorFoldingAction(PostureDescriptor(PostureValue.POSTURE_HALF_OPENED, PostureDescriptor.ValueType.HINGE_ANGLE, 30.0, 150.0)),
        EmulatorFoldingAction(PostureDescriptor(PostureValue.POSTURE_OPENED, PostureDescriptor.ValueType.HINGE_ANGLE, 150.0, 180.0)),
        Separator.getInstance(),
        ActionManager.getInstance().getAction(EmulatorShowVirtualSensorsAction.ID))
    for (action in foldingActions) {
      action.update(event)
      assertThat(event.presentation.isEnabled).isTrue()
      assertThat(event.presentation.isVisible).isTrue()
    }
    assertThat(emulatorView.deviceDisplaySize).isEqualTo(Dimension(2208, 1840))

    foldingActions.first().actionPerformed(event)
    call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setPhysicalModel")
    assertThat(shortDebugString(call.request)).isEqualTo("target: HINGE_ANGLE0 value { data: 0.0 }")
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 170 height: 341")
    waitForCondition(2.seconds) { foldingGroup.update(event); event.presentation.text == "Fold/Unfold (currently Closed)" }
    panel.waitForFrame(ui, ++frameNumber, 2.seconds)
    assertThat(emulatorView.deviceDisplaySize).isEqualTo(Dimension(1080, 2092))

    // Check EmulatorShowVirtualSensorsAction.
    val mockUIThemeLookAndFeelInfo = mock<UIThemeLookAndFeelInfoImpl>()
    whenever(mockUIThemeLookAndFeelInfo.name).thenReturn("Darcula")
    val mockLafManager = mock<LafManager>()
    whenever(mockLafManager.currentUIThemeLookAndFeel).thenReturn(mockUIThemeLookAndFeelInfo)
    ApplicationManager.getApplication().replaceService(LafManager::class.java, mockLafManager, testRootDisposable)

    foldingActions.last().actionPerformed(event)
    call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.UiController/setUiTheme")
    assertThat(call.request).isEqualTo(ThemingStyle.newBuilder().setStyle(ThemingStyle.Style.DARK).build())
    call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.UiController/showExtendedControls")
    assertThat(shortDebugString(call.request)).isEqualTo("index: VIRT_SENSORS")
  }

  @Test
  fun testZoom() {
    val panel = createWindowPanelForPhone()
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    var emulatorView = panel.primaryDisplayView ?: fail()

    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0u)
    panel.size = Dimension(400, 600)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 363 height: 515")

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
    val uiState = panel.destroyContent()
    panel.createContent(true, uiState)
    emulatorView = panel.primaryDisplayView ?: fail()
    ui.layoutAndDispatchEvents()

    // Check that zoom level and scroll position are restored.
    assertThat(emulatorView.scale).isWithin(0.0001).of(0.25)
    assertThat(viewport.viewSize).isEqualTo(Dimension(400, 811))
    assertThat(viewport.viewPosition).isEqualTo(scrollPosition)

    panel.destroyContent()
  }

  /** Checks a large container size resulting in a scale greater than 1:1. */
  @Test
  fun testZoomLargeScale() {
    val avdFolder = FakeEmulator.createWatchAvd(emulatorRule.avdRoot, api = 30)
    val panel = createWindowPanel(avdFolder)
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryDisplayView ?: fail()

    // Check appearance.
    var frameNumber = emulatorView.frameNumber
    assertThat(frameNumber).isEqualTo(0u)
    panel.size = Dimension(200, 400)
    ui.layoutAndDispatchEvents()
    val call1 = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(call1.request)).isEqualTo("format: RGB888 width: 168 height: 307")
    assertThat(emulatorView.displayRectangle!!.width).isEqualTo(168)
    assertThat(emulatorView.canZoomIn()).isTrue()
    assertThat(emulatorView.canZoomOut()).isFalse()
    assertThat(emulatorView.canZoomToActual()).isTrue()
    assertThat(emulatorView.canZoomToFit()).isFalse()

    emulatorView.zoom(ZoomType.IN)
    ui.layoutAndDispatchEvents()
    val call2 = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumber)
    assertThat(shortDebugString(call2.request)).isEqualTo("format: RGB888 width: 320 height: 320")
    assertThat(call1.completion.isCancelled).isTrue() // The previous call has been cancelled.
    assertThat(call1.completion.isDone).isTrue() // The previous call is no longer active.
    assertThat(emulatorView.displayRectangle!!.width).isEqualTo(320)
    assertThat(emulatorView.canZoomIn()).isTrue()
    assertThat(emulatorView.canZoomOut()).isTrue()
    assertThat(emulatorView.canZoomToActual()).isFalse()
    assertThat(emulatorView.canZoomToFit()).isTrue()

    emulatorView.zoom(ZoomType.IN)
    ui.layoutAndDispatchEvents()
    assertThat(call2.completion.isCancelled).isFalse() // The latest call has not been cancelled.
    assertThat(call2.completion.isDone).isFalse() // The latest call is still ongoing.
    ui.render() // Trigger displayRectangle update.
    assertThat(emulatorView.displayRectangle!!.width).isEqualTo(640)
    assertThat(emulatorView.canZoomIn()).isTrue()
    assertThat(emulatorView.canZoomOut()).isTrue()
    assertThat(emulatorView.canZoomToActual()).isTrue()
    assertThat(emulatorView.canZoomToFit()).isTrue()

    panel.size = Dimension(800, 1200)
    ui.layoutAndDispatchEvents()
    assertThat(call2.completion.isCancelled).isFalse() // The latest call has not been cancelled.
    assertThat(call2.completion.isDone).isFalse() // The latest call is still ongoing.
    ui.render() // Trigger displayRectangle update.
    assertThat(emulatorView.displayRectangle!!.width).isEqualTo(640)
    assertThat(emulatorView.canZoomIn()).isTrue()
    assertThat(emulatorView.canZoomOut()).isTrue()
    assertThat(emulatorView.canZoomToActual()).isTrue()
    assertThat(emulatorView.canZoomToFit()).isTrue()

    emulatorView.zoom(ZoomType.FIT)
    ui.layoutAndDispatchEvents()
    assertThat(call2.completion.isCancelled).isFalse() // The latest call has not been cancelled.
    assertThat(call2.completion.isDone).isFalse() // The latest call is still ongoing.
    ui.render() // Trigger displayRectangle update.
    assertThat(emulatorView.displayRectangle!!.width).isEqualTo(674)
    assertThat(emulatorView.canZoomIn()).isTrue()
    assertThat(emulatorView.canZoomOut()).isTrue()
    assertThat(emulatorView.canZoomToActual()).isTrue()
    assertThat(emulatorView.canZoomToFit()).isFalse()

    panel.size = Dimension(850, 1200)
    ui.layoutAndDispatchEvents()
    assertThat(call2.completion.isCancelled).isFalse() // The latest call has not been cancelled.
    assertThat(call2.completion.isDone).isFalse() // The latest call is still ongoing.
    ui.render() // Trigger displayRectangle update.
    assertThat(emulatorView.displayRectangle!!.width).isEqualTo(716)
    assertThat(emulatorView.canZoomIn()).isTrue()
    assertThat(emulatorView.canZoomOut()).isTrue()
    assertThat(emulatorView.canZoomToActual()).isTrue()
    assertThat(emulatorView.canZoomToFit()).isFalse()

    panel.size = Dimension(1200, 1200)
    ui.layoutAndDispatchEvents()
    assertThat(call2.completion.isCancelled).isFalse() // The latest call has not been cancelled.
    assertThat(call2.completion.isDone).isFalse() // The latest call is still ongoing.
    ui.render() // Trigger displayRectangle update.
    assertThat(emulatorView.displayRectangle!!.width).isEqualTo(960)
    assertThat(emulatorView.canZoomIn()).isTrue()
    assertThat(emulatorView.canZoomOut()).isTrue()
    assertThat(emulatorView.canZoomToActual()).isTrue()
    assertThat(emulatorView.canZoomToFit()).isTrue()

    emulatorView.zoom(ZoomType.OUT)
    ui.layoutAndDispatchEvents()
    assertThat(call2.completion.isCancelled).isFalse() // The latest call has not been cancelled.
    assertThat(call2.completion.isDone).isFalse() // The latest call is still ongoing.
    ui.render() // Trigger displayRectangle update.
    assertThat(emulatorView.displayRectangle!!.width).isEqualTo(640)
    assertThat(emulatorView.canZoomIn()).isTrue()
    assertThat(emulatorView.canZoomOut()).isTrue()
    assertThat(emulatorView.canZoomToActual()).isTrue()
    assertThat(emulatorView.canZoomToFit()).isTrue()

    emulatorView.zoom(ZoomType.ACTUAL)
    ui.layoutAndDispatchEvents()
    assertThat(call2.completion.isCancelled).isFalse() // The latest call has not been cancelled.
    assertThat(call2.completion.isDone).isFalse() // The latest call is still ongoing.
    ui.render() // Trigger displayRectangle update.
    assertThat(emulatorView.displayRectangle!!.width).isEqualTo(320)
    assertThat(emulatorView.canZoomIn()).isTrue()
    assertThat(emulatorView.canZoomOut()).isFalse()
    assertThat(emulatorView.canZoomToActual()).isFalse()
    assertThat(emulatorView.canZoomToFit()).isTrue()
  }

  @Test
  fun testMultipleDisplays() {
    val panel = createWindowPanelForPhone()
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryDisplayView ?: fail()

    // Check appearance.
    val frameNumbers = uintArrayOf(emulatorView.frameNumber, 0u, 0u)
    assertThat(frameNumbers[PRIMARY_DISPLAY_ID]).isEqualTo(0u)
    panel.size = Dimension(400, 600)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall = getStreamScreenshotCallAndWaitForFrame(ui, panel, ++frameNumbers[PRIMARY_DISPLAY_ID])
    assertThat(shortDebugString(streamScreenshotCall.request)).isEqualTo("format: RGB888 width: 363 height: 515")

    emulator.changeSecondaryDisplays(listOf(DisplayConfiguration.newBuilder().setDisplay(1).setWidth(1080).setHeight(2340).build(),
                                            DisplayConfiguration.newBuilder().setDisplay(2).setWidth(3840).setHeight(2160).build()))

    waitForCondition(2.seconds) { ui.findAllComponents<EmulatorView>().size == 3 }
    ui.layoutAndDispatchEvents()
    waitForNextFrameInAllDisplays(ui, frameNumbers)
    assertAppearance(ui, "MultipleDisplays1", maxPercentDifferentMac = 0.09, maxPercentDifferentWindows = 0.25)

    // Check that the largest display view can be zoomed 1:1.
    emulator.clearGrpcCallLog()
    val largestDisplayPanel = ui.getComponent<EmulatorDisplayPanel> { it.displayId == 2 }
    var frameNumber = largestDisplayPanel.displayView.frameNumber
    largestDisplayPanel.displayView.zoom(ZoomType.ACTUAL)
    ui.layoutAndDispatchEvents()
    val streamScreenshotCall4k = emulator.getNextGrpcCall(2.seconds)
    assertThat(streamScreenshotCall4k.methodName).isEqualTo("android.emulation.control.EmulatorController/streamScreenshot")
    largestDisplayPanel.waitForFrame(ui, ++frameNumber, 2.seconds)
    assertThat(shortDebugString(streamScreenshotCall4k.request)).isEqualTo("format: RGB888 width: 3840 height: 2160 display: 2")

    // Resize emulator display panels.
    ui.findAllComponents<SplitPanel>().forEach { it.proportion /= 2 }
    ui.layoutAndDispatchEvents()
    val displayViewSizes = ui.findAllComponents<EmulatorView>().map { it.size }

    val uiState = panel.destroyContent()
    assertThat(panel.primaryDisplayView).isNull()
    streamScreenshotCall.waitForCancellation(2.seconds)

    // Check serialization and deserialization of MultiDisplayStateStorage.
    val state = MultiDisplayStateStorage.getInstance(projectRule.project)
    val deserializedState = serialize(state)?.deserialize(MultiDisplayStateStorage::class.java)
    assertThat(deserializedState?.displayStateByAvdFolder).isEqualTo(state.displayStateByAvdFolder)

    // Check that the panel layout is recreated when the panel content is created again.
    panel.createContent(true, uiState)
    ui.layoutAndDispatchEvents()
    waitForCondition(2.seconds) { ui.findAllComponents<EmulatorView>().size == 3 }
    assertThat(ui.findAllComponents<EmulatorView>().map { it.size }).isEqualTo(displayViewSizes)
  }

  @Test
  fun testVirtualSceneCamera() {
    val panel = createWindowPanelForPhone()
    val ui = FakeUi(panel, parentDisposable = testRootDisposable)

    assertThat(panel.primaryDisplayView).isNull()

    panel.createContent(true)
    val emulatorView = panel.primaryDisplayView ?: fail()
    panel.size = Dimension(400, 600)
    ui.layoutAndDispatchEvents()

    val container = HeadlessRootPaneContainer(panel)
    val glassPane = container.glassPane

    val initialMousePosition = Point(glassPane.x + glassPane.width / 2, glassPane.y + glassPane.height / 2)
    val pointerInfo = mock<PointerInfo>()
    whenever(pointerInfo.location).thenReturn(initialMousePosition)
    val mouseInfoMock = mockStatic<MouseInfo>(testRootDisposable)
    mouseInfoMock.whenever<Any?> { MouseInfo.getPointerInfo() }.thenReturn(pointerInfo)

    val focusManager = FakeKeyboardFocusManager(testRootDisposable)
    focusManager.focusOwner = emulatorView

    assertThat(ui.findComponent<EditorNotificationPanel>()).isNull()

    // Activate the camera and check that a notification is displayed.
    emulator.virtualSceneCameraActive = true
    waitForCondition(2.seconds) { ui.findComponent<EditorNotificationPanel>() != null }
    assertThat(ui.findComponent<EditorNotificationPanel>()?.text).isEqualTo("Hold Shift to control camera")

    // Check that the notification is removed when the emulator view loses focus.
    focusManager.focusOwner = null
    val focusLostEvent = FocusEvent(emulatorView, FocusEvent.FOCUS_LOST, false, null)
    for (listener in emulatorView.focusListeners) {
      listener.focusLost(focusLostEvent)
    }
    assertThat(ui.findComponent<EditorNotificationPanel>()).isNull()

    // Check that the notification is displayed again when the emulator view gains focus.
    focusManager.focusOwner = emulatorView
    val focusGainedEvent = FocusEvent(emulatorView, FocusEvent.FOCUS_GAINED, false, null)
    for (listener in emulatorView.focusListeners) {
      listener.focusGained(focusGainedEvent)
    }
    assertThat(ui.findComponent<EditorNotificationPanel>()?.text).isEqualTo("Hold Shift to control camera")

    // Check that the notification changes when Shift is pressed.
    ui.keyboard.press(VK_SHIFT)
    assertThat(ui.findComponent<EditorNotificationPanel>()?.text)
        .isEqualTo("Move camera with WASDQE keys, rotate with mouse or arrow keys")

    // Check camera movement.
    val velocityExpectations =
        mapOf('W' to "z: -1.0", 'A' to "x: -1.0", 'S' to "z: 1.0", 'D' to "x: 1.0", 'Q' to "y: -1.0", 'E' to "y: 1.0")
    val callFilter = FakeEmulator.DEFAULT_CALL_FILTER.or("android.emulation.control.EmulatorController/streamClipboard",
                                                         "android.emulation.control.EmulatorController/streamScreenshot")
    for ((key, expected) in velocityExpectations) {
      ui.keyboard.press(key.code)

      var call = emulator.getNextGrpcCall(2.seconds, callFilter)
      assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setVirtualSceneCameraVelocity")
      assertThat(shortDebugString(call.request)).isEqualTo(expected)
      ui.keyboard.release(key.code)
      call = emulator.getNextGrpcCall(2.seconds, callFilter)
      assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setVirtualSceneCameraVelocity")
      assertThat(shortDebugString(call.request)).isEqualTo("")
    }

    // Check camera rotation.
    val x = initialMousePosition.x + 5
    val y = initialMousePosition.y + 10
    val event = MouseEvent(glassPane, MOUSE_MOVED, System.currentTimeMillis(), ui.keyboard.toModifiersCode(), x, y, x, y, 0, false, 0)
    glassPane.dispatch(event)
    var call = emulator.getNextGrpcCall(2.seconds, callFilter)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/rotateVirtualSceneCamera")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 2.3561945 y: 1.5707964")

    val rotationExpectations = mapOf(VK_LEFT to "y: 0.08726646", VK_RIGHT to "y: -0.08726646",
                                     VK_UP to "x: 0.08726646", VK_DOWN to "x: -0.08726646",
                                     VK_HOME to "x: 0.08726646 y: 0.08726646", VK_END to "x: -0.08726646 y: 0.08726646",
                                     VK_PAGE_UP to "x: 0.08726646 y: -0.08726646", VK_PAGE_DOWN to "x: -0.08726646 y: -0.08726646")
    for ((key, expected) in rotationExpectations) {
      ui.keyboard.pressAndRelease(key)
      call = emulator.getNextGrpcCall(2.seconds, callFilter)
      assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/rotateVirtualSceneCamera")
      assertThat(shortDebugString(call.request)).isEqualTo(expected)
    }

    // Check that the notification changes when Shift is released.
    ui.keyboard.release(VK_SHIFT)
    assertThat(ui.findComponent<EditorNotificationPanel>()?.text).isEqualTo("Hold Shift to control camera")

    // Deactivate the camera and check that the notification is removed.
    emulator.virtualSceneCameraActive = false
    waitForCondition(2.seconds) { ui.findComponent<EditorNotificationPanel>() == null }

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

  private fun FakeUi.mouseClickOn(component: Component) {
    val location: Point = getPosition(component)
    mouse.click(location.x, location.y)
    // Allow events to propagate.
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  private fun getStreamScreenshotCallAndWaitForFrame(fakeUi: FakeUi, panel: EmulatorToolWindowPanel, frameNumber: UInt): GrpcCallRecord {
    val call = emulator.getNextGrpcCall(2.seconds)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamScreenshot")
    panel.waitForFrame(fakeUi, frameNumber, 2.seconds)
    return call
  }

  private fun createWindowPanelForPhone(): EmulatorToolWindowPanel {
    val avdFolder = FakeEmulator.createPhoneAvd(emulatorRule.avdRoot)
    return createWindowPanel(avdFolder)
  }

  private fun createWindowPanelForXr(): EmulatorToolWindowPanel {
    val avdFolder = FakeEmulator.createXrAvd(emulatorRule.avdRoot)
    return createWindowPanel(avdFolder)
  }

  private fun createWindowPanel(avdFolder: Path): EmulatorToolWindowPanel {
    emulator = emulatorRule.newEmulator(avdFolder)
    emulator.start()
    val catalog = RunningEmulatorCatalog.getInstance()
    val emulators = runBlocking { catalog.updateNow().await() }
    assertThat(emulators).hasSize(1)
    val emulatorController = emulators.first()
    val panel = EmulatorToolWindowPanel(testRootDisposable, projectRule.project, emulatorController)
    panel.zoomToolbarVisible = true
    waitForCondition(5.seconds) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    return panel
  }

  @Throws(TimeoutException::class)
  private fun EmulatorToolWindowPanel.waitForFrame(fakeUi: FakeUi, frame: UInt, timeout: Duration) {
    waitForCondition(timeout) { renderAndGetFrameNumber(fakeUi, primaryDisplayView!!) >= frame }
  }

  @Throws(TimeoutException::class)
  private fun EmulatorDisplayPanel.waitForFrame(fakeUi: FakeUi, frame: UInt, timeout: Duration) {
    waitForCondition(timeout) { renderAndGetFrameNumber(fakeUi, displayView) >= frame }
  }

  @Throws(TimeoutException::class)
  private fun waitForNextFrameInAllDisplays(fakeUi: FakeUi, frameNumbers: UIntArray) {
    val displayViews = fakeUi.findAllComponents<EmulatorView>()
    assertThat(displayViews.size).isEqualTo(frameNumbers.size)
    waitForCondition(2.seconds) {
      fakeUi.render()
      for (view in displayViews) {
        if (view.frameNumber <= frameNumbers[view.displayId]) {
          return@waitForCondition false
        }
      }
      for (view in displayViews) {
        frameNumbers[view.displayId] = view.frameNumber
      }
      return@waitForCondition true
    }
  }

  private fun renderAndGetFrameNumber(fakeUi: FakeUi, emulatorView: EmulatorView): UInt {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return emulatorView.frameNumber
  }

  private fun getNextGrpcCallIgnoringStreamScreenshot(): GrpcCallRecord =
      emulator.getNextGrpcCall(2.seconds, IGNORE_SCREENSHOT_CALL_FILTER)

  private fun assertAppearance(ui: FakeUi,
                               goldenImageName: String,
                               maxPercentDifferentLinux: Double = 0.0003,
                               maxPercentDifferentMac: Double = 0.0003,
                               maxPercentDifferentWindows: Double = 0.0003) {
    ui.updateToolbars()
    val image = ui.render()
    val scaledImage = ImageUtils.scale(image, 0.5)
    val maxPercentDifferent = when {
      SystemInfo.isMac -> maxPercentDifferentMac
      SystemInfo.isWindows -> maxPercentDifferentWindows
      else -> maxPercentDifferentLinux
    }
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), scaledImage, maxPercentDifferent)
  }

  private fun getGoldenFile(name: String): Path {
    return TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/golden/${name}.png")
  }
}

private const val TEST_DATA_PATH = "tools/adt/idea/streaming/testData/EmulatorToolWindowPanelTest"
