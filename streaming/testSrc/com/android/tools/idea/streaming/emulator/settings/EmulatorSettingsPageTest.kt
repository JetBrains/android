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
package com.android.tools.idea.streaming.emulator.settings

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.streaming.DEFAULT_SNAPSHOT_AUTO_DELETION_POLICY
import com.android.tools.idea.streaming.EmulatorSettings
import com.android.tools.idea.streaming.EmulatorSettings.CameraVelocityControls
import com.android.tools.idea.streaming.EmulatorSettings.SnapshotAutoDeletionPolicy
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JCheckBox

/**
 * Tests for [EmulatorSettingsPage] and [EmulatorSettings].
 */
@RunsInEdt
class EmulatorSettingsPageTest {
  @get:Rule
  val ruleChain = RuleChain(ProjectRule(), EdtRule())

  private val settings: EmulatorSettings by lazy { EmulatorSettings.getInstance() }
  private val settingsUi: Configurable by lazy {
    val provider = EmulatorConfigurableProvider()
    assertThat(provider.canCreateConfigurable()).isTrue()
    provider.createConfigurable()
  }

  @Before
  fun setUp() {
    settings.loadState(EmulatorSettings())
  }

  @After
  fun tearDown() {
    settings.loadState(EmulatorSettings())
    settingsUi.disposeUIResources()
  }

  @Test
  fun testSettingsUi() {
    val ui = FakeUi(settingsUi.createComponent()!!)
    val launchInToolWindowCheckBox = ui.getComponent<JCheckBox> { c -> c.text == "Launch in the Running Devices tool window" }
    val activateOnAppLaunchCheckBox = ui.getComponent<JCheckBox> { it.text == "Open the Running Devices tool window when launching an app" }
    val activateOnTestLaunchCheckBox =
        ui.getComponent<JCheckBox> { it.text == "Open the Running Devices tool window when launching a test" }
    val synchronizeClipboardCheckBox = ui.getComponent<JCheckBox> { c -> c.text == "Synchronize clipboard" }
    val showCameraControlPromptsCheckBox = ui.getComponent<JCheckBox> { c -> c.text == "Show camera control prompts" }
    val cameraVelocityControlComboBox = ui.getComponent<ComboBox<*>> { it.selectedItem is CameraVelocityControls }
    val snapshotAutoDeletionPolicyComboBox = ui.getComponent<ComboBox<*>> { it.selectedItem is SnapshotAutoDeletionPolicy }

    settingsUi.reset()

    assertThat(launchInToolWindowCheckBox.isSelected).isTrue()
    assertThat(activateOnAppLaunchCheckBox.isEnabled).isTrue()
    assertThat(activateOnTestLaunchCheckBox.isEnabled).isTrue()
    assertThat(synchronizeClipboardCheckBox.isSelected).isTrue()
    assertThat(showCameraControlPromptsCheckBox.isSelected).isTrue()
    assertThat(cameraVelocityControlComboBox.selectedItem).isEqualTo(CameraVelocityControls.WASDQE)
    assertThat(cameraVelocityControlComboBox.isEnabled).isTrue()
    assertThat(snapshotAutoDeletionPolicyComboBox.selectedItem).isEqualTo(DEFAULT_SNAPSHOT_AUTO_DELETION_POLICY)
    assertThat(snapshotAutoDeletionPolicyComboBox.isEnabled).isTrue()
    assertThat(settingsUi.isModified).isFalse()

    activateOnAppLaunchCheckBox.isSelected = false
    assertThat(settingsUi.isModified).isTrue()
    settingsUi.apply()
    assertThat(settings.activateOnTestLaunch).isFalse()
    assertThat(settingsUi.isModified).isFalse()

    activateOnTestLaunchCheckBox.isSelected = true
    assertThat(settingsUi.isModified).isTrue()
    settingsUi.apply()
    assertThat(settings.activateOnTestLaunch).isTrue()
    assertThat(settingsUi.isModified).isFalse()

    synchronizeClipboardCheckBox.isSelected = false
    assertThat(settingsUi.isModified).isTrue()
    settingsUi.apply()
    assertThat(settings.synchronizeClipboard).isFalse()
    assertThat(settingsUi.isModified).isFalse()

    showCameraControlPromptsCheckBox.isSelected = false
    assertThat(settingsUi.isModified).isTrue()
    settingsUi.apply()
    assertThat(settings.showCameraControlPrompts).isFalse()
    assertThat(settingsUi.isModified).isFalse()

    cameraVelocityControlComboBox.selectedItem = CameraVelocityControls.ZQSDAE
    assertThat(settingsUi.isModified).isTrue()
    settingsUi.apply()
    assertThat(settings.cameraVelocityControls).isEqualTo(CameraVelocityControls.ZQSDAE)
    assertThat(settingsUi.isModified).isFalse()

    snapshotAutoDeletionPolicyComboBox.selectedItem = SnapshotAutoDeletionPolicy.DELETE_AUTOMATICALLY
    assertThat(settingsUi.isModified).isTrue()
    settingsUi.apply()
    assertThat(settings.snapshotAutoDeletionPolicy).isEqualTo(SnapshotAutoDeletionPolicy.DELETE_AUTOMATICALLY)
    assertThat(settingsUi.isModified).isFalse()

    launchInToolWindowCheckBox.isSelected = false
    assertThat(activateOnAppLaunchCheckBox.isEnabled).isTrue()
    assertThat(activateOnTestLaunchCheckBox.isEnabled).isTrue()
    assertThat(synchronizeClipboardCheckBox.isEnabled).isTrue()
    assertThat(showCameraControlPromptsCheckBox.isEnabled).isTrue()
    assertThat(cameraVelocityControlComboBox.isEnabled).isTrue()
    assertThat(snapshotAutoDeletionPolicyComboBox.isEnabled).isTrue()
    assertThat(settingsUi.isModified).isTrue()
    settingsUi.apply()
    assertThat(settings.launchInToolWindow).isFalse()
    assertThat(settingsUi.isModified).isFalse()
  }
}
