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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.model.NewProjectModuleModel
import com.android.tools.idea.npw.startup.PromotionTemplateStateService
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.wizard.template.Template.NoActivity
import com.android.tools.idea.wizard.template.WizardUiContext
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable

abstract class ChooseAndroidProjectEntry() {
  @Composable abstract fun AndroidProjectListEntry(isSelected: Boolean, isFocused: Boolean)

  @Composable abstract fun AndroidProjectEntryDetails()

  abstract val canGoForward: State<Boolean>

  /**
   * Applies this entry's selection to the wizard models.
   *
   * @return `true` if the wizard should advance to the next step; `false` if this entry has
   *   already handled the user's action (e.g. launched a plugin installation) and there is no
   *   wizard page to continue to.
   */
  abstract fun onProceeding(
    newProjectModuleModel: NewProjectModuleModel,
    model: NewProjectModel,
  ): Boolean
}

class FormFactorProjectEntry(
  val formFactorTitle: String,
  val templateInfos: List<TemplateInfo>,
  selectedTemplateInfo: TemplateInfo?,
) : ChooseAndroidProjectEntry() {
  var selectedTemplateInfo by mutableStateOf(selectedTemplateInfo)

  @Composable
  override fun AndroidProjectListEntry(isSelected: Boolean, isFocused: Boolean) {
    ListCell(formFactorTitle, null, isSelected, isFocused)
  }

  @Composable
  override fun AndroidProjectEntryDetails() {
    TemplateGrid(
      templateInfos = templateInfos,
      selectedTemplateInfo = selectedTemplateInfo,
      onTemplateClick = { templateInfo -> selectedTemplateInfo = templateInfo },
    )
  }

  override val canGoForward = derivedStateOf { selectedTemplateInfo != null }

  override fun onProceeding(newProjectModuleModel: NewProjectModuleModel, model: NewProjectModel): Boolean =
    when (val templateInfo = selectedTemplateInfo) {
      is NewProjectTemplateInfo -> {
        newProjectModuleModel.formFactor.set(templateInfo.formFactor)
        newProjectModuleModel.newRenderTemplate.setNullableValue(templateInfo.template)
        val hasExtraDetailStep = templateInfo.uiContexts.contains(WizardUiContext.NewProjectExtraDetail)
        newProjectModuleModel.extraRenderTemplateModel.newTemplate =
          if (hasExtraDetailStep) templateInfo.template else NoActivity
        true
      }
      is PluginPromotionTemplateInfo -> {
        installPromotedPlugin(templateInfo.pluginId)
        false // A promotion template has no wizard pages to continue to.
      }
      null -> true
    }

  private fun installPromotedPlugin(pluginId: String) {
    val id = PluginId.getId(pluginId)
    installAndEnable(
      project = null,
      pluginIds = setOf(id),
      showDialog = true,
      selectAlInDialog = true,
      onSuccess = Runnable {
        // checks if the plugin needs a restart and shows restart dialog if it does
        if (InstalledPluginsState.getInstance().wasInstalled(id)) {
          PromotionTemplateStateService.getInstance().requestNpwReopenOnNextStartup()
          PluginManagerConfigurable.shutdownOrRestartApp()
        }
      },
    )
  }
}

class GeminiProjectEntry() : ChooseAndroidProjectEntry() {
  val textFieldState = TextFieldState()

  @Composable
  override fun AndroidProjectListEntry(isSelected: Boolean, isFocused: Boolean) {
    GeminiListCell(isSelected, isFocused)
  }

  @Composable
  override fun AndroidProjectEntryDetails() {
    GeminiRightPanel(textFieldState, GeminiPluginApi.getInstance().isAvailable(), true)
  }

  override val canGoForward = derivedStateOf { textFieldState.text.isNotEmpty() }

  override fun onProceeding(newProjectModuleModel: NewProjectModuleModel, model: NewProjectModel): Boolean {
    val baseTemplateName =
      if (StudioFlags.NPW_ENABLE_ARCHITECTURE_SAMPLE_TEMPLATE.get()) "Architecture Sample"
      else "Empty Activity"
    val templateToUse =
      TemplateResolver.getAllTemplates().firstOrNull { it.name == baseTemplateName }
    newProjectModuleModel.newRenderTemplate.setNullableValue(templateToUse ?: NoActivity)
    model.prompt.set(textFieldState.text.toString())
    return true
  }
}
