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
package com.android.tools.idea.streaming.uisettings.ui

import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.streaming.uisettings.binding.ChangeListener
import com.android.tools.idea.streaming.uisettings.data.DEFAULT_LANGUAGE
import com.android.tools.idea.streaming.uisettings.testutil.DANISH_LANGUAGE
import com.android.tools.idea.streaming.uisettings.testutil.RUSSIAN_LANGUAGE
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.awt.Dimension
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_SPACE
import java.awt.event.KeyEvent.VK_TAB
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JSlider
import kotlin.time.Duration.Companion.seconds

class UiSettingsPanelTest {
  private val popupRule = JBPopupRule()
  private val projectRule = ProjectRule()
  private val nameRule = TestName()

  @get:Rule
  val ruleChain = RuleChain(projectRule, popupRule, nameRule)

  private lateinit var model: UiSettingsModel
  private lateinit var panel: UiSettingsPanel
  private var lastCommand: String = ""

  @Before
  fun before() {
    model = UiSettingsModel(Dimension(1344, 2992), 480, 34)
    model.appLanguage.addElement(DEFAULT_LANGUAGE)
    model.appLanguage.addElement(DANISH_LANGUAGE)
    model.appLanguage.addElement(RUSSIAN_LANGUAGE)
    model.appLanguage.selection.setFromController(DEFAULT_LANGUAGE)

    panel = UiSettingsPanel(
      model,
      limitedSupport = nameRule.methodName == "testLimitedControls"
    )
    model.inDarkMode.uiChangeListener = ChangeListener { lastCommand = "dark=$it" }
    model.gestureNavigation.uiChangeListener = ChangeListener { lastCommand = "gestures=$it" }
    model.appLanguage.selection.uiChangeListener = ChangeListener { lastCommand = "locale=${it?.tag}" }
    model.talkBackOn.uiChangeListener = ChangeListener { lastCommand = "talkBackOn=$it" }
    model.selectToSpeakOn.uiChangeListener = ChangeListener { lastCommand = "selectToSpeakOn=$it" }
    model.fontScaleInPercent.uiChangeListener = ChangeListener { lastCommand = "fontScale=$it" }
    model.screenDensity.uiChangeListener = ChangeListener { lastCommand = "density=$it" }
    model.resetAction = { lastCommand = "reset" }
  }

  @Test
  fun testSetDarkModeFromUi() {
    val checkBox = panel.getDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }
    assertThat(checkBox.isSelected).isFalse()

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "dark=true" }

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "dark=false" }
  }

  @Test
  fun testGestureOverlayNotInstalled() {
    model.gestureOverlayInstalled.setFromController(false)
    val checkBox = panel.getDescendant<JCheckBox> { it.name == GESTURE_NAVIGATION_TITLE }
    assertThat(checkBox.isVisible).isFalse()
  }

  @Test
  fun testSetGestureNavigationFromUi() {
    model.gestureOverlayInstalled.setFromController(true)
    val checkBox = panel.getDescendant<JCheckBox> { it.name == GESTURE_NAVIGATION_TITLE }
    assertThat(checkBox.isVisible).isTrue()
    assertThat(checkBox.isSelected).isFalse()

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "gestures=true" }

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "gestures=false" }
  }

  @Test
  fun testChangeLanguageFromUi() {
    val comboBox = panel.getDescendant<JComboBox<*>> { it.name == APP_LANGUAGE_TITLE }
    assertThat(comboBox.isVisible).isTrue()
    assertThat(comboBox.selectedIndex).isEqualTo(0)

    comboBox.selectedIndex = 1
    waitForCondition(1.seconds) { lastCommand == "locale=da" }

    comboBox.selectedIndex = 2
    waitForCondition(1.seconds) { lastCommand == "locale=ru" }

    comboBox.selectedIndex = 0
    waitForCondition(1.seconds) { lastCommand == "locale=" }
  }

  @Test
  fun testSetTalkBackFromUi() {
    val checkBox = panel.getDescendant<JCheckBox> { it.name == TALKBACK_TITLE }
    assertThat(checkBox.isVisible).isFalse()
    model.talkBackInstalled.setFromController(true)
    assertThat(checkBox.isVisible).isTrue()

    assertThat(checkBox.isSelected).isFalse()

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "talkBackOn=true" }

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "talkBackOn=false" }
  }

  @Test
  fun testSetSelectToSpeakFromUi() {
    val checkBox = panel.getDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }
    assertThat(checkBox.isVisible).isFalse()
    model.talkBackInstalled.setFromController(true)
    assertThat(checkBox.isVisible).isTrue()

    assertThat(checkBox.isSelected).isFalse()

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "selectToSpeakOn=true" }

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "selectToSpeakOn=false" }
  }

  @Test
  fun testSetFontScaleFromUi() {
    val slider = panel.getDescendant<JSlider> { it.name == FONT_SCALE_TITLE }
    assertThat(slider.value).isEqualTo(FontScale.NORMAL.ordinal)

    slider.value = FontScale.values().size - 1
    waitForCondition(1.seconds) { lastCommand == "fontScale=200" }

    slider.value = 0
    waitForCondition(1.seconds) { lastCommand == "fontScale=85" }
  }

  @Test
  fun testSetDensityFromUi() {
    val slider = panel.getDescendant<JSlider> { it.name == DENSITY_TITLE }
    assertThat(slider.value).isEqualTo(1)

    val densities = GoogleDensityRange.computeDensityRange(Dimension(1344, 2992), 480)
    slider.value = densities.lastIndex
    waitForCondition(1.seconds) { lastCommand == "density=672" }

    slider.value = 0
    waitForCondition(1.seconds) { lastCommand == "density=408" }
  }

  @Test
  fun testResetButton() {
    val button = panel.getDescendant<JButton> { it.name == RESET_BUTTON_TEXT }
    model.differentFromDefault.setFromController(false)
    assertThat(button.model.isEnabled).isFalse()
    model.differentFromDefault.setFromController(true)
    assertThat(button.model.isEnabled).isTrue()
    button.doClick()
    waitForCondition(1.seconds) { lastCommand == "reset" }
  }

  @Test
  fun testLimitedControls() {
    assertThat(panel.findDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JComboBox<*>> { it.name == APP_LANGUAGE_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == TALKBACK_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JSlider> { it.name == FONT_SCALE_TITLE }).isNotNull()

    assertThat(panel.findDescendant<JCheckBox> { it.name == GESTURE_NAVIGATION_TITLE }).isNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }).isNull()
    assertThat(panel.findDescendant<JSlider> { it.name == DENSITY_TITLE }).isNull()
  }

  @Test
  fun testKeyboardAccessibility() {
    model.gestureOverlayInstalled.setFromController(true)
    model.talkBackInstalled.setFromController(true)
    val ui = FakeUi(panel, createFakeWindow = true, parentDisposable = projectRule.disposable)
    val focusManager = FakeKeyboardFocusManager(projectRule.disposable)
    focusManager.focusOwner = panel
    panel.transferFocus()

    assertThat(panel.getDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }.hasFocus()).isTrue()
    ui.keyboard.pressAndRelease(VK_SPACE)
    waitForCondition(1.seconds) { lastCommand == "dark=true" }
    ui.keyboard.pressAndRelease(VK_TAB)
    model.differentFromDefault.setFromController(true)

    assertThat(panel.getDescendant<JCheckBox> { it.name == GESTURE_NAVIGATION_TITLE }.hasFocus()).isTrue()
    ui.keyboard.pressAndRelease(VK_SPACE)
    waitForCondition(1.seconds) { lastCommand == "gestures=true" }
    ui.keyboard.pressAndRelease(VK_TAB)

    val comboBox = panel.getDescendant<JComboBox<*>> { it.name == APP_LANGUAGE_TITLE }
    assertThat(comboBox.hasFocus()).isTrue()
    // simulate: ui.keyboard.pressAndRelease(VK_DOWN), popup from comboBox cannot be intercepted
    comboBox.selectedIndex = 1
    waitForCondition(1.seconds) { lastCommand == "locale=da" }
    // simulate: ui.keyboard.pressAndRelease(VK_DOWN), popup from comboBox cannot be intercepted
    comboBox.selectedIndex = 2
    waitForCondition(1.seconds) { lastCommand == "locale=ru" }
    ui.keyboard.pressAndRelease(VK_TAB)

    assertThat(panel.getDescendant<JCheckBox> { it.name == TALKBACK_TITLE }.hasFocus()).isTrue()
    ui.keyboard.pressAndRelease(VK_SPACE)
    waitForCondition(1.seconds) { lastCommand == "talkBackOn=true" }
    ui.keyboard.pressAndRelease(VK_TAB)

    assertThat(panel.getDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }.hasFocus()).isTrue()
    ui.keyboard.pressAndRelease(VK_SPACE)
    waitForCondition(1.seconds) { lastCommand == "selectToSpeakOn=true" }
    ui.keyboard.pressAndRelease(VK_TAB)

    assertThat(panel.getDescendant<JSlider> { it.name == FONT_SCALE_TITLE }.hasFocus()).isTrue()
    ui.keyboard.pressAndRelease(VK_RIGHT)
    waitForCondition(1.seconds) { lastCommand == "fontScale=115" }
    ui.keyboard.pressAndRelease(VK_RIGHT)
    waitForCondition(1.seconds) { lastCommand == "fontScale=130" }
    ui.keyboard.pressAndRelease(VK_TAB)

    assertThat(panel.getDescendant<JSlider> { it.name == DENSITY_TITLE }.hasFocus()).isTrue()
    ui.keyboard.pressAndRelease(VK_RIGHT)
    waitForCondition(1.seconds) { lastCommand == "density=544" }
    ui.keyboard.pressAndRelease(VK_RIGHT)
    waitForCondition(1.seconds) { lastCommand == "density=608" }
    ui.keyboard.pressAndRelease(VK_TAB)

    assertThat(panel.getDescendant<JButton> { it.name == RESET_BUTTON_TEXT }.hasFocus()).isTrue()
    ui.keyboard.pressAndRelease(VK_SPACE)
    waitForCondition(1.seconds) { lastCommand == "reset" }
    model.differentFromDefault.setFromController(false)

    // Activating the reset button should disable the reset button, and the focus will transfer to the next control:
    assertThat(panel.getDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }.hasFocus()).isTrue()

    // Back tab will now skip the reset button:
    ui.keyboard.press(VK_SHIFT)
    ui.keyboard.pressAndRelease(VK_TAB)
    ui.keyboard.release(VK_SHIFT)
    assertThat(panel.getDescendant<JSlider> { it.name == DENSITY_TITLE }.hasFocus()).isTrue()
  }
}
