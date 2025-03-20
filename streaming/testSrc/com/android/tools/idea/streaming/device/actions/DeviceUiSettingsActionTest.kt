/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.device.actions

import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.adblib.DevicePropertyNames
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.streaming.createTestEvent
import com.android.tools.idea.streaming.device.DeviceClient
import com.android.tools.idea.streaming.device.DeviceView
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule.FakeDevice
import com.android.tools.idea.streaming.device.UNKNOWN_ORIENTATION
import com.android.tools.idea.streaming.uisettings.ui.APP_LANGUAGE_TITLE
import com.android.tools.idea.streaming.uisettings.ui.DARK_THEME_TITLE
import com.android.tools.idea.streaming.uisettings.ui.DENSITY_TITLE
import com.android.tools.idea.streaming.uisettings.ui.FONT_SCALE_TITLE
import com.android.tools.idea.streaming.uisettings.ui.GESTURE_NAVIGATION_TITLE
import com.android.tools.idea.streaming.uisettings.ui.SELECT_TO_SPEAK_TITLE
import com.android.tools.idea.streaming.uisettings.ui.TALKBACK_TITLE
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsPanel
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.awt.RelativePoint
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.WindowFocusListener
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JSlider
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds

class DeviceUiSettingsActionTest {
  private val agentRule = FakeScreenSharingAgentRule()
  private val popupRule = JBPopupRule()

  @get:Rule
  val ruleChain: RuleChain = RuleChain(
    agentRule,
    popupRule
  )

  private val project
    get() = agentRule.project

  private val popupFactory
    get() = popupRule.fakePopupFactory

  private val testRootDisposable
    get() = agentRule.disposable

  @After
  fun after() {
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
  }

  @Test
  fun testActionOnApi32Device() {
    val action = DeviceUiSettingsAction()
    val view = connectDeviceAndCreateView(32)
    val event = createTestKeyEvent(view)
    action.update(event)
    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun testActiveAction() {
    val action = DeviceUiSettingsAction()
    val view = connectDeviceAndCreateView()
    val event = createTestMouseEvent(action, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    action.actionPerformed(event)
    waitForCondition(10.seconds) { popupFactory.balloonCount > 0 }
    val balloon = popupFactory.getNextBalloon()
    waitForCondition(10.seconds) { balloon.isShowing }
    assertThat(balloon.component).isInstanceOf(UiSettingsPanel::class.java)
    assertThat((balloon.target as RelativePoint).originalComponent).isSameAs(view)
    assertThat((balloon.target as RelativePoint).originalPoint).isEqualTo(Point(0, 400))
  }

  @Test
  fun testWearControls() {
    val action = DeviceUiSettingsAction()
    val view = connectDeviceAndCreateView(isWear = true)
    val event = createTestMouseEvent(action, view)
    action.actionPerformed(event)
    waitForCondition(10.seconds) { popupFactory.balloonCount > 0 }
    val balloon = popupFactory.getNextBalloon()
    waitForCondition(10.seconds) { balloon.isShowing }
    val panel = balloon.component
    assertThat(panel.findDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }).isNull()
    assertThat(panel.findDescendant<JComboBox<*>> { it.name == APP_LANGUAGE_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == TALKBACK_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JSlider> { it.name == FONT_SCALE_TITLE }).isNotNull()

    assertThat(panel.findDescendant<JCheckBox> { it.name == GESTURE_NAVIGATION_TITLE }).isNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }).isNull()
    assertThat(panel.findDescendant<JSlider> { it.name == DENSITY_TITLE }).isNull()
  }

  @Test
  fun testPickerClosesWhenWindowCloses() {
    val action = DeviceUiSettingsAction()
    val view = connectDeviceAndCreateView()
    val event = createTestKeyEvent(view)
    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    runInEdtAndWait { FakeUi(view, createFakeWindow = true, parentDisposable = testRootDisposable) }
    val window = SwingUtilities.windowForComponent(view)
    val listeners = mutableListOf<WindowFocusListener>()
    Mockito.doAnswer { invocation ->
      listeners.add(invocation.arguments[0] as WindowFocusListener)
    }.whenever(window).addWindowFocusListener(any())

    action.actionPerformed(event)
    waitForCondition(10.seconds) { popupFactory.balloonCount > 0 }
    val balloon = popupFactory.getNextBalloon()
    waitForCondition(10.seconds) { balloon.isShowing }

    listeners.forEach { it.windowLostFocus(mock()) }
    assertThat(balloon.isDisposed).isTrue()
  }

  @Test
  fun testPickerClosesWithParentDisposable() {
    val parentDisposable = Disposer.newDisposable()
    Disposer.register(testRootDisposable, parentDisposable)

    val action = DeviceUiSettingsAction()
    val view = connectDeviceAndCreateView(parentDisposable = parentDisposable)
    val event = createTestMouseEvent(action, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    action.actionPerformed(event)
    waitForCondition(10.seconds) { popupFactory.balloonCount > 0 }
    val balloon = popupFactory.getNextBalloon()
    waitForCondition(10.seconds) { balloon.isShowing }

    Disposer.dispose(parentDisposable)
    assertThat(balloon.isDisposed).isTrue()
  }

  private fun createTestMouseEvent(action: AnAction, view: DeviceView): AnActionEvent {
    val keyEvent = createTestKeyEvent(view)
    val component = createActionButton(action)
    val input = MouseEvent(component, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 10, 10, 1, false)
    return AnActionEvent.createEvent(keyEvent.dataContext, keyEvent.presentation, ActionPlaces.TOOLBAR, ActionUiKind.TOOLBAR, input)
  }

  private fun createTestKeyEvent(view: DeviceView): AnActionEvent =
    createTestEvent(view, project)

  private fun connectDeviceAndCreateView(
    apiLevel: Int = 33,
    isWear: Boolean = false,
    parentDisposable: Disposable = testRootDisposable
  ): DeviceView {
    val device = agentRule.connectDevice(
      "Pixel 8",
      apiLevel,
      Dimension(1344, 2992),
      screenDensity = 480,
      additionalDeviceProperties = if (isWear) mapOf(DevicePropertyNames.RO_BUILD_CHARACTERISTICS to "watch") else emptyMap()
    )
    val view = createDeviceView(device, parentDisposable)
    view.setBounds(0, 0, 600, 800)
    waitForFrame(view)
    return view
  }

  private fun createActionButton(action: AnAction) = ActionButton(
    action,
    action.templatePresentation.clone(),
    ActionPlaces.TOOLBAR,
    Dimension(16, 16)
  ).apply { size = Dimension(16, 16) }

  private fun createDeviceView(device: FakeDevice, parentDisposable: Disposable): DeviceView {
    val deviceClient = DeviceClient(device.serialNumber, device.configuration, device.deviceState.cpuAbi)
    Disposer.register(parentDisposable, deviceClient)
    return DeviceView(parentDisposable, deviceClient, project, PRIMARY_DISPLAY_ID, UNKNOWN_ORIENTATION)
  }

  private fun waitForFrame(view: DeviceView) {
    val ui = FakeUi(view)
    waitForCondition(10.seconds) {
      ui.render()
      view.isConnected && view.frameNumber > 0u
    }
  }
}
