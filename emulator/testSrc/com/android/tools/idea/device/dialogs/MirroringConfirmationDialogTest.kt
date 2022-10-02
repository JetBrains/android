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
package com.android.tools.idea.device.dialogs

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
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JEditorPane

/**
 * Tests for [MirroringConfirmationDialog].
 */
@RunsInEdt
class MirroringConfirmationDialogTest {
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
  fun testYes() {
    val dialogPanel = MirroringConfirmationDialog("Pixel 6", doNotAskAgain = false)
    val dialogWrapper = dialogPanel.createWrapper()
    createModalDialogAndInteractWithIt(dialogWrapper::show) { dlg ->
      val rootPane = dlg.rootPane
      val ui = FakeUi(rootPane)
      val message = ui.getComponent<JEditorPane>()
      assertThat(message.text).contains("Would you like to start mirroring of Pixel 6?")
      val doNotAskAgainCheckBox = ui.getComponent<JCheckBox>()
      assertThat(doNotAskAgainCheckBox.text).isEqualTo("Remember my choice and don't ask again when a device is connected")
      assertThat(doNotAskAgainCheckBox.isSelected).isFalse()

      doNotAskAgainCheckBox.isSelected = true

      val yesButton = rootPane.defaultButton
      assertThat(yesButton.text).isEqualTo("Yes")
      ui.clickOn(yesButton)
    }

    assertThat(dialogWrapper.exitCode).isEqualTo(MirroringConfirmationDialog.YES_EXIT_CODE)
    assertThat(dialogPanel.doNotAskAgain).isTrue()
  }

  @Test
  fun testNo() {
    val dialogPanel = MirroringConfirmationDialog("Pixel 5", doNotAskAgain = true)
    val dialogWrapper = dialogPanel.createWrapper()
    createModalDialogAndInteractWithIt(dialogWrapper::show) { dlg ->
      val rootPane = dlg.rootPane
      val ui = FakeUi(rootPane)
      val doNotAskAgainCheckBox = ui.getComponent<JCheckBox>()
      assertThat(doNotAskAgainCheckBox.isSelected).isTrue()

      doNotAskAgainCheckBox.isSelected = false

      val noButton = ui.getComponent<JButton> { !it.isDefaultButton }
      assertThat(noButton.text).isEqualTo("No")
      ui.clickOn(noButton)
    }

    assertThat(dialogWrapper.exitCode).isEqualTo(MirroringConfirmationDialog.NO_EXIT_CODE)
    assertThat(dialogPanel.doNotAskAgain).isFalse()
  }
}
