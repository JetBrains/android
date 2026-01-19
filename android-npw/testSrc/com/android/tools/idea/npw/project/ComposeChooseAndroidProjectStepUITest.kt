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
package com.android.tools.idea.npw.project

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gemini.LlmPrompt
import com.android.tools.idea.npw.project.ChooseAndroidProjectStep.Companion.getProjectTemplates
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.wizard.template.FormFactor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import java.util.function.Supplier
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ComposeChooseAndroidProjectStepUITest {
  @get:Rule val composeTestRule = createStudioComposeTestRule()
  @get:Rule val projectRule = ProjectRule()

  @Test
  fun showMobileFormFactorAsDefaultFocus() = runTest {
    StudioFlags.GEMINI_NEW_PROJECT_AGENT.override(false)
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier)
    model.getAndroidProjectEntries()

    composeTestRule.setContent { ChooseAndroidProjectStepUI(model = model) }

    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.LeftPanel.column)
      .assertExists()
      .onChildren()
      .assertCountEquals(FormFactor.entries.size)

    composeTestRule.onNodeWithText(FormFactor.Mobile.displayName).assertExists().assertIsFocused()

    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid)
      .onChildren()
      .assertCountEquals(FormFactor.Mobile.getProjectTemplates().size)
  }

  @Test
  fun showSelectedFormFactor() = runTest {
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier)
    val selectedFormFactor = FormFactor.Wear
    model.getAndroidProjectEntries()

    composeTestRule.setContent { ChooseAndroidProjectStepUI(model = model) }

    composeTestRule.onNodeWithText(selectedFormFactor.displayName).assertExists().performClick()

    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid)
      .onChildren()
      .assertCountEquals(selectedFormFactor.getProjectTemplates().size)
  }

  @Test
  fun showPreviouslySelectedTemplate() = runTest {
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier)
    val mobileTemplates = FormFactor.Mobile.getProjectTemplates()
    model.getAndroidProjectEntries()

    composeTestRule.setContent { ChooseAndroidProjectStepUI(model = model) }

    composeTestRule.onNodeWithText(FormFactor.Mobile.displayName).assertIsFocused()

    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid)
      .onChildren()[mobileTemplates.size - 1]
      .performClick()

    composeTestRule.onNodeWithText(FormFactor.Wear.displayName).performClick()

    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid)
      .performClick()

    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid)
      .onChildren()
      .assertCountEquals(FormFactor.Wear.getProjectTemplates().size)

    composeTestRule.onNodeWithText(FormFactor.Mobile.displayName).performClick()

    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid)
      .onChildren()
      .assertCountEquals(FormFactor.Mobile.getProjectTemplates().size)

    assertEquals(
      mobileTemplates[mobileTemplates.size - 1].name,
      (model.chooseAndroidProjectEntries[0] as FormFactorProjectEntry).selectedTemplate?.name,
    )
  }

  @Test
  fun showNewProjectWizardWithGemini() = runTest {
    StudioFlags.GEMINI_NEW_PROJECT_AGENT.override(true)
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier)
    val fakeGeminiPluginApi =
      object : GeminiPluginApi {
        override val MAX_QUERY_CHARS: Int
          get() = 1

        override fun isAvailable(): Boolean = true

        override fun sendChatQuery(
          project: Project,
          prompt: LlmPrompt,
          displayText: String?,
          requestSource: GeminiPluginApi.RequestSource,
        ) {}

        override fun stageChatQuery(
          project: Project,
          prompt: String,
          requestSource: GeminiPluginApi.RequestSource,
        ) {}
      }

    ExtensionTestUtil.maskExtensions(
      GeminiPluginApi.EP_NAME,
      listOf(fakeGeminiPluginApi),
      projectRule.disposable,
    )
    model.getAndroidProjectEntries()

    composeTestRule.setContent { ChooseAndroidProjectStepUI(model = model) }

    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.LeftPanel.column)
      .onChildren()
      .assertCountEquals(FormFactor.entries.size + 1)
      .onLast()
      .performClick()

    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.geminiTextArea)
      .assertExists()
      .assertTextContains("Ask Gemini to create a to-do list app")
  }

  @Test
  fun showNewProjectAgentErrorOnNoAiAvailability() = runTest {
    StudioFlags.GEMINI_NEW_PROJECT_AGENT.override(true)
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier)
    model.getAndroidProjectEntries()

    composeTestRule.setContent { ChooseAndroidProjectStepUI(model = model) }

    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.LeftPanel.column)
      .onChildren()
      .assertCountEquals(FormFactor.entries.size + 1)
      .onLast()
      .performClick()

    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.geminiErrorText)
      .assertExists()
      .assertTextContains("Log in to Gemini to create a new project.")
  }
}
