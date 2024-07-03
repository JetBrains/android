/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wizard.model

import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.idea.testing.ui.flatten
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

@RunsInEdt
class ModelWizardDialogTest {
  @get:Rule
  val chain =
    RuleChain.outerRule(EdtRule())
      .around(ApplicationRule())
      .around(HeadlessDialogRule())

  @Test
  fun `check looks for single step wizard`() {
    val testWizard = ModelWizard.Builder().apply {
      addStep(createTestStep())
    }.build()

    createModalDialogAndInteractWithIt(dialogTrigger = { createDialog(testWizard).show() }) { dialogWrapper ->
      assertThat(dialogWrapper.title).isEqualTo("Test")
      with(dialogWrapper.contentPane.flatten()) {
        val title = filterIsInstance<JLabel>().mapNotNull { it.text.takeUnless { text -> text.isEmpty() } }.single()
        assertThat(title).isEqualTo("Test Step")

        // Check buttons: no "Previous" & "Next" action buttons, only "Cancel" & "Finish" action
        // buttons.
        val buttons = filterIsInstance<JButton>().map { it.text }
        assertThat(buttons).containsExactly("Cancel", "Finish")
      }
    }
  }

  @Test
  fun `check looks for multi-step wizard`() {
    val testWizard = ModelWizard.Builder().apply {
      addStep(createTestStep(title = "Test Step 1"))
      addStep(createTestStep(title = "Test Step 2"))
    }.build()

    val dialog = createDialog(testWizard)
    createModalDialogAndInteractWithIt(dialogTrigger = { dialog.show() }) { dialogWrapper ->
      assertThat(dialogWrapper.title).isEqualTo("Test")
      with(dialogWrapper.contentPane.flatten()) {
        val title = filterIsInstance<JLabel>().mapNotNull { it.text.takeUnless { text -> text.isEmpty() } }.single()
        assertThat(title).isEqualTo("Test Step 1")

        val buttons = filterIsInstance<JButton>().map { it.text }
        assertThat(buttons).containsExactly("Previous", "Next", "Cancel", "Finish")
      }
    }
  }

  private fun createTestWizardModel() = object : WizardModel() {
    override fun handleFinished() = Unit
  }

  private fun createTestStep(model: WizardModel = createTestWizardModel(), title: String = "Test Step"): ModelWizardStep<WizardModel> {
    return object : ModelWizardStep<WizardModel>(model, title) {
      override fun getComponent() = JPanel()
    }
  }

  private fun createDialog(wizard: ModelWizard): ModelWizardDialog {
    return StudioWizardDialogBuilder(wizard, "Test").build()
  }
}