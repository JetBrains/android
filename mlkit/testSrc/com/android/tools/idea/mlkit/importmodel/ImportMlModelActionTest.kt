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
package com.android.tools.idea.mlkit.importmodel

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.mlkit.MlProjectTestUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.gradleModule
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.ui.UIUtil
import kotlin.test.assertNotNull
import org.jetbrains.android.util.AndroidBundle
import org.junit.Rule
import org.junit.Test

/** Unit tests for [ImportMlModelAction]. */
@RunsInEdt
class ImportMlModelActionTest {
  private val myAction = ImportMlModelAction()

  private val projectRule = AndroidProjectRule.withAndroidModels()

  @get:Rule val ruleChain = RuleChain(projectRule, EdtRule(), HeadlessDialogRule())

  private fun setupProjectAndEvent(agpVersion: String, minSdkVersion: Int): AnActionEvent {
    MlProjectTestUtil.setupTestMlProject(
      projectRule.project,
      agpVersion,
      minSdkVersion,
      ImmutableList.of(),
    )

    val dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, projectRule.project)
        .add(PlatformCoreDataKeys.MODULE, projectRule.project.gradleModule(":"))
        .build()

    return TestActionEvent.createTestEvent(dataContext)
  }

  @Test
  fun allConditionsMet_shouldEnabledPresentation() {
    val event =
      setupProjectAndEvent(ImportMlModelAction.MIN_AGP_VERSION, ImportMlModelAction.MIN_SDK_VERSION)

    myAction.update(event)
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun lowAgpVersion_shouldDisablePresentation() {
    val event = setupProjectAndEvent("3.6.0", ImportMlModelAction.MIN_SDK_VERSION)

    myAction.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.text)
      .isEqualTo(
        AndroidBundle.message(
          "android.wizard.action.requires.new.agp",
          ImportMlModelAction.TITLE,
          ImportMlModelAction.MIN_AGP_VERSION,
        )
      )
  }

  @Test
  fun lowMinSdkApi_shouldDisablePresentation() {
    val event =
      setupProjectAndEvent(
        ImportMlModelAction.MIN_AGP_VERSION,
        ImportMlModelAction.MIN_SDK_VERSION - 2,
      )

    myAction.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.text)
      .isEqualTo(
        AndroidBundle.message(
          "android.wizard.action.requires.minsdk",
          ImportMlModelAction.TITLE,
          ImportMlModelAction.MIN_SDK_VERSION,
        )
      )
  }

  @Test
  fun testDialogValidation() {
    val tfliteModelFilePath =
      resolveWorkspacePath("tools/adt/idea/mlkit/testData/mobilenet_quant_metadata.tflite")

    val event =
      setupProjectAndEvent(ImportMlModelAction.MIN_AGP_VERSION, ImportMlModelAction.MIN_SDK_VERSION)

    val dialog = assertNotNull(myAction.createDialog(event))

    createModalDialogAndInteractWithIt(dialog::show) { dlg ->
      assertThat(dlg.title).isEqualTo("Import TensorFlow Lite model")

      val rootPane = dialog.rootPane
      val finishButton = rootPane.defaultButton
      val ui = FakeUi(rootPane)

      val validatorPanel = ui.getComponent<ValidatorPanel>()

      // Initially the finish button should be disabled, and there should be an error indicating a
      // model has to be selected.
      UIUtil.dispatchAllInvocationEvents()
      assertThat(finishButton.isEnabled).isFalse()
      assertThat(validatorPanel.hasErrors().get()).isTrue()

      val fileTextBox = ui.getComponent<TextFieldWithBrowseButton>()
      val pathString = tfliteModelFilePath.toAbsolutePath().toString()
      fileTextBox.text = pathString

      // After selecting a valid model, the finish button is enabled and there are no errors.
      UIUtil.dispatchAllInvocationEvents()
      assertThat(finishButton.isEnabled).isTrue()
      assertThat(validatorPanel.hasErrors().get()).isFalse()
    }
  }
}
