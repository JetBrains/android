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
import com.android.tools.idea.streaming.uisettings.binding.ChangeListener
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doAnswer
import java.awt.event.WindowFocusListener
import javax.swing.JCheckBox
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
    model = UiSettingsModel()
    panel = UiSettingsPanel(model, projectRule.disposable)
    model.inDarkMode.uiChangeListener = ChangeListener { lastCommand = "dark=$it" }
    model.fontSizeInPercent.uiChangeListener = ChangeListener { lastCommand = "fontSize=$it" }
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
  fun testSetFontSizeFromUi() {
    val slider = AdtUiUtils.allComponents(panel).filterIsInstance<JSlider>().filter { it.name == FONT_SIZE_TITLE }.single()
    assertThat(slider.value).isEqualTo(FontSize.NORMAL.ordinal)

    slider.value = FontSize.values().size - 1
    waitForCondition(1.seconds) { lastCommand == "fontSize=200" }

    slider.value = 0
    waitForCondition(1.seconds) { lastCommand == "fontSize=85" }
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
