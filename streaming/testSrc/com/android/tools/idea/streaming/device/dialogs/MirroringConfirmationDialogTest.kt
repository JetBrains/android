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
package com.android.tools.idea.streaming.device.dialogs

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
  fun testAccept() {
    val dialogPanel = MirroringConfirmationDialog("Pixel 6")
    val dialogWrapper = dialogPanel.createWrapper()
    createModalDialogAndInteractWithIt(dialogWrapper::show) { dlg ->
      val rootPane = dlg.rootPane
      val ui = FakeUi(rootPane)
      val message = ui.getComponent<JEditorPane>()
      assertThat(message.text).contains("<b>Warning:</b> Mirroring might result in information disclosure")

      val acceptButton = rootPane.defaultButton
      assertThat(acceptButton.text).isEqualTo("Acknowledge")
      ui.clickOn(acceptButton)
    }

    assertThat(dialogWrapper.exitCode).isEqualTo(MirroringConfirmationDialog.ACCEPT_EXIT_CODE)
  }

  @Test
  fun testReject() {
    val dialogPanel = MirroringConfirmationDialog("Pixel 5")
    val dialogWrapper = dialogPanel.createWrapper()
    createModalDialogAndInteractWithIt(dialogWrapper::show) { dlg ->
      val rootPane = dlg.rootPane
      val ui = FakeUi(rootPane)
      val rejectButton = ui.getComponent<JButton> { !it.isDefaultButton }
      assertThat(rejectButton.text).isEqualTo("Disable Mirroring")
      ui.clickOn(rejectButton)
    }

    assertThat(dialogWrapper.exitCode).isEqualTo(MirroringConfirmationDialog.REJECT_EXIT_CODE)
  }
}
