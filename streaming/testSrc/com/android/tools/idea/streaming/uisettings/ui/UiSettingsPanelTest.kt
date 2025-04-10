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

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.uisettings.binding.ChangeListener
import com.android.tools.idea.streaming.uisettings.data.DEFAULT_LANGUAGE
import com.android.tools.idea.streaming.uisettings.testutil.DANISH_LANGUAGE
import com.android.tools.idea.streaming.uisettings.testutil.RUSSIAN_LANGUAGE
import com.android.tools.idea.testing.flags.overrideForTest
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.awt.Dimension
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JSlider
import kotlin.time.Duration.Companion.seconds

class UiSettingsPanelTest {
  private val nameRule = TestName()
  private val disposableRule = DisposableRule()

  @get:Rule
  val ruleChain = RuleChain(nameRule, disposableRule)

  private lateinit var model: UiSettingsModel
  private lateinit var panel: UiSettingsPanel
  private lateinit var ui: FakeUi
  private var lastCommand: String = ""
  private val deviceTypeFromTestName: DeviceType
    get() = when {
      nameRule.methodName.endsWith("Wear") -> DeviceType.WEAR
      nameRule.methodName.endsWith("Tv") -> DeviceType.TV
      nameRule.methodName.endsWith("Automotive") -> DeviceType.AUTOMOTIVE
      nameRule.methodName.endsWith("Desktop") -> DeviceType.DESKTOP
      else -> DeviceType.HANDHELD
    }

  @Before
  fun before() {
    model = createModel()
    panel = UiSettingsPanel(model, deviceTypeFromTestName)
    ui = FakeUi(panel, createFakeWindow = true, parentDisposable = disposableRule.disposable)
  }

  private fun createModel(): UiSettingsModel {
    val deviceType = deviceTypeFromTestName
    val model = UiSettingsModel(Dimension(1344, 2992), 480, 34, deviceType)
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
  fun testSetDarkModeFromUi() {
    val checkBox = panel.getDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }
    assertThat(checkBox.accessibleContext.accessibleName).isEqualTo(DARK_THEME_TITLE)
    assertThat(checkBox.isShowing).isTrue()
    assertThat(checkBox.isSelected).isFalse()

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "dark=true" }

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "dark=false" }
  }

  @Test
  fun testGestureOverlayNotInstalled() {
    StudioFlags.EMBEDDED_EMULATOR_GESTURE_NAVIGATION_IN_UI_SETTINGS.overrideForTest(true, disposableRule.disposable)
    model.gestureOverlayInstalled.setFromController(false)
    val comboBox = panel.getDescendant<JComboBox<*>> { it.name == GESTURE_NAVIGATION_TITLE }
    assertThat(comboBox.accessibleContext.accessibleName).isEqualTo(GESTURE_NAVIGATION_TITLE)
    assertThat(comboBox.isShowing).isFalse()
  }

  @Test
  fun testSetGestureNavigationFromUi() {
    StudioFlags.EMBEDDED_EMULATOR_GESTURE_NAVIGATION_IN_UI_SETTINGS.overrideForTest(true, disposableRule.disposable)
    model.gestureOverlayInstalled.setFromController(true)
    val comboBox = panel.getDescendant<JComboBox<*>> { it.name == GESTURE_NAVIGATION_TITLE }
    assertThat(comboBox.isShowing).isTrue()
    assertThat(comboBox.selectedItem).isEqualTo(false)

    comboBox.selectedItem = true
    waitForCondition(1.seconds) { lastCommand == "gestures=true" }

    comboBox.selectedItem = false
    waitForCondition(1.seconds) { lastCommand == "gestures=false" }
  }

  @Test
  fun testChangeLanguageFromUi() {
    val comboBox = panel.getDescendant<JComboBox<*>> { it.name == APP_LANGUAGE_TITLE }
    assertThat(comboBox.accessibleContext.accessibleName).isEqualTo(APP_LANGUAGE_TITLE)
    assertThat(comboBox.isShowing).isTrue()
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
    assertThat(checkBox.accessibleContext.accessibleName).isEqualTo(TALKBACK_TITLE)
    model.talkBackInstalled.setFromController(false)
    assertThat(checkBox.isShowing).isFalse()
    model.talkBackInstalled.setFromController(true)
    assertThat(checkBox.isShowing).isTrue()

    assertThat(checkBox.isSelected).isFalse()

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "talkBackOn=true" }

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "talkBackOn=false" }
  }

  @Test
  fun testSetSelectToSpeakFromUi() {
    val checkBox = panel.getDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }
    assertThat(checkBox.accessibleContext.accessibleName).isEqualTo(SELECT_TO_SPEAK_TITLE)
    model.talkBackInstalled.setFromController(false)
    assertThat(checkBox.isShowing).isFalse()
    model.talkBackInstalled.setFromController(true)
    assertThat(checkBox.isShowing).isTrue()

    assertThat(checkBox.isSelected).isFalse()

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "selectToSpeakOn=true" }

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "selectToSpeakOn=false" }
  }

  @Test
  fun testSetFontScaleFromUi() {
    val slider = panel.getDescendant<JSlider> { it.name == FONT_SCALE_TITLE }
    assertThat(slider.isShowing).isTrue()
    assertThat(slider.accessibleContext.accessibleName).isEqualTo(FONT_SCALE_TITLE)
    assertThat(slider.value).isEqualTo(FontScale.NORMAL.ordinal)

    slider.value = FontScale.entries.size - 1
    waitForCondition(1.seconds) { lastCommand == "fontScale=200" }

    slider.value = 0
    waitForCondition(1.seconds) { lastCommand == "fontScale=85" }
  }

  @Test
  fun testSetDensityFromUi() {
    val slider = panel.getDescendant<JSlider> { it.name == DENSITY_TITLE }
    assertThat(slider.isShowing).isTrue()
    assertThat(slider.accessibleContext.accessibleName).isEqualTo(DENSITY_TITLE)
    assertThat(slider.value).isEqualTo(1)

    val densities = GoogleDensityRange.computeDensityRange(Dimension(1344, 2992), 480)
    slider.value = densities.lastIndex
    waitForCondition(1.seconds) { lastCommand == "density=672" }

    slider.value = 0
    waitForCondition(1.seconds) { lastCommand == "density=408" }
  }

  @Test
  fun testControlsForWear() {
    assertThat(panel.findDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }).isNull()
    assertThat(panel.findDescendant<JComboBox<*>> { it.name == APP_LANGUAGE_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == TALKBACK_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JSlider> { it.name == FONT_SCALE_TITLE }).isNotNull()

    assertThat(panel.findDescendant<JComboBox<*>> { it.name == GESTURE_NAVIGATION_TITLE }).isNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }).isNull()
    assertThat(panel.findDescendant<JSlider> { it.name == DENSITY_TITLE }).isNull()
  }

  @Test
  fun testControlsForAutomotive() {
    assertThat(panel.findDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JComboBox<*>> { it.name == APP_LANGUAGE_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == TALKBACK_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JSlider> { it.name == FONT_SCALE_TITLE }).isNotNull()

    assertThat(panel.findDescendant<JComboBox<*>> { it.name == GESTURE_NAVIGATION_TITLE }).isNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }).isNull()
    assertThat(panel.findDescendant<JSlider> { it.name == DENSITY_TITLE }).isNull()
  }

  @Test
  fun testControlsForDesktop() {
    assertThat(panel.findDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JComboBox<*>> { it.name == APP_LANGUAGE_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == TALKBACK_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JSlider> { it.name == FONT_SCALE_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JSlider> { it.name == DENSITY_TITLE }).isNotNull()

    assertThat(panel.findDescendant<JComboBox<*>> { it.name == GESTURE_NAVIGATION_TITLE }).isNull()
  }

  @Test
  fun testControlsForTv() {
    assertThat(panel.findDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JComboBox<*>> { it.name == APP_LANGUAGE_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == TALKBACK_TITLE }).isNotNull()
    assertThat(panel.findDescendant<JSlider> { it.name == FONT_SCALE_TITLE }).isNotNull()

    assertThat(panel.findDescendant<JComboBox<*>> { it.name == GESTURE_NAVIGATION_TITLE }).isNull()
    assertThat(panel.findDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }).isNull()
    assertThat(panel.findDescendant<JSlider> { it.name == DENSITY_TITLE }).isNull()
  }

  @Test
  fun testControlsForOemWithPermissionMonitoring() {
    StudioFlags.EMBEDDED_EMULATOR_GESTURE_NAVIGATION_IN_UI_SETTINGS.overrideForTest(true, disposableRule.disposable)
    StudioFlags.EMBEDDED_EMULATOR_DEBUG_LAYOUT_IN_UI_SETTINGS.overrideForTest(true, disposableRule.disposable)
    model.gestureOverlayInstalled.setFromController(true)
    model.talkBackInstalled.setFromController(true)

    // Simulate Permission Monitoring enabled:
    model.fontScaleSettable.setFromController(false)
    model.gestureOverlayInstalled.setFromController(true)
    assertThat(panel.getDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }.isShowing).isTrue()
    assertThat(panel.getDescendant<JComboBox<*>> { it.name == GESTURE_NAVIGATION_TITLE }.isShowing).isFalse()
    assertThat(panel.getDescendant<JComboBox<*>> { it.name == APP_LANGUAGE_TITLE }.isShowing).isTrue()
    assertThat(panel.getDescendant<JCheckBox> { it.name == TALKBACK_TITLE }.isShowing).isFalse()
    assertThat(panel.getDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }.isShowing).isFalse()
    assertThat(panel.getDescendant<JSlider> { it.name == FONT_SCALE_TITLE }.isShowing).isFalse()
    assertThat(panel.getDescendant<JSlider> { it.name == DENSITY_TITLE }.isShowing).isFalse()
    assertThat(panel.getDescendant<JCheckBox> { it.name == DEBUG_LAYOUT_TITLE }.isShowing).isTrue()
    assertThat(panel.getDescendant<JLabel> { it.text == PERMISSION_HINT_LINE1 }.isShowing).isTrue()
    assertThat(panel.getDescendant<JLabel> { it.text == PERMISSION_HINT_LINE2 }.isShowing).isTrue()

    // Simulate Permission Monitoring disabled:
    model.fontScaleSettable.setFromController(true)
    assertThat(panel.getDescendant<JCheckBox> { it.name == DARK_THEME_TITLE }.isShowing).isTrue()
    assertThat(panel.getDescendant<JComboBox<*>> { it.name == GESTURE_NAVIGATION_TITLE }.isShowing).isTrue()
    assertThat(panel.getDescendant<JComboBox<*>> { it.name == APP_LANGUAGE_TITLE }.isShowing).isTrue()
    assertThat(panel.getDescendant<JCheckBox> { it.name == TALKBACK_TITLE }.isShowing).isTrue()
    assertThat(panel.getDescendant<JCheckBox> { it.name == SELECT_TO_SPEAK_TITLE }.isShowing).isTrue()
    assertThat(panel.getDescendant<JSlider> { it.name == FONT_SCALE_TITLE }.isShowing).isTrue()
    assertThat(panel.getDescendant<JSlider> { it.name == DENSITY_TITLE }.isShowing).isTrue()
    assertThat(panel.getDescendant<JCheckBox> { it.name == DEBUG_LAYOUT_TITLE }.isShowing).isTrue()
    assertThat(panel.getDescendant<JLabel> { it.text == PERMISSION_HINT_LINE1 }.isShowing).isFalse()
    assertThat(panel.getDescendant<JLabel> { it.text == PERMISSION_HINT_LINE2 }.isShowing).isFalse()
  }
}
