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
package com.android.tools.idea.ui.screenrecording

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.ui.extractTextFromHtml
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JEditorPane
import javax.swing.JTextField

/** Tests for [ScreenRecorderOptionsDialog]. */
@RunsInEdt
class ScreenRecorderOptionsDialogTest {

  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), PortableUiFontRule(), HeadlessDialogRule())

  private val project: Project
    get() = projectRule.project
  private val testRootDisposable
    get() = projectRule.disposable
  private val settings by lazy { DeviceScreenRecordingSettings.getInstance() }

  @Before
  fun setUp() {
    settings.loadState(DeviceScreenRecordingSettings())
  }

  @After
  fun tearDown() {
    settings.loadState(DeviceScreenRecordingSettings()) // Reset settings.
  }

  @Test
  fun testVirtualDevice() {
    assertThat(settings.bitRateMbps).isEqualTo(4)
    assertThat(settings.scale).isEqualTo(1.0)
    assertThat(settings.showTaps).isEqualTo(false)
    assertThat(settings.useEmulatorRecordingWhenAvailable).isEqualTo(true)
    val dialog = ScreenRecorderOptionsDialog(project, true, 33)
    createModalDialogAndInteractWithIt(dialog::show) { dlg ->
      val ui = FakeUi(dlg.rootPane)
      val recordingLengthField = ui.getComponent<JEditorPane>()
      val bitRateField = ui.getComponent<JTextField>()
      val resolutionPercentField = ui.getComponent<ComboBox<Int>>()
      val showTapsField = ui.getComponent<JCheckBox> { it.text == "Show taps" }
      val emulatorRecordingField = ui.getComponent<JCheckBox> { it.text == "Use emulator recording (WebM)" }
      assertThat(bitRateField.text).isEqualTo("4")
      assertThat(resolutionPercentField.selectedItem).isEqualTo(100)
      assertThat(showTapsField.isSelected).isEqualTo(false)
      assertThat(emulatorRecordingField.isSelected).isEqualTo(true)
      assertThat(extractTextFromHtml(recordingLengthField.text)).isEqualTo("The length of the recording can be up to 30 minutes.")
      bitRateField.text = "8"
      resolutionPercentField.selectedItem = 50
      showTapsField.isSelected = true
      emulatorRecordingField.isSelected = false
      assertThat(extractTextFromHtml(recordingLengthField.text)).isEqualTo("The length of the recording can be up to 3 minutes.")
      dlg.clickDefaultButton()
    }
    assertThat(settings.bitRateMbps).isEqualTo(8)
    assertThat(settings.scale).isEqualTo(0.5)
    assertThat(settings.showTaps).isEqualTo(true)
    assertThat(settings.useEmulatorRecordingWhenAvailable).isEqualTo(false)
  }

  @Test
  fun testPhysicalDevice() {
    assertThat(settings.bitRateMbps).isEqualTo(4)
    assertThat(settings.scale).isEqualTo(1.0)
    assertThat(settings.showTaps).isEqualTo(false)
    val dialog = ScreenRecorderOptionsDialog(project, false, 34)
    createModalDialogAndInteractWithIt(dialog::show) { dlg ->
      val ui = FakeUi(dlg.rootPane)
      val recordingLengthField = ui.getComponent<JEditorPane>()
      val bitRateField = ui.getComponent<JTextField>()
      val resolutionPercentField = ui.getComponent<ComboBox<Int>>()
      val showTapsField = ui.findAllComponents<JCheckBox>().single()
      assertThat(showTapsField.text).isEqualTo("Show taps")
      assertThat(bitRateField.text).isEqualTo("4")
      assertThat(resolutionPercentField.selectedItem).isEqualTo(100)
      assertThat(showTapsField.isSelected).isEqualTo(false)
      assertThat(extractTextFromHtml(recordingLengthField.text)).isEqualTo("The length of the recording can be up to 30 minutes.")
      bitRateField.text = "2"
      resolutionPercentField.selectedItem = 25
      showTapsField.isSelected = true
      dlg.clickDefaultButton()
    }
    assertThat(settings.bitRateMbps).isEqualTo(2)
    assertThat(settings.scale).isEqualTo(0.25)
    assertThat(settings.showTaps).isEqualTo(true)
  }

  @Test
  fun testPhysicalDeviceWithStreamlinedSave() {
    StudioFlags.SCREENSHOT_STREAMLINED_SAVING.overrideForTest(true, testRootDisposable)
    assertThat(settings.bitRateMbps).isEqualTo(4)
    assertThat(settings.scale).isEqualTo(1.0)
    assertThat(settings.showTaps).isEqualTo(false)
    val dialog = ScreenRecorderOptionsDialog(project, false, 34)
    createModalDialogAndInteractWithIt(dialog::show) { dlg ->
      val ui = FakeUi(dlg.rootPane)
      val recordingLengthField = ui.getComponent<JEditorPane>()
      val bitRateField = ui.getComponent<JTextField>()
      val resolutionPercentField = ui.getComponent<ComboBox<Int>>()
      val showTapsField = ui.findAllComponents<JCheckBox>().single()
      val configureSaveButton = ui.getComponent<JButton>()
      assertThat(showTapsField.text).isEqualTo("Show taps")
      assertThat(bitRateField.text).isEqualTo("4")
      assertThat(resolutionPercentField.selectedItem).isEqualTo(100)
      assertThat(showTapsField.isSelected).isEqualTo(false)
      assertThat(extractTextFromHtml(recordingLengthField.text)).isEqualTo("The length of the recording can be up to 30 minutes.")
      assertThat(configureSaveButton.text).isEqualTo("Configure")
      bitRateField.text = "2"
      resolutionPercentField.selectedItem = 25
      showTapsField.isSelected = true
      createModalDialogAndInteractWithIt({ ui.clickOn(configureSaveButton) }) { dlg2 ->
        assertThat(dlg2.title).isEqualTo("Settings")
        dlg2.clickDefaultButton()
      }
      dlg.clickDefaultButton()
    }
    assertThat(settings.bitRateMbps).isEqualTo(2)
    assertThat(settings.scale).isEqualTo(0.25)
    assertThat(settings.showTaps).isEqualTo(true)
  }
}