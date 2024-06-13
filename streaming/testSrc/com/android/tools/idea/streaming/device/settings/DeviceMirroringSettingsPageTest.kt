/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.streaming.device.settings

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.device.dialogs.MirroringConfirmationDialog
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.flags.override
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JCheckBox
import javax.swing.JTextField

/**
 * Tests for [DeviceMirroringSettingsPage] and [DeviceMirroringSettings].
 */
@RunsInEdt
class DeviceMirroringSettingsPageTest {

  private val projectRule = ProjectRule()
  @get:Rule
  val ruleChain = RuleChain(projectRule, EdtRule(), HeadlessDialogRule())

  private val settings by lazy { DeviceMirroringSettings.getInstance() }
  private val settingsPage by lazy {
    val provider = DeviceMirroringConfigurableProvider()
    assertThat(provider.canCreateConfigurable()).isTrue()
    provider.createConfigurable()
  }

  @Before
  fun setUp() {
    StudioFlags.DEVICE_MIRRORING_AUDIO.override(true, projectRule.disposable)
    settings.loadState(DeviceMirroringSettings())
  }

  @After
  fun tearDown() {
    settings.loadState(DeviceMirroringSettings())
    settingsPage.disposeUIResources()
  }

  @Test
  fun testSettingsPage() {
    val component = settingsPage.createComponent()!!
    val ui = FakeUi(component)
    val activateOnConnectionCheckBox =
        ui.getComponent<JCheckBox> { it.text == "Activate mirroring when a new physical device is connected" }
    val activateOnAppLaunchCheckBox =
        ui.getComponent<JCheckBox> { it.text == "Activate mirroring when launching an app on a physical device" }
    val activateOnTestLaunchCheckBox =
        ui.getComponent<JCheckBox> { it.text == "Activate mirroring when launching a test on a physical device" }
    val redirectAudioCheckBox = ui.getComponent<JCheckBox> { it.text == "Redirect audio from local devices" }
    val synchronizeClipboardCheckBox = ui.getComponent<JCheckBox> { it.text == "Synchronize clipboard" }
    val maxSyncedClipboardLengthTextField = ui.getComponent<JTextField>()
    val turnOffDisplayWhileMirroringCheckBox = ui.getComponent<JCheckBox> { it.text == "Turn off device display while mirroring" }

    assertThat(settingsPage.isModified).isFalse()
    assertThat(activateOnConnectionCheckBox.isEnabled).isTrue()
    assertThat(activateOnAppLaunchCheckBox.isEnabled).isTrue()
    assertThat(activateOnTestLaunchCheckBox.isEnabled).isTrue()
    assertThat(redirectAudioCheckBox.isEnabled).isTrue()
    assertThat(synchronizeClipboardCheckBox.isEnabled).isTrue()
    assertThat(maxSyncedClipboardLengthTextField.isEnabled).isTrue()
    assertThat(turnOffDisplayWhileMirroringCheckBox.isEnabled).isTrue()
    assertThat(activateOnConnectionCheckBox.isSelected).isFalse()
    assertThat(activateOnAppLaunchCheckBox.isSelected).isFalse()
    assertThat(activateOnTestLaunchCheckBox.isSelected).isFalse()
    assertThat(redirectAudioCheckBox.isSelected).isFalse()
    assertThat(synchronizeClipboardCheckBox.isSelected).isTrue()
    assertThat(maxSyncedClipboardLengthTextField.text).isEqualTo(DeviceMirroringSettings.MAX_SYNCED_CLIPBOARD_LENGTH_DEFAULT.toString())
    assertThat(turnOffDisplayWhileMirroringCheckBox.isSelected).isFalse()

    createModalDialogAndInteractWithIt(activateOnConnectionCheckBox::doClick) { dialog ->
      assertThat(dialog.title).isEqualTo("Privacy Notice")
      dialog.close(MirroringConfirmationDialog.REJECT_EXIT_CODE)
    }
    assertThat(activateOnConnectionCheckBox.isSelected).isFalse()
    assertThat(settingsPage.isModified).isFalse()

    createModalDialogAndInteractWithIt(activateOnAppLaunchCheckBox::doClick) { dialog ->
      assertThat(dialog.title).isEqualTo("Privacy Notice")
      dialog.close(MirroringConfirmationDialog.ACCEPT_EXIT_CODE)
    }
    assertThat(activateOnAppLaunchCheckBox.isSelected).isTrue()
    assertThat(settingsPage.isModified).isTrue()

    settingsPage.apply()
    assertThat(settings.activateOnAppLaunch).isTrue()
    assertThat(settingsPage.isModified).isFalse()

    activateOnTestLaunchCheckBox.doClick()
    assertThat(activateOnTestLaunchCheckBox.isSelected).isTrue()
    assertThat(settingsPage.isModified).isTrue()
    settingsPage.apply()
    assertThat(settings.activateOnTestLaunch).isTrue()
    assertThat(settingsPage.isModified).isFalse()

    redirectAudioCheckBox.isSelected = true
    assertThat(settingsPage.isModified).isTrue()
    settingsPage.apply()
    assertThat(settings.redirectAudio).isTrue()
    assertThat(settingsPage.isModified).isFalse()
    redirectAudioCheckBox.isSelected = false
    assertThat(settingsPage.isModified).isTrue()

    maxSyncedClipboardLengthTextField.text = " 3000 "
    assertThat(settingsPage.isModified).isTrue()
    settingsPage.apply()
    assertThat(settings.maxSyncedClipboardLength).isEqualTo(3000)
    assertThat(settingsPage.isModified).isFalse()
    maxSyncedClipboardLengthTextField.text = "   3000   "
    assertThat(settingsPage.isModified).isFalse()

    synchronizeClipboardCheckBox.isSelected = false
    assertThat(maxSyncedClipboardLengthTextField.isEnabled).isFalse()
    assertThat(settingsPage.isModified).isTrue()
    settingsPage.apply()
    assertThat(settings.synchronizeClipboard).isFalse()
    assertThat(settingsPage.isModified).isFalse()
    synchronizeClipboardCheckBox.isSelected = true
    assertThat(settingsPage.isModified).isTrue()

    turnOffDisplayWhileMirroringCheckBox.isSelected = false
    assertThat(settingsPage.isModified).isTrue()
    settingsPage.apply()
    assertThat(settings.turnOffDisplayWhileMirroring).isFalse()
    assertThat(settingsPage.isModified).isFalse()

    settings.loadState(DeviceMirroringSettings())
    settingsPage.reset()
    assertThat(settingsPage.isModified).isFalse()
    assertThat(activateOnConnectionCheckBox.isSelected).isFalse()
    assertThat(activateOnAppLaunchCheckBox.isSelected).isFalse()
    assertThat(activateOnTestLaunchCheckBox.isSelected).isFalse()
    assertThat(redirectAudioCheckBox.isSelected).isFalse()
    assertThat(synchronizeClipboardCheckBox.isSelected).isTrue()
    assertThat(maxSyncedClipboardLengthTextField.text).isEqualTo(DeviceMirroringSettings.MAX_SYNCED_CLIPBOARD_LENGTH_DEFAULT.toString())
    assertThat(turnOffDisplayWhileMirroringCheckBox.isSelected).isFalse()
  }
}
