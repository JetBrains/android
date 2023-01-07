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
package com.android.tools.idea.streaming.emulator.dialogs

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JCheckBox
import javax.swing.JTextField
import javax.swing.JTextPane

/**
 * Tests for [EditSnapshotDialog].
 */
@RunsInEdt
class EditSnapshotDialogTest {
  private val projectRule = AndroidProjectRule.inMemory()
  @get:Rule
  val ruleChain = RuleChain(projectRule, EdtRule())

  private val testRootDisposable
    get() = projectRule.testRootDisposable

  @Before
  fun setUp() {
    enableHeadlessDialogs(testRootDisposable)
  }

  @Test
  fun testDialog() {
    val dialogPanel = EditSnapshotDialog("snap_2020-09-08_18-24-23", "", false)
    val dialogWrapper = dialogPanel.createWrapper()
    createModalDialogAndInteractWithIt(dialogWrapper::show) { dlg ->
      val rootPane = dlg.rootPane
      val ui = FakeUi(rootPane)
      val nameField = ui.getComponent<JTextField>()
      nameField.text = "  magic moment  " // Leading and trailing spaces are used to test truncation.

      val descriptionField = ui.getComponent<JTextPane>()
      descriptionField.text = "  This snapshot captures a truly magic moment  " // Leading and trailing spaces are used to test truncation.

      val useToBootCheckbox = ui.getComponent<JCheckBox>()
      useToBootCheckbox.isSelected = true

      val okButton = rootPane.defaultButton
      assertThat(okButton.text).isEqualTo("OK")
      ui.clickOn(okButton)
    }

    assertThat(dialogWrapper.isOK).isTrue()
    assertThat(dialogPanel.snapshotName).isEqualTo("magic moment")
    assertThat(dialogPanel.snapshotDescription).isEqualTo("This snapshot captures a truly magic moment")
    assertThat(dialogPanel.useToBoot).isTrue()
  }
}
