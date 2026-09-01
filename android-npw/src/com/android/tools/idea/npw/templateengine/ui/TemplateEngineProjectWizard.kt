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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.template.engine.TemplateDefinition
import com.android.tools.adtui.compose.ComposeWizard
import com.android.tools.adtui.compose.LocalWizardDialogScope
import com.android.tools.adtui.compose.WizardAction
import com.android.tools.adtui.compose.WizardPageScope
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.model.NewProjectModuleModel
import com.android.tools.idea.npw.project.ChooseAndroidProjectEntry
import com.android.tools.idea.npw.templateengine.WizardConstants
import com.android.tools.idea.npw.templateengine.importer.ProjectImporter
import com.android.tools.idea.npw.templateengine.services.ProjectGenerationService
import com.android.tools.idea.npw.templateengine.services.TemplateEngineProjectParameters
import com.android.tools.idea.npw.templateengine.services.TemplateRegistryService
import com.android.tools.idea.npw.templateengine.viewmodel.ChooseProjectViewModel
import com.android.tools.idea.npw.templateengine.viewmodel.ConfigureProjectViewModel
import com.android.tools.idea.wizard.ui.WizardUtils
import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

class TemplateEngineProjectWizard(private val project: Project?) {
  private val dialog =
    ComposeWizard(
      project = project,
      title = IdeCoreBundle.message("title.new.project"),
      preferredSize = WizardConstants.PREFERRED_DIALOG_SIZE,
      minimumSize = WizardConstants.MINIMUM_DIALOG_SIZE,
      initialPage = { TemplateEngineChooseProjectPage(project) },
    )

  fun show() {
    dialog.show()
  }
}

@Composable
fun WizardPageScope.TemplateEngineChooseProjectPage(project: Project?) {
  val registry = getOrCreateState { TemplateRegistryService.getInstance() }
  var isLoading by remember { mutableStateOf(registry.getTemplateDefinitions().isEmpty()) }
  var loadError by remember { mutableStateOf<String?>(null) }

  if (isLoading) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      if (loadError != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(text = "Failed to load templates: $loadError", color = JewelTheme.globalColors.text.error)
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedButton(onClick = { loadError = null }) { Text("Retry") }
        }
      } else {
        CircularProgressIndicator()
      }
    }
    LaunchedEffect(isLoading, loadError) {
      if (isLoading && loadError == null) {
        try {
          withContext(Dispatchers.IO) { registry.loadTemplatesAndResources() }
          isLoading = false
        } catch (e: Exception) {
          loadError = e.localizedMessage ?: "Unknown error"
        }
      }
    }
  } else {
    val chooseProjectViewModel = getOrCreateState { ChooseProjectViewModel(registry) }

    TemplateEngineChooseProjectStep(chooseProjectViewModel)

    val selectedEntry = chooseProjectViewModel.selectedCategory
    val canProceed = selectedEntry?.canGoForward?.value ?: false
    nextActionName = "Next"
    nextAction =
      if (canProceed && selectedEntry != null) {
        if (selectedEntry is TemplateEngineTemplateGridProjectEntry) {
          when (val selectedItem = selectedEntry.selectedItem) {
            is TemplateGalleryItem.Standard ->
              createConfigurePageAction(project, selectedItem.definition, selectedEntry.formFactor, selectedEntry)
            is TemplateGalleryItem.Promotion -> WizardAction { selectedItem.spec.onClickAction(emptyMap()) }
            else -> WizardAction.Disabled
          }
        } else {
          val aiTemplateName = StudioFlags.NPW_AI_STARTER_TEMPLATE.get()
          val templateToUse =
            registry.getTemplateDefinitions().firstOrNull { it.name == aiTemplateName || it.metadata.shortName == aiTemplateName }
              ?: registry.getTemplateDefinitions().firstOrNull()
          if (templateToUse != null) {
            createConfigurePageAction(project, templateToUse, FormFactor.MOBILE, selectedEntry)
          } else {
            WizardAction.Disabled
          }
        }
      } else {
        WizardAction.Disabled
      }

    val dialogScope = LocalWizardDialogScope.current
    if (selectedEntry is TemplateEngineTemplateGridProjectEntry) {
      selectedEntry.onTemplateDoubleClick = {
        if (nextAction.enabled) {
          nextAction.action?.let { with(dialogScope) { it() } }
        }
      }
    }
  }
}

private fun WizardPageScope.createConfigurePageAction(
  project: Project?,
  templateToUse: TemplateDefinition,
  formFactor: FormFactor,
  selectedEntry: ChooseAndroidProjectEntry,
): WizardAction = WizardAction {
  val defaultMinSdk = templateToUse.metadata.arguments.find { it.id == "minSdk" }?.defaultValue
  val configureProjectViewModel = getOrCreateState {
    ConfigureProjectViewModel(WizardUtils.getProjectLocationParent().toPath(), scope = coroutineScope)
  }
  configureProjectViewModel.updateTemplate(templateToUse.name, templateToUse.metadata.shortName, formFactor, defaultMinSdk)

  pushPage { TemplateEngineConfigureProjectPage(project, templateToUse, selectedEntry, configureProjectViewModel) }
}

@Composable
fun WizardPageScope.TemplateEngineConfigureProjectPage(
  project: Project?,
  template: TemplateDefinition,
  entry: ChooseAndroidProjectEntry,
  configureProjectViewModel: ConfigureProjectViewModel,
) {
  TemplateEngineConfigureProjectStep(configureProjectViewModel)

  nextActionName = "Finish"
  nextAction =
    if (configureProjectViewModel.isFinishEnabled) {
      WizardAction {
        val projectModel = NewProjectModel()
        val projectModuleModel = NewProjectModuleModel(projectModel)
        entry.onProceeding(projectModuleModel, projectModel)

        val prompt = projectModel.prompt.get()
        val attachments = projectModel.imageAttachments.get()
        val modelId = projectModel.modelId.get().takeIf { it.isNotBlank() }

        val params =
          TemplateEngineProjectParameters(
            name = configureProjectViewModel.name,
            packageName = configureProjectViewModel.packageName,
            location = configureProjectViewModel.location,
            minSdk = configureProjectViewModel.minSdk,
          )
        val importer = ProjectImporter.createImporter(template.metadata)
        close()
        ProjectGenerationService.getInstance().generateProject(project, params, template, importer) { createdProject ->
          if (createdProject != null && StudioFlags.GEMINI_NEW_PROJECT_AGENT.get() && prompt.isNotBlank()) {
            ToolWindowManager.getInstance(createdProject).invokeLater {
              GeminiPluginApi.getInstance().launchNewProjectAgent(createdProject, prompt, attachments, modelId)
            }
          }
        }
      }
    } else {
      WizardAction.Disabled
    }
}
