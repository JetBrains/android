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
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.FakeBalloon
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.streaming.emulator.APPLICATION_ID1
import com.android.tools.idea.streaming.emulator.APPLICATION_ID2
import com.android.tools.idea.streaming.uisettings.binding.ChangeListener
import com.android.tools.idea.streaming.uisettings.data.DEFAULT_LANGUAGE
import com.android.tools.idea.streaming.uisettings.testutil.DANISH_LANGUAGE
import com.android.tools.idea.streaming.uisettings.testutil.RUSSIAN_LANGUAGE
import com.android.tools.idea.streaming.uisettings.testutil.SPANISH_LANGUAGE
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doAnswer
import java.awt.Dimension
import java.awt.event.WindowFocusListener
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds

class UiSettingsPanelTest {
  private val popupRule = JBPopupRule()
  private val projectRule = ProjectRule()

  @get:Rule
  val ruleChain = RuleChain(projectRule, popupRule)

  private lateinit var model: UiSettingsModel
  private lateinit var panel: UiSettingsPanel
  private var lastCommand: String = ""

  @Before
  fun before() {
    model = UiSettingsModel(Dimension(1344, 2992), 480)
    val language1 = model.addLanguageModel(APPLICATION_ID1)
    language1.addElement(DEFAULT_LANGUAGE)
    language1.addElement(DANISH_LANGUAGE)
    language1.addElement(SPANISH_LANGUAGE)
    language1.selection.setFromController(DEFAULT_LANGUAGE)
    val language2 = model.addLanguageModel(APPLICATION_ID2)
    language2.addElement(DEFAULT_LANGUAGE)
    language2.addElement(RUSSIAN_LANGUAGE)
    language2.selection.setFromController(DEFAULT_LANGUAGE)
    model.appIds.selection.setFromController(APPLICATION_ID1)

    panel = UiSettingsPanel(model)
    model.inDarkMode.uiChangeListener = ChangeListener { lastCommand = "dark=$it" }
    model.appIds.selection.uiChangeListener = ChangeListener { lastCommand = "applicationId=$it" }
    language1.selection.uiChangeListener = ChangeListener { lastCommand = "locale1=${it?.tag}" }
    language2.selection.uiChangeListener = ChangeListener { lastCommand = "locale2=${it?.tag}" }
    model.talkBackOn.uiChangeListener = ChangeListener { lastCommand = "talkBackOn=$it" }
    model.selectToSpeakOn.uiChangeListener = ChangeListener { lastCommand = "selectToSpeakOn=$it" }
    model.fontSizeInPercent.uiChangeListener = ChangeListener { lastCommand = "fontSize=$it" }
    model.screenDensity.uiChangeListener = ChangeListener { lastCommand = "density=$it" }
  }

  @Test
  fun testSetDarkModeFromUi() {
    val checkBox = AdtUiUtils.allComponents(panel).filterIsInstance<JCheckBox>().filter { it.name == DARK_THEME_TITLE }.single()
    assertThat(checkBox.isSelected).isFalse()

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "dark=true" }

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "dark=false" }
  }

  @Test
  fun testChangeLanguageFromUi() {
    val comboBox1 = findLanguageComboBox(APPLICATION_ID1)
    val comboBox2 = findLanguageComboBox(APPLICATION_ID2)
    assertThat(comboBox1.isVisible).isTrue()
    assertThat(comboBox2.isVisible).isFalse()
    assertThat(comboBox1.selectedIndex).isEqualTo(0)
    assertThat(comboBox2.selectedIndex).isEqualTo(0)

    comboBox1.selectedIndex = 1
    waitForCondition(1.seconds) { lastCommand == "locale1=da" }

    comboBox1.selectedIndex = 2
    waitForCondition(1.seconds) { lastCommand == "locale1=es" }

    comboBox1.selectedIndex = 0
    waitForCondition(1.seconds) { lastCommand == "locale1=" }

    model.appIds.selection.setFromUi(APPLICATION_ID2)
    assertThat(comboBox1.isVisible).isFalse()
    assertThat(comboBox2.isVisible).isTrue()
    assertThat(comboBox1.selectedIndex).isEqualTo(0)
    assertThat(comboBox2.selectedIndex).isEqualTo(0)

    comboBox2.selectedIndex = 1
    waitForCondition(1.seconds) { lastCommand == "locale2=ru" }

    comboBox2.selectedIndex = 0
    waitForCondition(1.seconds) { lastCommand == "locale2=" }
  }

  private fun findLanguageComboBox(applicationId: String): JComboBox<*> {
    return AdtUiUtils.allComponents(panel)
      .filterIsInstance<JComboBox<*>>()
      .filter { it.name == "$APP_LANGUAGE_TITLE ($applicationId)" }
      .single()
  }

  @Test
  fun testSetTalkBackFromUi() {
    val checkBox = AdtUiUtils.allComponents(panel).filterIsInstance<JCheckBox>().filter { it.name == TALKBACK_TITLE }.single()
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
    val checkBox = AdtUiUtils.allComponents(panel).filterIsInstance<JCheckBox>().filter { it.name == SELECT_TO_SPEAK_TITLE }.single()
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
    val slider = AdtUiUtils.allComponents(panel).filterIsInstance<JSlider>().filter { it.name == FONT_SIZE_TITLE }.single()
    assertThat(slider.value).isEqualTo(FontSize.NORMAL.ordinal)

    slider.value = FontSize.values().size - 1
    waitForCondition(1.seconds) { lastCommand == "fontSize=200" }

    slider.value = 0
    waitForCondition(1.seconds) { lastCommand == "fontSize=85" }
  }

  @Test
  fun testSetDensityFromUi() {
    val slider = AdtUiUtils.allComponents(panel).filterIsInstance<JSlider>().filter { it.name == DENSITY_TITLE }.single()
    assertThat(slider.value).isEqualTo(1)

    val densities = GoogleDensityRange.computeDensityRange(Dimension(1344, 2992), 480)
    slider.value = densities.lastIndex
    waitForCondition(1.seconds) { lastCommand == "density=672" }

    slider.value = 0
    waitForCondition(1.seconds) { lastCommand == "density=408" }
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
