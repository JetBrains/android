/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.npw.templateengine.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.template.engine.TemplateDefinition
import com.android.tools.adtui.compose.TestComposeWizard
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.project.AndroidProjectEntryProvider
import com.android.tools.idea.npw.templateengine.services.TemplateRegistryService
import com.android.tools.idea.npw.templateengine.viewmodel.ChooseProjectViewModel
import com.android.tools.idea.npw.templateengine.viewmodel.ConfigureProjectViewModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.replaceService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class TemplateEngineWizardUITest {

  @get:Rule val appRule = ApplicationRule()

  @get:Rule val composeTestRule = createStudioComposeTestRule()

  private lateinit var registry: TemplateRegistryService
  private lateinit var testFolder: Path
  private lateinit var disposable: Disposable
  private var originalGeminiAgentFlag: Boolean = false
  private var originalNewTemplateEngineFlag: Boolean = false

  private val dummyTemplate =
    TemplateEngineTestUtils.createTestTemplateDefinition(name = "Dummy Template", shortName = "dummy-template", tags = listOf("project"))

  @Before
  fun setUp() {
    disposable = Disposer.newDisposable()
    originalGeminiAgentFlag = StudioFlags.GEMINI_NEW_PROJECT_AGENT.get()
    StudioFlags.GEMINI_NEW_PROJECT_AGENT.override(false)
    originalNewTemplateEngineFlag = StudioFlags.NPW_NEW_TEMPLATE_ENGINE.get()
    StudioFlags.NPW_NEW_TEMPLATE_ENGINE.override(true)
    registry = mock<TemplateRegistryService>()
    whenever(registry.getTemplateDefinitions()).thenReturn(listOf(dummyTemplate))
    whenever(registry.getPromotionCards()).thenReturn(emptyList())
    whenever(registry.getContributorExternalTemplates()).thenReturn(emptyList())
    ApplicationManager.getApplication().replaceService(TemplateRegistryService::class.java, registry, disposable)
    ExtensionTestUtil.maskExtensions(AndroidProjectEntryProvider.EP_NAME, listOf(TemplateGridProjectEntryProvider()), disposable)
    testFolder = Files.createTempDirectory("wizard_ui_test")
  }

  @After
  fun tearDown() {
    StudioFlags.GEMINI_NEW_PROJECT_AGENT.override(originalGeminiAgentFlag)
    StudioFlags.NPW_NEW_TEMPLATE_ENGINE.override(originalNewTemplateEngineFlag)
    Disposer.dispose(disposable)
    testFolder.deleteRecursively()
  }

  @Test
  fun testWizardFlowUI() {
    val chooseVM = ChooseProjectViewModel(registry)

    val mockTarget =
      mock<IAndroidTarget>().apply {
        whenever(this.isPlatform).thenReturn(true)
        whenever(this.version).thenReturn(AndroidVersion(34))
        whenever(this.versionName).thenReturn("Android 14")
      }
    val stubVersionsInfo = AndroidVersionsInfo { arrayOf(mockTarget) }
    val configureVM = ConfigureProjectViewModel(testFolder, stubVersionsInfo)

    val wizard = TestComposeWizard {
      getOrCreateState { chooseVM }
      getOrCreateState { configureVM }
      TemplateEngineChooseProjectPage(null)
    }

    composeTestRule.setContent { wizard.Content() }
    composeTestRule.waitForIdle()

    // 1. Choose Step
    composeTestRule.onNodeWithText("Dummy Template", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Dummy Template", useUnmergedTree = true).performClick()

    // Go to next step by triggering nextAction
    assert(wizard.nextAction.enabled)
    composeTestRule.runOnUiThread { wizard.performAction(wizard.nextAction) }
    composeTestRule.waitForIdle()

    // 2. Configure Step
    composeTestRule.onNodeWithText("Name").assertIsDisplayed()
    composeTestRule.onNodeWithText("Package name").assertIsDisplayed()

    // Check initial values
    composeTestRule.onNodeWithText("My Application").assertIsDisplayed()

    // Edit Name
    composeTestRule.onNodeWithText("My Application").performTextReplacement("New App")
    composeTestRule.waitForIdle()

    // Verify UI updated
    composeTestRule.onNodeWithText("New App").assertIsDisplayed()
    composeTestRule.onNodeWithText("com.example.newapp").assertIsDisplayed()
    composeTestRule.onNodeWithText(testFolder.resolve("NewApp").toAbsolutePath().toString()).assertIsDisplayed()
  }

  @Test
  fun testWizardFlowUIWithAsyncLoading() {
    val templates = mutableListOf<TemplateDefinition>()
    val asyncRegistry =
      mock<TemplateRegistryService>().apply {
        whenever(this.getTemplateDefinitions()).thenAnswer { templates }
        whenever(this.loadTemplatesAndResources()).thenAnswer {
          templates.add(dummyTemplate)
          Unit
        }
      }
    val chooseVM = ChooseProjectViewModel(asyncRegistry)
    ApplicationManager.getApplication().replaceService(TemplateRegistryService::class.java, asyncRegistry, disposable)

    val mockTarget =
      mock<IAndroidTarget>().apply {
        whenever(this.isPlatform).thenReturn(true)
        whenever(this.version).thenReturn(AndroidVersion(34))
        whenever(this.versionName).thenReturn("Android 14")
      }
    val stubVersionsInfo = AndroidVersionsInfo { arrayOf(mockTarget) }
    val configureVM = ConfigureProjectViewModel(testFolder, stubVersionsInfo)

    val wizard = TestComposeWizard {
      getOrCreateState { chooseVM }
      getOrCreateState { configureVM }
      TemplateEngineChooseProjectPage(null)
    }

    composeTestRule.setContent { wizard.Content() }

    // Wait for LaunchedEffect to finish loading templates
    composeTestRule.waitForIdle()

    // Verify template was loaded and displayed
    composeTestRule.onNodeWithText("Dummy Template", useUnmergedTree = true).assertIsDisplayed()

    // Verify Next action is now enabled
    assert(wizard.nextAction.enabled)
  }
}
