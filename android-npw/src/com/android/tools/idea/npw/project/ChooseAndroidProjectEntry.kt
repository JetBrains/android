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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.model.NewProjectModuleModel
import com.android.tools.idea.npw.template.installPromotedPlugin
import com.android.tools.idea.wizard.model.ModelWizard.ActionCancellationException
import com.android.tools.idea.wizard.template.Template.NoActivity
import com.android.tools.idea.wizard.template.WizardUiContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName

interface ChooseAndroidProjectEntry {
  val entryId: String
    get() = javaClass.simpleName

  @Composable fun AndroidProjectListEntry(isSelected: Boolean, isFocused: Boolean)

  @Composable fun AndroidProjectEntryDetails()

  val canGoForward: State<Boolean>

  fun onProceeding(newProjectModuleModel: NewProjectModuleModel, model: NewProjectModel)

  fun onShowing(model: NewProjectModel) {}
}

// FormFactor entries are automatically included and don't implement AndroidProjectEntryProvider
interface AndroidProjectEntryProvider {
  fun getProjectEntries(): List<ChooseAndroidProjectEntry>

  companion object {
    val EP_NAME = ExtensionPointName.create<AndroidProjectEntryProvider>("com.android.androidProjectEntryProvider")

    fun getAllProjectEntries(): List<ChooseAndroidProjectEntry> {
      if (!ApplicationManager.getApplication().extensionArea.hasExtensionPoint(EP_NAME)) return emptyList()
      return buildList {
        EP_NAME.forEachExtensionSafe { provider ->
          addAll(provider.getProjectEntries())
        }
      }
    }
  }
}

class FormFactorProjectEntry(
  val formFactorTitle: String,
  val gridItems: List<GridItem>,
  selectedGridItem: GridItem?,
  val onGridItemDoubleClick: () -> Unit = {},
) : ChooseAndroidProjectEntry {
  override val entryId: String
    get() = formFactorTitle

  var selectedGridItem by mutableStateOf(selectedGridItem)

  @Composable
  override fun AndroidProjectListEntry(isSelected: Boolean, isFocused: Boolean) {
    ProjectEntryListCell(formFactorTitle, null, isSelected, isFocused)
  }

  @Composable
  override fun AndroidProjectEntryDetails() {
    TemplateGrid(
      gridItems = gridItems,
      selectedGridItem = selectedGridItem,
      onGridItemClick = { item -> selectedGridItem = item },
      onGridItemDoubleClick = { item ->
        selectedGridItem = item
        onGridItemDoubleClick()
      },
    )
  }

  override val canGoForward = derivedStateOf { selectedGridItem != null }

  override fun onProceeding(newProjectModuleModel: NewProjectModuleModel, model: NewProjectModel) {
    when (val item = selectedGridItem) {
      is TemplateGridItem -> {
        newProjectModuleModel.formFactor.set(item.formFactor)
        newProjectModuleModel.newRenderTemplate.setNullableValue(item.template)
        val hasExtraDetailStep = item.uiContexts.contains(WizardUiContext.NewProjectExtraDetail)
        newProjectModuleModel.extraRenderTemplateModel.newTemplate = if (hasExtraDetailStep) item.template else NoActivity
      }
      is PluginPromotionGridItem -> {
        installPromotedPlugin(item.pluginId, item.template.name, item.formFactor)
        throw ActionCancellationException(null, null)
      }
      null -> {}
    }
  }
}
