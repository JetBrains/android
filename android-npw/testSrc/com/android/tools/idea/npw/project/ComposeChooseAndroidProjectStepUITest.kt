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

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.model.NewProjectModuleModel
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.android.tools.idea.npw.project.ChooseAndroidProjectStep.Companion.getProjectTemplates
import com.android.tools.idea.npw.template.PluginPromotionTemplate
import com.android.tools.idea.npw.template.WizardPluginPromotionTemplateProvider
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.Thumb
import com.android.tools.idea.wizard.template.WizardTemplateProvider
import com.android.tools.idea.wizard.template.WizardUiContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import java.awt.image.BufferedImage
import java.io.File
import java.util.function.Supplier
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ComposeChooseAndroidProjectStepUITest {
  @get:Rule val composeTestRule = createStudioComposeTestRule()
  @get:Rule val projectRule = ProjectRule()

  private var originalNewTemplateEngineFlag: Boolean = false

  @Before
  fun setUp() {
    originalNewTemplateEngineFlag = StudioFlags.NPW_NEW_TEMPLATE_ENGINE.get()
    StudioFlags.NPW_NEW_TEMPLATE_ENGINE.override(false)
  }

  @After
  fun tearDown() {
    StudioFlags.NPW_NEW_TEMPLATE_ENGINE.override(originalNewTemplateEngineFlag)
  }

  @Before
  fun setUpProviders() {
    val point = ExtensionPointName<WizardTemplateProvider>("com.android.tools.idea.wizard.template.wizardTemplateProvider")
    val area = ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl
    if (!area.hasExtensionPoint(point)) {
      area.registerExtensionPoint(point, WizardTemplateProvider::class.java.name, ExtensionPoint.Kind.INTERFACE, projectRule.project)
    }
    val templates = listOf(
      template("Empty Activity", FormFactor.Mobile),
      template("Other Activity", FormFactor.Mobile),
      template("Wear Activity", FormFactor.Wear),
    )
    val provider = object : WizardTemplateProvider() {
      override fun getTemplates() = templates
    }
    ExtensionTestUtil.maskExtensions(point, listOf(provider), projectRule.project)

    if (!area.hasExtensionPoint(PROMOTION_EP_NAME)) {
      area.registerExtensionPoint(PROMOTION_EP_NAME, WizardPluginPromotionTemplateProvider::class.java.name, ExtensionPoint.Kind.INTERFACE, projectRule.project)
    }
  }

  private fun template(name: String, formFactor: FormFactor): Template = mock<Template>().also {
    whenever(it.name).thenReturn(name)
    whenever(it.formFactor).thenReturn(formFactor)
    whenever(it.uiContexts).thenReturn(listOf(WizardUiContext.NewProject))
    whenever(it.flags).thenReturn(emptyList())
    whenever(it.thumb()).thenReturn(Thumb.NoThumb)
  }

  @Test
  fun keyboardMovesAcrossRowsAndStopsAtTheGridBoundary() {
    val items = (1..4).map { TemplateGridItem(template("Template $it", FormFactor.Mobile)) }
    val entry = FormFactorProjectEntry("Test", items, items.first())
    composeTestRule.setContent { Box(Modifier.size(384.dp, 600.dp)) { entry.AndroidProjectEntryDetails() } }
    composeTestRule.onNodeWithText("Template 1").performClick()
    val grid = composeTestRule.onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid)
    listOf(Key.DirectionRight to 1, Key.DirectionDown to 3, Key.DirectionLeft to 2, Key.DirectionUp to 0,
           Key.DirectionLeft to 0, Key.DirectionUp to 0).forEach { (key, index) ->
      grid.performKeyInput { pressKey(key) }
      composeTestRule.runOnIdle { assertEquals(items[index], entry.selectedGridItem) }
    }
  }

  @Test
  fun entryLoadingRunsOffTheUiThread() = runTest {
    val model = ChooseAndroidProjectStepModel(Supplier {
      assertFalse(SwingUtilities.isEventDispatchThread())
      listOf(FormFactor.Mobile)
    })
    withContext(Dispatchers.Main) { model.getAndroidProjectEntries() }
    assertFalse(model.isLoading)
    assertEquals(FormFactor.Mobile.displayName, (model.selectedAndroidProjectEntry as FormFactorProjectEntry).formFactorTitle)
  }

  @Test
  fun defaultSelectionIsOneOfTheDisplayedItems() = runTest {
    val model = ChooseAndroidProjectStepModel(Supplier { listOf(FormFactor.Mobile) })
    model.getAndroidProjectEntries()
    val entry = model.selectedAndroidProjectEntry as FormFactorProjectEntry
    assertEquals("Empty Activity", entry.selectedGridItem?.title)
    assertEquals(true, entry.gridItems.any { it === entry.selectedGridItem })
  }

  @Test
  fun doubleClickSelectsTemplateBeforeAdvancing() = runTest {
    val model = ChooseAndroidProjectStepModel(Supplier { listOf(FormFactor.Mobile) })
    model.getAndroidProjectEntries()
    var advances = 0
    model.onGridItemDoubleClick = {
      val entry = model.selectedAndroidProjectEntry as FormFactorProjectEntry
      assertEquals("Other Activity", entry.selectedGridItem?.title)
      advances++
    }
    composeTestRule.setContent { ChooseAndroidProjectStepUI(model) }
    composeTestRule.onNodeWithText("Other Activity").performMouseInput { doubleClick() }
    composeTestRule.runOnIdle { assertEquals(1, advances) }
  }

  @Test
  fun showMobileFormFactorAsDefaultFocus() = runTest {
    StudioFlags.GEMINI_NEW_PROJECT_AGENT.override(false)
    StudioFlags.IMPORT_PROJECT_ENABLED.override(false)
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier)
    model.getAndroidProjectEntries()

    composeTestRule.setContent { ChooseAndroidProjectStepUI(model = model) }

    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.LeftPanel.column)
      .assertExists()
      .onChildren()
      .assertCountEquals(FormFactor.entries.size - 1 /* AiGlasses is not included */)

    composeTestRule.onNodeWithText(FormFactor.Mobile.displayName).assertExists().assertIsFocused()

    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid)
      .onChildren()
      .assertCountEquals(FormFactor.Mobile.getProjectTemplates().size)
  }

  @Test
  fun showMobileAsDefaultSelectionWhenGeminiNewProjectAgentIsEnabled() = runTest {
    StudioFlags.GEMINI_NEW_PROJECT_AGENT.override(true)
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier)
    model.getAndroidProjectEntries()

    val mobileEntry =
      model.chooseAndroidProjectEntries.filterIsInstance<FormFactorProjectEntry>().first {
        it.formFactorTitle == FormFactor.Mobile.toString()
      }
    assertEquals(mobileEntry, model.selectedAndroidProjectEntry)
  }

  @Test
  fun allowPreselectingEntry() = runTest {
    StudioFlags.GEMINI_NEW_PROJECT_AGENT.override(true)
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier)
    val customEntry =
      object : ChooseAndroidProjectEntry {
        @Composable override fun AndroidProjectListEntry(isSelected: Boolean, isFocused: Boolean) {}

        @Composable override fun AndroidProjectEntryDetails() {}

        override val canGoForward = mutableStateOf(true)

        override fun onProceeding(newProjectModuleModel: NewProjectModuleModel, model: NewProjectModel) {}
      }
    model.updateSelectedCell(customEntry)
    model.getAndroidProjectEntries()

    assertEquals(customEntry, model.selectedAndroidProjectEntry)
  }

  @Test
  fun allowPreselectingTargetWithInitialTargetParam() = runTest {
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier, initialTarget = "Wear")
    model.getAndroidProjectEntries()

    val wearEntry =
      model.chooseAndroidProjectEntries.filterIsInstance<FormFactorProjectEntry>().first {
        it.formFactorTitle == FormFactor.Wear.toString()
      }
    assertEquals(wearEntry, model.selectedAndroidProjectEntry)
  }

  @Test
  fun allowPreselectingTargetWithInitialTargetParamForCustomEntry() = runTest {
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier, initialTarget = "AI")
    val customAiEntry =
      object : ChooseAndroidProjectEntry {
        override val entryId: String = "AI"

        @Composable override fun AndroidProjectListEntry(isSelected: Boolean, isFocused: Boolean) {}

        @Composable override fun AndroidProjectEntryDetails() {}

        override val canGoForward = mutableStateOf(true)

        override fun onProceeding(newProjectModuleModel: NewProjectModuleModel, model: NewProjectModel) {}
      }
    model.updateSelectedCell(customAiEntry)
    model.getAndroidProjectEntries()

    assertEquals("AI", model.selectedAndroidProjectEntry?.entryId)
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

    composeTestRule.onNodeWithText(FormFactor.Mobile.displayName).performClick()

    composeTestRule.onNodeWithText("Other Activity").performClick()
    composeTestRule.runOnIdle {
      val entry = model.selectedAndroidProjectEntry as FormFactorProjectEntry
      assertEquals("Other Activity", entry.selectedGridItem?.title)
    }

    composeTestRule.onNodeWithText(FormFactor.Wear.displayName).performClick()

    composeTestRule.onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid).performClick()

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
      mobileTemplates[mobileTemplates.size - 1].title,
      (model.chooseAndroidProjectEntries.filterIsInstance<FormFactorProjectEntry>().first()).selectedGridItem?.title,
    )
  }

  @Test
  fun showSelectedProjectTypeOnBackNavigation() = runTest {
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier)
    model.getAndroidProjectEntries()
    var showUi by mutableStateOf(true)
    composeTestRule.setContent {
      if (showUi) {
        ChooseAndroidProjectStepUI(model = model)
      }
    }

    // Select Wear OS
    val wearOsDisplayName = FormFactor.Wear.displayName
    composeTestRule.onNodeWithText(wearOsDisplayName).performClick()

    // Simulate navigating next then back by recomposing the UI
    showUi = false
    composeTestRule.awaitIdle()
    showUi = true
    composeTestRule.awaitIdle()

    // Verify Wear OS is still selected and focused in the UI
    composeTestRule.onNodeWithText(wearOsDisplayName).assertIsFocused()

    // And verify the templates are for Wear OS
    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid)
      .onChildren()
      .assertCountEquals(FormFactor.Wear.getProjectTemplates().size)
  }

  @Test
  fun canSelectTemplatesAcrossRowsAfterSelectingNoActivity() = runTest {
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier)
    model.getAndroidProjectEntries()

    composeTestRule.setContent { ChooseAndroidProjectStepUI(model = model) }

    // Switch to Wear OS
    composeTestRule.onNodeWithText(FormFactor.Wear.displayName).performClick()

    val templateGrid = composeTestRule.onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid)
    val wearTemplates = FormFactor.Wear.getProjectTemplates()
    assertTrue(wearTemplates.size >= 2, "Wear OS must have at least 2 templates")

    // Click 'No Activity' (index 0 in Row 1)
    templateGrid.onChildren()[0].performClick()
    assertEquals("No Activity", (model.selectedAndroidProjectEntry as FormFactorProjectEntry).selectedGridItem?.title)

    // Click subsequent template in the next row / index 1
    templateGrid.onChildren()[1].performClick()
    assertEquals(wearTemplates[1].title, (model.selectedAndroidProjectEntry as FormFactorProjectEntry).selectedGridItem?.title)

    // Also verify in Mobile (Phone and Tablet)
    composeTestRule.onNodeWithText(FormFactor.Mobile.displayName).performClick()
    val mobileTemplates = FormFactor.Mobile.getProjectTemplates()
    assertTrue(mobileTemplates.size >= 3, "Mobile must have at least 3 templates")

    // In Phone, auto focus lands on default (e.g. Empty Compose Activity at index 1). Verify we can click index 0 (No Activity) and index 2
    // (Gemini API / next item).
    templateGrid.onChildren()[0].performClick()
    assertEquals("No Activity", (model.selectedAndroidProjectEntry as FormFactorProjectEntry).selectedGridItem?.title)

    templateGrid.onChildren()[2].performClick()
    assertEquals(mobileTemplates[2].title, (model.selectedAndroidProjectEntry as FormFactorProjectEntry).selectedGridItem?.title)
  }

  @Test
  fun showPluginPromotionTemplateInGrid() = runTest {
    StudioFlags.GEMINI_NEW_PROJECT_AGENT.override(false)
    registerPromotionTemplates(FakePluginPromotionTemplate(PROMOTION_NAME, PROMOTION_PLUGIN_ID, FormFactor.Mobile))
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier)
    model.getAndroidProjectEntries()

    composeTestRule.setContent { ChooseAndroidProjectStepUI(model = model) }

    composeTestRule.onNodeWithText(FormFactor.Mobile.displayName).assertExists().performClick()

    // The Mobile template grid now includes the registered promotion template.
    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid)
      .onChildren()
      .assertCountEquals(FormFactor.Mobile.getProjectTemplates().size)

    composeTestRule.onNodeWithText(PROMOTION_NAME, substring = true).assertExists()
  }

  @Test
  fun selectingPluginPromotionTemplateUpdatesSelectedTemplate() = runTest {
    StudioFlags.GEMINI_NEW_PROJECT_AGENT.override(false)
    StudioFlags.IMPORT_PROJECT_ENABLED.override(false)
    registerPromotionTemplates(FakePluginPromotionTemplate(PROMOTION_NAME, PROMOTION_PLUGIN_ID, FormFactor.Mobile))
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier)
    val mobileTemplateInfos = FormFactor.Mobile.getProjectTemplates()
    model.getAndroidProjectEntries()

    composeTestRule.setContent { ChooseAndroidProjectStepUI(model = model) }

    composeTestRule.onNodeWithText(FormFactor.Mobile.displayName).assertExists().performClick()

    // The promotion template is appended after the regular templates, so it is the last cell.
    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid)
      .onChildren()[mobileTemplateInfos.size - 1]
      .performClick()

    val selectedGridItem = (model.chooseAndroidProjectEntries[0] as FormFactorProjectEntry).selectedGridItem
    assertEquals(PROMOTION_PLUGIN_ID, (selectedGridItem as PluginPromotionGridItem).pluginId)
  }

  @Test
  fun pluginPromotionTemplateAppearsOnlyForItsFormFactor() = runTest {
    StudioFlags.GEMINI_NEW_PROJECT_AGENT.override(false)
    registerPromotionTemplates(FakePluginPromotionTemplate(PROMOTION_NAME, PROMOTION_PLUGIN_ID, FormFactor.Wear))
    val formFactorSupplier = Supplier<List<FormFactor>> { FormFactor.entries }
    val model = ChooseAndroidProjectStepModel(formFactorSupplier)
    model.getAndroidProjectEntries()

    composeTestRule.setContent { ChooseAndroidProjectStepUI(model = model) }

    // The Mobile grid must not show the Wear-only promotion template.
    composeTestRule.onNodeWithText(FormFactor.Mobile.displayName).assertExists().performClick()
    composeTestRule.onNodeWithText(PROMOTION_NAME, substring = true).assertDoesNotExist()

    // Switching to Wear reveals the promotion template in its grid.
    composeTestRule.onNodeWithText(FormFactor.Wear.displayName).performClick()

    composeTestRule
      .onNodeWithTag(ChooseAndroidProjectStepLayoutTags.RightPanel.templateGrid)
      .onChildren()
      .assertCountEquals(FormFactor.Wear.getProjectTemplates().size)

    composeTestRule.onNodeWithText(PROMOTION_NAME, substring = true).assertExists()
  }

  private fun registerPromotionTemplates(vararg templates: PluginPromotionTemplate) {
    ExtensionTestUtil.maskExtensions(
      PROMOTION_EP_NAME,
      listOf(FakeWizardPluginPromotionTemplateProvider(templates.toList())),
      projectRule.disposable,
    )
  }
}

private const val PROMOTION_NAME = "Test Promotion Plugin"
private const val PROMOTION_PLUGIN_ID = "com.example.testpromotion"

private val PROMOTION_EP_NAME =
  ExtensionPointName<WizardPluginPromotionTemplateProvider>("com.android.tools.idea.npw.template.wizardPluginPromotionTemplateProvider")

private val fakeThumbUrl by lazy {
  val file = File.createTempFile("fake_thumb", ".png").apply { deleteOnExit() }
  ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", file)
  file.toURI().toURL()
}

private class FakePluginPromotionTemplate(override val name: String, override val pluginId: String, override val formFactor: FormFactor) :
  PluginPromotionTemplate {
  override fun thumb(): Thumb = Thumb { fakeThumbUrl }
}

private class FakeWizardPluginPromotionTemplateProvider(private val templates: List<PluginPromotionTemplate>) :
  WizardPluginPromotionTemplateProvider() {
  override fun getTemplates(): List<PluginPromotionTemplate> = templates
}
