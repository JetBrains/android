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
package com.android.tools.idea.streaming.emulator.actions

import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.emulator.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.streaming.emulator.EMULATOR_VIEW_KEY
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.streaming.emulator.FakeEmulatorRule
import com.android.tools.idea.streaming.emulator.RunningEmulatorCatalog
import com.android.tools.idea.streaming.emulator.UiSettingsRule
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsPanel
import com.android.tools.idea.testing.flags.override
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RuleChain
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit

const val EMULATOR_PORT = 5554

class EmulatorUiSettingsActionTest {
  private val popupRule = JBPopupRule()
  private val uiRule = UiSettingsRule(EMULATOR_PORT)
  private val emulatorRule = FakeEmulatorRule()

  @get:Rule
  val ruleChain: RuleChain = RuleChain(
    uiRule,
    emulatorRule,
    popupRule
  )

  private val popupFactory
    get() = popupRule.fakePopupFactory

  private val testRootDisposable
    get() = uiRule.testRootDisposable

  @Test
  fun testUpdateWhenUnused() {
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(false, testRootDisposable)
    val controller = createEmulator(34)
    val view = createEmulatorView(controller)
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun testActionOnApi33Emulator() {
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(true, testRootDisposable)
    val controller = createEmulator(33)
    val view = createEmulatorView(controller)
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun testActiveAction() {
    simulateDarkTheme(false)
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(true, testRootDisposable)
    val controller = createEmulator(34)
    val view = createEmulatorView(controller)
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    action.actionPerformed(event)
    waitForCondition(10, TimeUnit.SECONDS) { popupFactory.balloonCount > 0 }
    val balloon = popupFactory.getNextBalloon()
    Disposer.register(testRootDisposable, balloon)
    assertThat(balloon.component).isInstanceOf(UiSettingsPanel::class.java)
  }

  @Test
  fun testActiveActionFromKeyEvent() {
    simulateDarkTheme(true)
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(true)
    val action = EmulatorUiSettingsAction()
    val controller = createEmulator(34)
    val view = createEmulatorView(controller)
    val event = createTestKeyEvent(action, controller, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    action.actionPerformed(event)
    waitForCondition(10, TimeUnit.SECONDS) { popupFactory.balloonCount > 0 }
    val balloon = popupFactory.getNextBalloon()
    Disposer.register(testRootDisposable, balloon)
    assertThat(balloon.component).isInstanceOf(UiSettingsPanel::class.java)
  }

  private fun simulateDarkTheme(on: Boolean) {
    val state = if (on) "yes" else "no"
    uiRule.adb.configureShellCommand(uiRule.deviceSelector, "cmd uimode night", "Night mode: $state")
  }

  private fun createTestMouseEvent(action: AnAction, controller: EmulatorController, view: EmulatorView): AnActionEvent {
    val component = createActionButton(action)
    val input = MouseEvent(component, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 10, 10, 1, false)
    val presentation = action.templatePresentation.clone()
    return AnActionEvent(input, createTestDataContext(controller, view), ActionPlaces.TOOLBAR, presentation, ActionManager.getInstance(), 0)
  }

  private fun createTestKeyEvent(action: AnAction, controller: EmulatorController, view: EmulatorView): AnActionEvent {
    val input = KeyEvent(view, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_B, 'b')
    val presentation = action.templatePresentation.clone()
    return AnActionEvent(input, createTestDataContext(controller, view), ActionPlaces.TOOLBAR, presentation, ActionManager.getInstance(), 0)
  }

  private fun createActionButton(action: AnAction) = ActionButton(
    action,
    action.templatePresentation.clone(),
    ActionPlaces.TOOLBAR,
    Dimension(16, 16)
  )

  private fun createEmulator(api: Int): EmulatorController {
    val avdFolder = FakeEmulator.createPhoneAvd(emulatorRule.avdRoot, api = api)
    emulatorRule.newEmulator(avdFolder).apply { start() }
    val emulatorController = RunningEmulatorCatalog.getInstance().apply { updateNow().get() }.emulators.single()
    waitForCondition(5, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    return emulatorController
  }

  private fun createEmulatorView(controller: EmulatorController): EmulatorView =
    EmulatorView(testRootDisposable, controller, displayId = 1, Dimension(600, 800), deviceFrameVisible = false)

  private fun createTestDataContext(controller: EmulatorController, view: EmulatorView): DataContext {
    return DataContext { dataId ->
      when (dataId) {
        CommonDataKeys.PROJECT.name -> uiRule.project
        EMULATOR_CONTROLLER_KEY.name -> controller
        EMULATOR_VIEW_KEY.name -> view
        else -> null
      }
    }
  }
}
