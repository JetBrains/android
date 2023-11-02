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
import com.android.tools.adtui.common.AdtUiUtils
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
import javax.swing.JCheckBox
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
  }

  @Test
  fun testSetFromUi() {
    val checkBox = AdtUiUtils.allComponents(panel).filterIsInstance<JCheckBox>().filter { it.name == DARK_THEME_TITLE }.single()
    assertThat(checkBox.isSelected).isFalse()

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "dark=true" }

    checkBox.doClick()
    waitForCondition(1.seconds) { lastCommand == "dark=false" }
  }

  @Test
  fun testCreatePicker() {
    val balloon = panel.createPicker() as FakeBalloon
    Disposer.register(projectRule.disposable, balloon)
    assertThat(balloon.component).isInstanceOf(UiSettingsPanel::class.java)
  }
}
