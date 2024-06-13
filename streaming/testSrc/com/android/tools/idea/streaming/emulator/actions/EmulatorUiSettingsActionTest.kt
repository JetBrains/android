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

import com.android.adblib.DeviceSelector
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.adtui.swing.popup.FakeJBPopup
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.emulator.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.streaming.emulator.EMULATOR_VIEW_KEY
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.UiSettingsRule
import com.android.tools.idea.streaming.uisettings.ui.DARK_THEME_TITLE
import com.android.tools.idea.streaming.uisettings.ui.DENSITY_TITLE
import com.android.tools.idea.streaming.uisettings.ui.RESET_BUTTON_TEXT
import com.android.tools.idea.streaming.uisettings.ui.SELECT_TO_SPEAK_TITLE
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsPanel
import com.android.tools.idea.testing.flags.override
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.awt.RelativePoint
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doAnswer
import java.awt.Dimension
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.WindowFocusListener
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JSlider
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds

class EmulatorUiSettingsActionTest {
  private val popupRule = JBPopupRule()
  private val uiRule = UiSettingsRule()

  @get:Rule
  val ruleChain: RuleChain = RuleChain(uiRule, popupRule)

  private val popupFactory
    get() = popupRule.fakePopupFactory

  private val testRootDisposable
    get() = uiRule.testRootDisposable

  @After
  fun after() {
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
  }

  @Test
  fun testUpdateWhenUnused() {
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(false, testRootDisposable)
    val controller = uiRule.getControllerOf(uiRule.emulator)
    val view = createEmulatorView(controller)
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun testActionOnApi32Emulator() {
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(true, testRootDisposable)
    val controller = uiRule.getControllerOf(uiRule.createAndStartEmulator(api = 32))
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
    val controller = uiRule.getControllerOf(uiRule.emulator)
    val view = createEmulatorView(controller)
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    action.actionPerformed(event)
    waitForCondition(10.seconds) { popupFactory.balloonCount > 0 }
    val balloon = popupFactory.getNextBalloon()
    waitForCondition(10.seconds) { balloon.isShowing }
    assertThat(balloon.component).isInstanceOf(UiSettingsPanel::class.java)
    assertThat((balloon.target as RelativePoint).originalComponent).isInstanceOf(ActionButton::class.java)
    assertThat((balloon.target as RelativePoint).originalPoint).isEqualTo(Point(8, 8))
  }

  @Test
  fun testHasResetButton() {
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(true, testRootDisposable)
    val controller = uiRule.getControllerOf(uiRule.emulator)
    val view = createEmulatorView(controller)
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)
    action.actionPerformed(event)
    waitForCondition(10.seconds) { popupFactory.balloonCount > 0 }
    val balloon = popupFactory.getNextBalloon()
    waitForCondition(10.seconds) { balloon.isShowing }
    assertThat(balloon.component.findDescendant<JButton> { it.name == RESET_BUTTON_TEXT }).isNotNull()
  }

  @Test
  fun testWearControls() {
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(true, testRootDisposable)
    val controller = uiRule.getControllerOf(uiRule.createAndStartWatchEmulator())
    val view = createEmulatorView(controller)
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)
    uiRule.configureUiSettings(deviceSelector = DeviceSelector.fromSerialNumber(controller.emulatorId.serialNumber))
    action.actionPerformed(event)
    waitForCondition(10.seconds) { popupFactory.balloonCount > 0 }
    val balloon = popupFactory.getNextBalloon()
    waitForCondition(10.seconds) { balloon.isShowing }
    val panel = balloon.component
    assertThat(panel.findDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }).isNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }).isNull()
    assertThat(panel.findDescendant<JSlider> { it.name == DENSITY_TITLE }).isNull()
  }

  @Test
  fun testActiveActionFromActionButtonInPopup() {
    simulateDarkTheme(false)
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(true, testRootDisposable)
    val controller = uiRule.getControllerOf(uiRule.emulator)
    val view = createEmulatorView(controller).apply { size = Dimension(600, 800) }
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)
    (event.inputEvent?.component as? JComponent)?.putClientProperty(JBPopup.KEY, FakeJBPopup<String>(listOf()))

    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    action.actionPerformed(event)
    waitForCondition(10.seconds) { popupFactory.balloonCount > 0 }
    val balloon = popupFactory.getNextBalloon()
    waitForCondition(10.seconds) { balloon.isShowing }
    assertThat(balloon.component).isInstanceOf(UiSettingsPanel::class.java)
    assertThat((balloon.target as RelativePoint).originalComponent).isSameAs(view)
    assertThat((balloon.target as RelativePoint).originalPoint).isEqualTo(Point())
  }

  @Test
  fun testActiveActionFromKeyEvent() {
    simulateDarkTheme(true)
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(true)
    val action = EmulatorUiSettingsAction()
    val controller = uiRule.getControllerOf(uiRule.emulator)
    val view = createEmulatorView(controller)
    val event = createTestKeyEvent(action, controller, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    action.actionPerformed(event)
    waitForCondition(10.seconds) { popupFactory.balloonCount > 0 }
    val balloon = popupFactory.getNextBalloon()
    waitForCondition(10.seconds) { balloon.isShowing }
    assertThat(balloon.component).isInstanceOf(UiSettingsPanel::class.java)
    assertThat((balloon.target as RelativePoint).originalComponent).isSameAs(view)
    assertThat((balloon.target as RelativePoint).originalPoint).isEqualTo(Point())
  }

  @Test
  fun testPickerClosesWhenWindowCloses() {
    simulateDarkTheme(false)
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(true, testRootDisposable)
    val controller = uiRule.getControllerOf(uiRule.emulator)
    val view = createEmulatorView(controller)
    val action = EmulatorUiSettingsAction()
    val event = createTestKeyEvent(action, controller, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    runInEdtAndWait { FakeUi(view, createFakeWindow = true, parentDisposable = testRootDisposable) }
    val window = SwingUtilities.windowForComponent(view)
    val listeners = mutableListOf<WindowFocusListener>()
    doAnswer { invocation ->
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

    simulateDarkTheme(false)
    StudioFlags.EMBEDDED_EMULATOR_SETTINGS_PICKER.override(true, testRootDisposable)
    val controller = uiRule.getControllerOf(uiRule.emulator)
    val view = createEmulatorView(controller, parentDisposable)
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    action.actionPerformed(event)
    waitForCondition(10.seconds) { popupFactory.balloonCount > 0 }
    val balloon = popupFactory.getNextBalloon()
    waitForCondition(10.seconds) { balloon.isShowing }

    Disposer.dispose(parentDisposable)
    assertThat(balloon.isDisposed).isTrue()
  }

  private fun simulateDarkTheme(on: Boolean) {
    val state = if (on) "yes" else "no"
    uiRule.adb.configureShellCommand(uiRule.emulatorDeviceSelector, "cmd uimode night", "Night mode: $state")
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
  ).apply { size = Dimension(16, 16) }

  private fun createEmulatorView(controller: EmulatorController, parentDisposable: Disposable = testRootDisposable): EmulatorView =
    EmulatorView(parentDisposable, controller, displayId = 0, Dimension(600, 800), deviceFrameVisible = false)

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
