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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.adtui.swing.popup.FakeBalloon
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.streaming.uisettings.binding.ChangeListener
import com.android.tools.idea.streaming.uisettings.data.DEFAULT_LANGUAGE
import com.android.tools.idea.streaming.uisettings.testutil.DANISH_LANGUAGE
import com.android.tools.idea.streaming.uisettings.testutil.RUSSIAN_LANGUAGE
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.mockito.Mockito.doAnswer
import java.awt.Dimension
import java.awt.event.WindowFocusListener
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingUtilities
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
    model = UiSettingsModel(Dimension(1344, 2992), 480)
    model.appLanguage.addElement(DEFAULT_LANGUAGE)
    model.appLanguage.addElement(DANISH_LANGUAGE)
    model.appLanguage.addElement(RUSSIAN_LANGUAGE)
    model.appLanguage.selection.setFromController(DEFAULT_LANGUAGE)

    panel = UiSettingsPanel(model, showResetButton = nameRule.methodName == "testResetButton")
    model.inDarkMode.uiChangeListener = ChangeListener { lastCommand = "dark=$it" }
    model.appLanguage.selection.uiChangeListener = ChangeListener { lastCommand = "locale=${it?.tag}" }
    model.talkBackOn.uiChangeListener = ChangeListener { lastCommand = "talkBackOn=$it" }
    model.selectToSpeakOn.uiChangeListener = ChangeListener { lastCommand = "selectToSpeakOn=$it" }
    model.fontSizeInPercent.uiChangeListener = ChangeListener { lastCommand = "fontSize=$it" }
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
  fun testSetFontSizeFromUi() {
    val slider = panel.getDescendant<JSlider> { it.name == FONT_SIZE_TITLE }
    assertThat(slider.value).isEqualTo(FontSize.NORMAL.ordinal)

    slider.value = FontSize.values().size - 1
    waitForCondition(1.seconds) { lastCommand == "fontSize=200" }

    slider.value = 0
    waitForCondition(1.seconds) { lastCommand == "fontSize=85" }
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
  fun testNoResetButtonIfNotRequested() {
    assertThat(panel.findDescendant<JButton> { it.name == RESET_BUTTON_TEXT }).isNull()
  }

  @Test
  fun testResetButton() {
    val button = panel.getDescendant<JButton> { it.name == RESET_BUTTON_TEXT }
    button.doClick()
    waitForCondition(1.seconds) { lastCommand == "reset" }
  }

  @Test
  fun testCreatePicker() {
    val component = JPanel().apply { setBounds(0, 0, 600, 800) }
    FakeUi(component, createFakeWindow = true)
    val balloon = panel.createPicker(component, projectRule.disposable) as FakeBalloon
    assertThat(balloon.component).isInstanceOf(UiSettingsPanel::class.java)
  }

  @Test
  fun testPickerClosesWhenWindowCloses() {
    val component = JPanel().apply { setBounds(0, 0, 600, 800) }
    FakeUi(component, createFakeWindow = true)
    val window = SwingUtilities.windowForComponent(component)
    val listeners = mutableListOf<WindowFocusListener>()
    doAnswer { invocation ->
      listeners.add(invocation.arguments[0] as WindowFocusListener)
    }.whenever(window).addWindowFocusListener(any())
    val balloon = panel.createPicker(component, projectRule.disposable) as FakeBalloon
    listeners.forEach { it.windowLostFocus(mock()) }
    assertThat(balloon.isDisposed).isTrue()
  }

  @Test
  fun testPickerClosesWithParentDisposable() {
    val component = JPanel().apply { setBounds(0, 0, 600, 800) }
    FakeUi(component, createFakeWindow = true)
    val parentDisposable = Disposer.newDisposable()
    Disposer.register(projectRule.disposable, parentDisposable)
    val balloon = panel.createPicker(component, parentDisposable) as FakeBalloon

    Disposer.dispose(parentDisposable)
    assertThat(balloon.isDisposed).isTrue()
  }
}
