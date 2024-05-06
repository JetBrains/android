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

import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.createTestEvent
import com.android.tools.idea.streaming.device.DeviceClient
import com.android.tools.idea.streaming.device.DeviceView
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule
import com.android.tools.idea.streaming.device.FakeScreenSharingAgentRule.FakeDevice
import com.android.tools.idea.streaming.device.UNKNOWN_ORIENTATION
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsPanel
import com.android.tools.idea.testing.flags.override
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RuleChain
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.awt.event.MouseEvent
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

  @Test
  fun testUpdateWhenUnused() {
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(false, testRootDisposable)
    val action = DeviceUiSettingsAction()
    val view = connectDeviceAndCreateView(34)
    val event = createTestKeyEvent(view)
    action.update(event)
    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun testActionOnApi33Device() {
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(true, testRootDisposable)
    val action = DeviceUiSettingsAction()
    val view = connectDeviceAndCreateView(33)
    val event = createTestKeyEvent(view)
    action.update(event)
    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun testActiveAction() {
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(true, testRootDisposable)
    val action = DeviceUiSettingsAction()
    val view = connectDeviceAndCreateView(34)
    val event = createTestMouseEvent(action, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    action.actionPerformed(event)
    waitForCondition(10.seconds) { popupFactory.balloonCount > 0 }
    val balloon = popupFactory.getNextBalloon()
    Disposer.register(agentRule.disposable, balloon)
    assertThat(balloon.component).isInstanceOf(UiSettingsPanel::class.java)
  }

  @Test
  fun testActiveActionViaKeyboard() {
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(true)
    val action = DeviceUiSettingsAction()
    val view = connectDeviceAndCreateView(34)
    val event = createTestKeyEvent(view)
    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    action.actionPerformed(event)
    waitForCondition(10.seconds) { popupFactory.balloonCount > 0 }
    val balloon = popupRule.fakePopupFactory.getNextBalloon()
    Disposer.register(agentRule.disposable, balloon)
    assertThat(balloon.component).isInstanceOf(UiSettingsPanel::class.java)
  }

  private fun createTestMouseEvent(action: AnAction, view: DeviceView): AnActionEvent {
    val keyEvent = createTestKeyEvent(view)
    val component = createActionButton(action)
    val input = MouseEvent(component, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 10, 10, 1, false)
    return AnActionEvent(input, keyEvent.dataContext, ActionPlaces.TOOLBAR, keyEvent.presentation, ActionManager.getInstance(), 0)
  }

  private fun createTestKeyEvent(view: DeviceView): AnActionEvent =
    createTestEvent(view, project)

  private fun connectDeviceAndCreateView(apiLevel: Int): DeviceView {
    val device = agentRule.connectDevice("Pixel 8", apiLevel, Dimension(1080, 2280))
    val view = createDeviceView(device)
    view.setBounds(0, 0, 600, 800)
    waitForFrame(view)
    return view
  }

  private fun createActionButton(action: AnAction) = ActionButton(
    action,
    action.templatePresentation.clone(),
    ActionPlaces.TOOLBAR,
    Dimension(16, 16)
  )

  private fun createDeviceView(device: FakeDevice): DeviceView {
    val deviceClient = DeviceClient(device.serialNumber, device.configuration, device.deviceState.cpuAbi)
    Disposer.register(testRootDisposable, deviceClient)
    return DeviceView(testRootDisposable, deviceClient, PRIMARY_DISPLAY_ID, UNKNOWN_ORIENTATION, project)
  }

  private fun waitForFrame(view: DeviceView) {
    val ui = FakeUi(view)
    waitForCondition(10.seconds) {
      ui.render()
      view.isConnected && view.frameNumber > 0u
    }
  }
}
