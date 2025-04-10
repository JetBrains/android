/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.streaming.uisettings.ui

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.uisettings.binding.ChangeListener
import com.android.tools.idea.streaming.uisettings.data.DEFAULT_LANGUAGE
import com.android.tools.idea.streaming.uisettings.testutil.DANISH_LANGUAGE
import com.android.tools.idea.streaming.uisettings.testutil.RUSSIAN_LANGUAGE
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.awt.Dimension
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_SPACE
import java.awt.event.KeyEvent.VK_TAB
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JSlider
import kotlin.time.Duration.Companion.seconds

@RunsInEdt
class UiSettingsDialogTest {
  private lateinit var model: UiSettingsModel
  private lateinit var dialog: UiSettingsDialog
  private lateinit var panel: JComponent
  private lateinit var ui: FakeUi
  private var lastCommand: String = ""
  private val nameRule = TestName()
  private val projectRule = ProjectRule()
  private val deviceTypeFromTestName: DeviceType
    get() = when {
      nameRule.methodName.endsWith("Wear") -> DeviceType.WEAR
      nameRule.methodName.endsWith("Tv") -> DeviceType.TV
      nameRule.methodName.endsWith("Automotive") -> DeviceType.AUTOMOTIVE
      nameRule.methodName.endsWith("Desktop") -> DeviceType.DESKTOP
      else -> DeviceType.HANDHELD
    }

  @get:Rule
  val ruleChain = RuleChain(nameRule, projectRule, HeadlessDialogRule(), EdtRule())

  @Before
  fun before() {
    model = createModel()
    dialog = UiSettingsDialog(projectRule.project, model, deviceTypeFromTestName, projectRule.disposable)
    panel = dialog.contentPanel
    ui = FakeUi(panel, createFakeWindow = false, parentDisposable = projectRule.disposable)
  }

  private fun createModel(): UiSettingsModel {
    val model = UiSettingsModel(Dimension(1344, 2992), 480, 34, DeviceType.HANDHELD)
    model.appLanguage.addElement(DEFAULT_LANGUAGE)
    model.appLanguage.addElement(DANISH_LANGUAGE)
    model.appLanguage.addElement(RUSSIAN_LANGUAGE)
    model.appLanguage.selection.setFromController(DEFAULT_LANGUAGE)
    model.fontScaleSettable.setFromController(true)
    model.screenDensitySettable.setFromController(true)
    model.gestureOverlayInstalled.setFromController(true)
    model.talkBackInstalled.setFromController(true)

    model.inDarkMode.uiChangeListener = ChangeListener { lastCommand = "dark=$it" }
    model.gestureNavigation.uiChangeListener = ChangeListener { lastCommand = "gestures=$it" }
    model.appLanguage.selection.uiChangeListener = ChangeListener { lastCommand = "locale=${it?.tag}" }
    model.talkBackOn.uiChangeListener = ChangeListener { lastCommand = "talkBackOn=$it" }
    model.selectToSpeakOn.uiChangeListener = ChangeListener { lastCommand = "selectToSpeakOn=$it" }
    model.fontScaleInPercent.uiChangeListener = ChangeListener { lastCommand = "fontScale=$it" }
    model.screenDensity.uiChangeListener = ChangeListener { lastCommand = "density=$it" }
    model.debugLayout.uiChangeListener = ChangeListener { lastCommand = "debugLayout=$it" }
    model.resetAction = { lastCommand = "reset" }
    return model
  }

  @Test
  fun testKeyboardAccessibility() {
    val focusManager = FakeKeyboardFocusManager(projectRule.disposable)
    focusManager.focusOwner = panel
    panel.transferFocus()

    assertThat(focusManager.focusOwner?.name).isEqualTo(DARK_THEME_TITLE)
    ui.keyboard.pressAndRelease(VK_SPACE)
    waitForCondition(1.seconds) { lastCommand == "dark=true" }
    ui.keyboard.pressAndRelease(VK_TAB)
    model.differentFromDefault.setFromController(true)

    if (StudioFlags.EMBEDDED_EMULATOR_GESTURE_NAVIGATION_IN_UI_SETTINGS.get()) {
      assertThat(focusManager.focusOwner?.name).isEqualTo(GESTURE_NAVIGATION_TITLE)
      val navigationComboBox = panel.getDescendant<JComboBox<*>> { it.name == GESTURE_NAVIGATION_TITLE }
      // simulate: ui.keyboard.pressAndRelease(VK_DOWN), popup from comboBox cannot be intercepted
      navigationComboBox.selectedItem = true
      waitForCondition(1.seconds) { lastCommand == "gestures=true" }
      ui.keyboard.pressAndRelease(VK_TAB)
    }

    assertThat(focusManager.focusOwner?.name).isEqualTo(APP_LANGUAGE_TITLE)
    val comboBox = panel.getDescendant<JComboBox<*>> { it.name == APP_LANGUAGE_TITLE }
    // simulate: ui.keyboard.pressAndRelease(VK_DOWN), popup from comboBox cannot be intercepted
    comboBox.selectedIndex = 1
    waitForCondition(1.seconds) { lastCommand == "locale=da" }
    // simulate: ui.keyboard.pressAndRelease(VK_DOWN), popup from comboBox cannot be intercepted
    comboBox.selectedIndex = 2
    waitForCondition(1.seconds) { lastCommand == "locale=ru" }
    ui.keyboard.pressAndRelease(VK_TAB)

    assertThat(focusManager.focusOwner?.name).isEqualTo(TALKBACK_TITLE)
    ui.keyboard.pressAndRelease(VK_SPACE)
    waitForCondition(1.seconds) { lastCommand == "talkBackOn=true" }
    ui.keyboard.pressAndRelease(VK_TAB)

    assertThat(focusManager.focusOwner?.name).isEqualTo(SELECT_TO_SPEAK_TITLE)
    ui.keyboard.pressAndRelease(VK_SPACE)
    waitForCondition(1.seconds) { lastCommand == "selectToSpeakOn=true" }
    ui.keyboard.pressAndRelease(VK_TAB)

    assertThat(focusManager.focusOwner?.name).isEqualTo(FONT_SCALE_TITLE)
    ui.keyboard.pressAndRelease(VK_RIGHT)
    waitForCondition(1.seconds) { lastCommand == "fontScale=115" }
    ui.keyboard.pressAndRelease(VK_RIGHT)
    waitForCondition(1.seconds) { lastCommand == "fontScale=130" }
    ui.keyboard.pressAndRelease(VK_TAB)

    assertThat(focusManager.focusOwner?.name).isEqualTo(DENSITY_TITLE)
    ui.keyboard.pressAndRelease(VK_RIGHT)
    waitForCondition(1.seconds) { lastCommand == "density=544" }
    ui.keyboard.pressAndRelease(VK_RIGHT)
    waitForCondition(1.seconds) { lastCommand == "density=608" }
    ui.keyboard.pressAndRelease(VK_TAB)

    if (StudioFlags.EMBEDDED_EMULATOR_DEBUG_LAYOUT_IN_UI_SETTINGS.get()) {
      ui.keyboard.pressAndRelease(VK_SPACE)
      waitForCondition(1.seconds) { lastCommand == "debugLayout=true" }
      ui.keyboard.pressAndRelease(VK_TAB)
    }

    assertThat(focusManager.focusOwner?.name).isEqualTo(RESET_TITLE)
    ui.keyboard.pressAndRelease(VK_SPACE)
    waitForCondition(1.seconds) { lastCommand == "reset" }
    model.differentFromDefault.setFromController(false)

    // Activating the reset button should disable the reset button, and the focus will transfer to the next control:
    assertThat(focusManager.focusOwner?.name).isEqualTo(DARK_THEME_TITLE)

    // Back tab will now skip the reset button:
    ui.keyboard.press(VK_SHIFT)
    ui.keyboard.pressAndRelease(VK_TAB)
    ui.keyboard.release(VK_SHIFT)

    // Back tab to skip the debug layout control:
    if (StudioFlags.EMBEDDED_EMULATOR_DEBUG_LAYOUT_IN_UI_SETTINGS.get()) {
      ui.keyboard.press(VK_SHIFT)
      ui.keyboard.pressAndRelease(VK_TAB)
      ui.keyboard.release(VK_SHIFT)
    }
    assertThat(panel.getDescendant<JSlider> { it.name == DENSITY_TITLE }.hasFocus()).isTrue()
  }

  @Test
  fun testFirstFocusedComponentWithActiveResetLink() {
    model.screenDensity.setFromController(560)
    val focusManager = FakeKeyboardFocusManager(projectRule.disposable)
    focusManager.focusOwner = panel
    panel.transferFocus()

    // The Reset link should not be selected as the first focused component:
    assertThat(focusManager.focusOwner?.name).isEqualTo(DARK_THEME_TITLE)
  }

  @Test
  fun testFirstFocusedComponentWithActiveResetLinkForWear() {
    model.screenDensity.setFromController(560)
    val focusManager = FakeKeyboardFocusManager(projectRule.disposable)
    focusManager.focusOwner = panel
    panel.transferFocus()

    // The Reset link should not be selected as the first focused component, and Wear does not have Dark Mode:
    assertThat(focusManager.focusOwner?.name).isEqualTo(APP_LANGUAGE_TITLE)
  }
}
