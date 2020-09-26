/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator.settings

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.emulator.EmulatorSettings
import com.android.tools.idea.emulator.EmulatorSettings.SnapshotAutoDeletionPolicy
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import javax.swing.JCheckBox

/**
 * Tests for [EmulatorSettingsUi].
 */
@RunsInEdt
class EmulatorSettingsUiTest {
  private val projectRule = AndroidProjectRule.inMemory()
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private val settings
    get() = EmulatorSettings.getInstance()

  @Before
  fun setUp() {
    settings.loadState(EmulatorSettings())
  }

  @After
  fun tearDown() {
    settings.loadState(EmulatorSettings())
  }

  @Test
  fun testSettingsUi() {
    val provider = EmulatorConfigurableProvider()
    assertThat(provider.canCreateConfigurable()).isTrue()
    val settingsUi = provider.createConfigurable()
    val component = settingsUi.createComponent()!!
    val ui = FakeUi(component)
    val launchInToolWindowCheckBox = ui.getComponent<JCheckBox>()
    val snapshotAutoDeletionPolicyComboBox = ui.getComponent<ComboBox<SnapshotAutoDeletionPolicy>>()

    settingsUi.reset()

    assertThat(launchInToolWindowCheckBox.isSelected).isFalse()
    assertThat(snapshotAutoDeletionPolicyComboBox.selectedItem).isEqualTo(SnapshotAutoDeletionPolicy.ASK_BEFORE_DELETING)
    assertThat(snapshotAutoDeletionPolicyComboBox.isEnabled).isFalse()
    assertThat(settingsUi.isModified).isFalse()

    launchInToolWindowCheckBox.isSelected = true

    assertThat(settingsUi.isModified).isTrue()
    assertThat(snapshotAutoDeletionPolicyComboBox.isEnabled).isTrue()

    snapshotAutoDeletionPolicyComboBox.selectedItem = SnapshotAutoDeletionPolicy.DELETE_AUTOMATICALLY
    settingsUi.apply()

    assertThat(settings.launchInToolWindow).isTrue()
    assertThat(settings.snapshotAutoDeletionPolicy).isEqualTo(SnapshotAutoDeletionPolicy.DELETE_AUTOMATICALLY)
  }
}
