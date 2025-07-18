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

import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.adblib.DevicePropertyNames.RO_BUILD_CHARACTERISTICS
import com.android.testutils.TestUtils
import com.android.testutils.ImageDiffUtil
import com.android.testutils.waitForCondition
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.idea.streaming.ClipboardSynchronizationDisablementRule
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.actions.FloatingXrToolbarState
import com.android.tools.idea.streaming.actions.HardwareInputStateStorage
import com.android.tools.idea.streaming.actions.ToggleFloatingXrToolbarAction
import com.android.tools.idea.streaming.core.DisplayType
import com.android.tools.idea.streaming.core.FloatingToolbarContainer
import com.android.tools.idea.streaming.createTestEvent
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN_AND_UP
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_UP
import com.android.tools.idea.streaming.device.DeviceState.Property.PROPERTY_POLICY_CANCEL_WHEN_REQUESTER_NOT_ON_TOP
import com.android.tools.idea.streaming.device.FakeScreenSharingAgent.Companion.defaultControlMessageFilter
import com.android.tools.idea.streaming.device.FakeScreenSharingAgent.ControlMessageFilter
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule.FakeDevice
import com.android.tools.idea.streaming.device.actions.DeviceFoldingAction
import com.android.tools.idea.streaming.device.xr.DeviceXrInputController
import com.android.tools.idea.streaming.executeStreamingAction
import com.android.tools.idea.streaming.updateAndGetActionPresentation
import com.android.tools.idea.streaming.xr.XrInputMode
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
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent.createEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
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
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.KeyEvent.VK_W
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
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration
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
    whenever(mockScreenRecordingCache.isScreenRecordingSupported(any())).thenReturn(true)
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
    waitForCondition(10.seconds) { agent.isRunning && panel.isConnected }

    // Check appearance.
    waitForFrame(5.seconds)
    assertAppearance("AppearanceAndToolbarActions1",  maxPercentDifferentLinux = 0.03, maxPercentDifferentMac = 0.06,
                     maxPercentDifferentWindows = 0.06)
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
    action.actionPerformed(createEvent(action, dataContext, null, ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, keyEvent))
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_DOWN_AND_UP, AKEYCODE_POWER, 0))

    // Check DevicePowerAndVolumeUpButtonAction invoked by a keyboard shortcut.
    action = ActionManager.getInstance().getAction("android.device.power.and.volume.up.button")
    keyEvent = KeyEvent(panel, KEY_RELEASED, System.currentTimeMillis(), CTRL_DOWN_MASK or SHIFT_DOWN_MASK, VK_P, KeyEvent.CHAR_UNDEFINED)
    action.actionPerformed(createEvent(action, dataContext, null, ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, keyEvent))
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
    waitForCondition(10.seconds) { agent.isRunning && panel.isConnected }

    // Check appearance.
    waitForFrame()
    assertThat(panel.preferredFocusableComponent).isEqualTo(panel.primaryDisplayView)
    assertThat((panel.icon as LayeredIcon).getIcon(0)).isEqualTo(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR)

    fakeUi.updateToolbarsIfNecessary()
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
  fun testXrToolbarActions() {
    // Move XR buttons to the Running Devices toolbar to check its appearance.
    service<FloatingXrToolbarState>()::floatingXrToolbarEnabled.override(false, testRootDisposable)
    device = agentRule.connectDevice("XR Headset", 34, Dimension(2560, 2558),
                                     additionalDeviceProperties = mapOf(RO_BUILD_CHARACTERISTICS to "xr"))
    panel.createContent(false)
    val displayView = panel.primaryDisplayView ?: fail()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(10.seconds) { agent.isRunning && panel.isConnected }
    waitForFrame()
    fakeUi.updateToolbarsIfNecessary()

    // Check that the buttons not applicable to XR devices are hidden.
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Power" }).isNotNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Volume Up" }).isNotNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Volume Down" }).isNotNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Rotate Left" }).isNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Rotate Right" }).isNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Back" }).isNotNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Home" }).isNotNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Overview" }).isNotNull()

    // Check XR-specific actions.
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Reset View" }).isNotNull()
    assertThat(fakeUi.findComponent<ActionButton> { it.action.templateText == "Toggle Passthrough" }).isNotNull()

    val xrInputController = DeviceXrInputController.getInstance(project, panel.deviceClient)
    assertAppearance("XrToolbarActions1", maxPercentDifferentMac = 0.04, maxPercentDifferentWindows = 0.15)

    assertThat(xrInputController.inputMode).isEqualTo(XrInputMode.INTERACTION)
    val modes = mapOf(
      "Interact with Apps" to XrInputMode.INTERACTION,
      "View Direction" to XrInputMode.VIEW_DIRECTION,
      "Move Right/Left and Up/Down" to XrInputMode.LOCATION_IN_SPACE_XY,
      "Move Forward/Backward" to XrInputMode.LOCATION_IN_SPACE_Z,
    )
    for ((actionName, mode) in modes) {
      fakeUi.mouseClickOn(fakeUi.getComponent<ActionButton> { it.action.templateText == actionName })
      assertThat(xrInputController.inputMode).isEqualTo(mode)
    }

    val button = fakeUi.getComponent<ActionButton> { it.action.templateText == "Home" }
    fakeUi.mousePressOn(button)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_DOWN, AKEYCODE_ALL_APPS, 0))
    fakeUi.mouseRelease()
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_UP, AKEYCODE_ALL_APPS, 0))

    fakeUi.mouseClickOn(fakeUi.getComponent<ActionButton> { it.action.templateText == "Reset View" })
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(XrRecenterMessage())

    waitForCondition(2.seconds) { xrInputController.passthroughCoefficient >= 0 }
    assertThat(xrInputController.passthroughCoefficient).isEqualTo(0f)
    val togglePassthroughButton = fakeUi.getComponent<ActionButton> { it.action.templateText == "Toggle Passthrough" }
    assertThat(togglePassthroughButton.isEnabled).isTrue()
    assertThat(togglePassthroughButton.isSelected).isFalse()
    fakeUi.mouseClickOn(togglePassthroughButton)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(XrSetPassthroughCoefficientMessage(1F))
    waitForCondition(2.seconds) { xrInputController.passthroughCoefficient != 0f }
    assertThat(xrInputController.passthroughCoefficient).isEqualTo(1f)
    fakeUi.updateToolbarsIfNecessary()
    assertThat(togglePassthroughButton.isSelected).isTrue()

    val toggleAction = ToggleFloatingXrToolbarAction()
    toggleAction.actionPerformed(createTestEvent(displayView, project, ActionPlaces.TOOLWINDOW_POPUP))
    assertAppearance("XrToolbarActions2", maxPercentDifferentMac = 0.04, maxPercentDifferentWindows = 0.15)
  }

  @Test
  fun testXrKeyboardNavigation() {
    device = agentRule.connectDevice("XR Headset", 34, Dimension(2560, 2558),
                                     additionalDeviceProperties = mapOf(RO_BUILD_CHARACTERISTICS to "xr"))
    panel.createContent(false)
    val displayView = panel.primaryDisplayView ?: fail()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(10.seconds) { agent.isRunning && panel.isConnected }
    waitForFrame()

    expandFloatingToolbar()
    fakeUi.mouseClickOn(fakeUi.getComponent<ActionButton> { it.action.templateText == "View Direction" })

    val xrInputController = DeviceXrInputController.getInstance(project, displayView.deviceClient)
    assertThat(xrInputController.inputMode).isEqualTo(XrInputMode.VIEW_DIRECTION)
    assertThat(project.service<HardwareInputStateStorage>().isHardwareInputEnabled(displayView.deviceId)).isFalse()

    fakeUi.keyboard.setFocus(displayView)
    fakeUi.keyboard.press(VK_ENTER)
    // Keys that are not used for navigation produce keypress events.
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(KeyEventMessage(ACTION_DOWN_AND_UP, AKEYCODE_ENTER, 0))
    fakeUi.keyboard.release(VK_ENTER)

    val velocityKeys = mapOf(
      VK_W to XrVelocityMessage(0f, 0f, -1f),
      VK_A to XrVelocityMessage(-1f, 0f, 0f),
      VK_S to XrVelocityMessage(0f, 0f, 1f),
      VK_D to XrVelocityMessage(1f, 0f, 0f),
      VK_Q to XrVelocityMessage(0f, -1f, 0f),
      VK_E to XrVelocityMessage(0f, 1f, 0f),
    )
    for ((k, message) in velocityKeys) {
      fakeUi.keyboard.press(k)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(message)
      fakeUi.keyboard.release(k)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(XrVelocityMessage(0f, 0f, 0f))
    }

    // Expectations are represented by strings to avoid rounding problems.
    val angularVelocityKeys = mapOf(
      VK_RIGHT to "XrAngularVelocityMessage(x = 0.0, y = -0.5235988)",
      VK_LEFT to "XrAngularVelocityMessage(x = 0.0, y = 0.5235988)",
      VK_UP to "XrAngularVelocityMessage(x = 0.5235988, y = 0.0)",
      VK_DOWN to "XrAngularVelocityMessage(x = -0.5235988, y = 0.0)",
      VK_PAGE_UP to "XrAngularVelocityMessage(x = 0.5235988, y = -0.5235988)",
      VK_PAGE_DOWN to "XrAngularVelocityMessage(x = -0.5235988, y = -0.5235988)",
      VK_HOME to "XrAngularVelocityMessage(x = 0.5235988, y = 0.5235988)",
      VK_END to "XrAngularVelocityMessage(x = -0.5235988, y = 0.5235988)",
    )
    for ((k, message) in angularVelocityKeys) {
      fakeUi.keyboard.press(k)
      assertThat(getNextControlMessageAndWaitForFrame().toString()).isEqualTo(message)
      fakeUi.keyboard.release(k)
      assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(XrAngularVelocityMessage(0f, 0f))
    }

    // Two keys pressed together.
    fakeUi.keyboard.press(VK_D)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(XrVelocityMessage(1f, 0f, 0f))
    fakeUi.keyboard.press(VK_E)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(XrVelocityMessage(1f, 1f, 0f))
    fakeUi.keyboard.press(VK_A)
    // D and A cancel each other out.
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(XrVelocityMessage(0f, 1f, 0f))
    fakeUi.keyboard.release(VK_D)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(XrVelocityMessage(-1f, 1f, 0f))
    fakeUi.keyboard.press(VK_Q)
    // E and Q cancel each other out.
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(XrVelocityMessage(-1f, 0f, 0f))
    fakeUi.keyboard.release(VK_E)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(XrVelocityMessage(-1f, -1f, 0f))

    expandFloatingToolbar()
    fakeUi.mouseClickOn(fakeUi.getComponent<ActionButton> { it.action.templateText == "Interact with Apps" })
    // Switching to Interact with Apps resets state of the navigation keys.
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(XrVelocityMessage(0f, 0f, 0f))
    fakeUi.keyboard.release(VK_A)
    fakeUi.keyboard.release(VK_Q)
  }

  @Test
  fun testXrMouseViewRotation() {
    device = agentRule.connectDevice("XR Headset", 34, Dimension(2560, 2558),
                                     additionalDeviceProperties = mapOf(RO_BUILD_CHARACTERISTICS to "xr"))
    panel.createContent(false)
    val displayView = panel.primaryDisplayView ?: fail()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(10.seconds) { agent.isRunning && panel.isConnected }
    waitForFrame()

    expandFloatingToolbar()
    fakeUi.mouseClickOn(fakeUi.getComponent<ActionButton> { it.action.templateText == "View Direction" })

    val xrInputController = DeviceXrInputController.getInstance(project, displayView.deviceClient)
    assertThat(xrInputController.inputMode).isEqualTo(XrInputMode.VIEW_DIRECTION)

    fakeUi.mouse.press(50, 70)
    fakeUi.mouse.dragTo(200, 70)
    assertThat(getNextControlMessageAndWaitForFrame().toString()).isEqualTo("XrRotationMessage(x = 0.0, y = -0.017356513)")
    fakeUi.mouse.dragTo(200, 200)
    assertThat(getNextControlMessageAndWaitForFrame().toString()).isEqualTo("XrRotationMessage(x = -0.0150423115, y = 0.0)")
    fakeUi.mouse.dragTo(200, 10) // Exit the DeviceView component.
    fakeUi.mouse.dragTo(100, 70) // Enter the DeviceView component in a different location.
    fakeUi.mouse.dragTo(150, 200)
    assertThat(getNextControlMessageAndWaitForFrame().toString()).isEqualTo("XrRotationMessage(x = -0.0150423115, y = -0.0057855044)")
  }

  @Test
  fun testXrMouseMovementInSpace() {
    device = agentRule.connectDevice("XR Headset", 34, Dimension(2560, 2558),
                                     additionalDeviceProperties = mapOf(RO_BUILD_CHARACTERISTICS to "xr"))
    panel.createContent(false)
    val displayView = panel.primaryDisplayView ?: fail()

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(10.seconds) { agent.isRunning && panel.isConnected }
    waitForFrame()

    expandFloatingToolbar()
    fakeUi.mouseClickOn(fakeUi.getComponent<ActionButton> { it.action.templateText == "Move Right/Left and Up/Down" })

    val xrInputController = DeviceXrInputController.getInstance(project, displayView.deviceClient)
    assertThat(xrInputController.inputMode).isEqualTo(XrInputMode.LOCATION_IN_SPACE_XY)

    fakeUi.mouse.press(50, 70)
    fakeUi.mouse.dragTo(200, 70)
    assertThat(getNextControlMessageAndWaitForFrame().toString()).isEqualTo("XrTranslationMessage(x = -0.022099, y = 0.0, z = 0.0)")
    fakeUi.mouse.dragTo(200, 200)
    assertThat(getNextControlMessageAndWaitForFrame().toString()).isEqualTo("XrTranslationMessage(x = 0.0, y = 0.019152466, z = 0.0)")
    fakeUi.mouse.dragTo(200, 10) // Exit the DeviceView component.
    fakeUi.mouse.dragTo(100, 70) // Enter the DeviceView component in a different location.
    fakeUi.mouse.dragTo(150, 200)
    assertThat(getNextControlMessageAndWaitForFrame().toString()).isEqualTo(
        "XrTranslationMessage(x = -0.007366333, y = 0.019152466, z = 0.0)")
    fakeUi.mouse.release()

    // Moving forward and backward by rotating the mouse wheel.
    fakeUi.mouse.wheel(10, 100, 1)
    assertThat(getNextControlMessageAndWaitForFrame().toString()).isEqualTo("XrTranslationMessage(x = 0.0, y = 0.0, z = 0.0625)")
    fakeUi.mouse.wheel(10, 100, -3)
    assertThat(getNextControlMessageAndWaitForFrame().toString()).isEqualTo("XrTranslationMessage(x = 0.0, y = 0.0, z = -0.1875)")

    expandFloatingToolbar()
    fakeUi.mouseClickOn(fakeUi.getComponent<ActionButton> { it.action.templateText == "Move Forward/Backward" })
    assertThat(xrInputController.inputMode).isEqualTo(XrInputMode.LOCATION_IN_SPACE_Z)
    assertThat(project.service<HardwareInputStateStorage>().isHardwareInputEnabled(displayView.deviceId)).isFalse()

    fakeUi.mouse.press(50, 70)
    fakeUi.mouse.dragTo(100, 200)
    assertThat(getNextControlMessageAndWaitForFrame().toString()).isEqualTo("XrTranslationMessage(x = 0.0, y = 0.0, z = 0.019152466)")
    fakeUi.mouse.release()
  }

  @Test
  @Suppress("OverrideOnly")
  fun testFolding() {
    device = agentRule.connectDevice("Pixel Fold", 33, Dimension(2208, 1840), foldedSize = Dimension(1080, 2092))

    panel.createContent(false)
    val deviceView = panel.primaryDisplayView!!

    fakeUi.layoutAndDispatchEvents()
    waitForCondition(10.seconds) { agent.isRunning && panel.isConnected }

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
    waitForFrame(displayId = PRIMARY_DISPLAY_ID)
    waitForFrame(displayId = externalDisplayId)
    assertAppearance("MultipleDisplays1", maxPercentDifferentMac = 0.06, maxPercentDifferentWindows = 0.06)

    agent.clearCommandLog()
    // Rotating the device. Only the internal display should rotate.
    executeStreamingAction("android.device.rotate.left", panel.primaryDisplayView!!, project)
    assertThat(getNextControlMessageAndWaitForFrame()).isEqualTo(SetDeviceOrientationMessage(orientation=1))
    waitForFrame(displayId = externalDisplayId)
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
    // The 5% of extra sound duration is intended to make sure that there is enough data to satisfy
    // the following wait condition.
    runBlocking { agent.beep(frequencyHz, durationMillis * 105 / 100) }
    waitForCondition(5.seconds) {
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
    val filter = defaultControlMessageFilter.or(SetMaxVideoResolutionMessage.TYPE)
    assertThat(agent.getNextControlMessage(1.seconds, filter)).isEqualTo(StartAudioStreamMessage())
    waitForCondition(1.seconds) { agent.audioStreamActive }

    DeviceMirroringSettings.getInstance().redirectAudio = false
    assertThat(agent.getNextControlMessage(1.seconds, filter)).isEqualTo(StopAudioStreamMessage())
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
    waitForFrame(displayId = displayId)
    return message
  }

  /** Waits for all video frames to be received after the given one. */
  private fun waitForFrame(timeout: Duration = 2.seconds, displayId: Int = PRIMARY_DISPLAY_ID, minFrameNumber: UInt = 1u) {
    waitForCondition(timeout) {
      panel.isConnected &&
      agent.getFrameNumber(displayId) >= minFrameNumber &&
      renderAndGetFrameNumber(displayId) == agent.getFrameNumber(displayId)
    }
  }

  private fun renderAndGetFrameNumber(displayId: Int = PRIMARY_DISPLAY_ID): UInt {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return panel.findDisplayView(displayId)!!.frameNumber
  }

  private fun expandFloatingToolbar() {
    fakeUi.layoutAndDispatchEvents()
    val toolbar = fakeUi.getComponent<FloatingToolbarContainer>()
    // Trigger expansion of the floating toolbar.
    fakeUi.mouse.moveTo(toolbar.locationOnScreen.x + toolbar.width / 2, toolbar.locationOnScreen.y + toolbar.height - toolbar.width / 2)
    fakeUi.layoutAndDispatchEvents()
    waitForCondition(1.seconds) { toolbar.activationFactor == 1.0 }
    fakeUi.layoutAndDispatchEvents()
  }

  @Suppress("SameParameterValue")
  private fun assertAppearance(goldenImageName: String,
                               maxPercentDifferentLinux: Double = 0.0003,
                               maxPercentDifferentMac: Double = 0.0003,
                               maxPercentDifferentWindows: Double = 0.0003) {
    fakeUi.updateToolbarsIfNecessary()
    val maxPercentDifferent = when {
      SystemInfo.isMac -> maxPercentDifferentMac
      SystemInfo.isWindows -> maxPercentDifferentWindows
      else -> maxPercentDifferentLinux
    }
    // First rendering may be low quality.
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), fakeUi.render(), max(maxPercentDifferent, 0.7),
                                     ignoreMissingGoldenFile = true)
    // Second rendering is guaranteed to be high quality.
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), fakeUi.render(), maxPercentDifferent)
  }

  private fun getGoldenFile(name: String): Path {
    return TestUtils.resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/${name}.png")
  }

  private val DeviceToolWindowPanel.isConnected
    get() = primaryDisplayView?.isConnected == true

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
