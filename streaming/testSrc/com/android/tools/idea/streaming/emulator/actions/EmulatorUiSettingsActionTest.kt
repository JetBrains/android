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
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.adtui.swing.findModelessDialog
import com.android.tools.idea.streaming.emulator.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.streaming.emulator.EMULATOR_VIEW_KEY
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.UiSettingsRule
import com.android.tools.idea.streaming.uisettings.ui.APP_LANGUAGE_TITLE
import com.android.tools.idea.streaming.uisettings.ui.DARK_THEME_TITLE
import com.android.tools.idea.streaming.uisettings.ui.DENSITY_TITLE
import com.android.tools.idea.streaming.uisettings.ui.FONT_SCALE_TITLE
import com.android.tools.idea.streaming.uisettings.ui.GESTURE_NAVIGATION_TITLE
import com.android.tools.idea.streaming.uisettings.ui.RESET_TITLE
import com.android.tools.idea.streaming.uisettings.ui.SELECT_TO_SPEAK_TITLE
import com.android.tools.idea.streaming.uisettings.ui.TALKBACK_TITLE
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsDialog
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsPanel
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.ActionLink
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.awt.Dimension
import java.awt.event.MouseEvent
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JSlider
import kotlin.time.Duration.Companion.seconds

@RunsInEdt
class EmulatorUiSettingsActionTest {
  private val uiRule = UiSettingsRule()

  @get:Rule
  val ruleChain: RuleChain = RuleChain(uiRule, EdtRule(), HeadlessDialogRule(createDialogWindow = true))

  private val testRootDisposable
    get() = uiRule.testRootDisposable

  @After
  fun after() {
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
  }

  @Test
  fun testActionOnApi32Emulator() {
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
    val controller = uiRule.getControllerOf(uiRule.emulator)
    val view = createEmulatorView(controller).apply { size = Dimension(600, 800) }
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    action.actionPerformed(event)
    val dialog = waitForDialog()
    assertThat(dialog.contentPanel.findDescendant<UiSettingsPanel>()).isNotNull()
  }

  @Test
  fun testHasResetLink() {
    val controller = uiRule.getControllerOf(uiRule.emulator)
    val view = createEmulatorView(controller)
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)
    action.actionPerformed(event)
    val dialog = waitForDialog()
    assertThat(dialog.contentPanel.findDescendant<ActionLink> { it.name == RESET_TITLE }).isNotNull()
  }

  @Test
  fun testWearControls() {
    val controller = uiRule.getControllerOf(uiRule.createAndStartWatchEmulator())
    val view = createEmulatorView(controller)
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)
    uiRule.configureUiSettings(deviceSelector = DeviceSelector.fromSerialNumber(controller.emulatorId.serialNumber))
    action.actionPerformed(event)
    val dialog = waitForDialog()
    val panel = dialog.contentPanel
    assertThat(panel.findDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }).isNull()
    assertThat(panel.findDescendant<JComboBox<*>> { it.name == APP_LANGUAGE_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == TALKBACK_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JSlider> { it.name == FONT_SCALE_TITLE }).isNotNull()

    assertThat(panel.findDescendant<JCheckBox> { it.name == GESTURE_NAVIGATION_TITLE }).isNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }).isNull()
    assertThat(panel.findDescendant<JSlider> { it.name == DENSITY_TITLE }).isNull()
  }

  @Test
  fun testDialogClosesWhenDialogLosesFocus() {
    simulateDarkTheme(false)
    val controller = uiRule.getControllerOf(uiRule.emulator)
    val view = createEmulatorView(controller)
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    action.actionPerformed(event)
    val dialog = waitForDialog()
    dialog.window.windowFocusListeners.forEach { it.windowLostFocus(mock()) }
    assertThat(dialog.isDisposed).isTrue()
  }

  @Test
  fun testDialogClosesWithParentDisposable() {
    val parentDisposable = Disposer.newDisposable()
    Disposer.register(testRootDisposable, parentDisposable)

    simulateDarkTheme(false)
    val controller = uiRule.getControllerOf(uiRule.emulator)
    val view = createEmulatorView(controller, parentDisposable)
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)
    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    action.actionPerformed(event)
    val dialog = waitForDialog()

    Disposer.dispose(parentDisposable)
    assertThat(dialog.isDisposed).isTrue()
  }

  @Test
  fun testDialogIsMovable() {
    simulateDarkTheme(false)
    val controller = uiRule.getControllerOf(uiRule.emulator)
    val view = createEmulatorView(controller).apply { size = Dimension(600, 800) }
    val action = EmulatorUiSettingsAction()
    val event = createTestMouseEvent(action, controller, view)

    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    action.actionPerformed(event)
    val dialog = waitForDialog()
    val uiSettingsPanel = dialog.contentPanel.findDescendant<UiSettingsPanel>()!!

    // Move the dialog (90, 90):
    val ui = FakeUi(uiSettingsPanel)
    ui.mouse.press(10, 10)
    ui.mouse.dragTo(100, 100)
    ui.mouse.release()

    // The mock Window will not be able to move, it will always have a screen location of (0, 0).
    // Verify that an attempt to move it to (90, 90) was made:
    verify(dialog.window, atLeast(1)).reshape(eq(90), eq(90), any(), any())
  }

  private fun waitForDialog(): DialogWrapper {
    waitForCondition(2.seconds) { findDialog() != null }
    return findDialog()!!
  }

  private fun findDialog() = findModelessDialog { it is UiSettingsDialog && it.isShowing }

  private fun simulateDarkTheme(on: Boolean) {
    val state = if (on) "yes" else "no"
    uiRule.adb.configureShellCommand(uiRule.emulatorDeviceSelector, "cmd uimode night", "Night mode: $state")
  }

  private fun createTestMouseEvent(action: AnAction, controller: EmulatorController, view: EmulatorView): AnActionEvent {
    val component = createActionButton(action)
    val input = MouseEvent(component, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 10, 10, 1, false)
    val presentation = action.templatePresentation.clone()
    return AnActionEvent.createEvent(createTestDataContext(controller, view), presentation, ActionPlaces.TOOLBAR, ActionUiKind.NONE, input)
  }

  private fun createActionButton(action: AnAction) = ActionButton(
    action,
    action.templatePresentation.clone(),
    ActionPlaces.TOOLBAR,
    Dimension(16, 16)
  ).apply { size = Dimension(16, 16) }

  private fun createEmulatorView(controller: EmulatorController, parentDisposable: Disposable = testRootDisposable): EmulatorView =
    EmulatorView(parentDisposable, controller, uiRule.project, displayId = 0, Dimension(600, 800), deviceFrameVisible = false)

  private fun createTestDataContext(controller: EmulatorController, view: EmulatorView): DataContext {
    return SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, uiRule.project)
      .add(EMULATOR_CONTROLLER_KEY, controller)
      .add(EMULATOR_VIEW_KEY, view)
      .build()
  }
}
