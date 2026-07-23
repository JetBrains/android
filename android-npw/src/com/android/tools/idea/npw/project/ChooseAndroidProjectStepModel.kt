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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.android.tools.idea.npw.project.ChooseAndroidProjectStep.Companion.getProjectTemplates
import com.android.tools.idea.npw.startup.PromotedTemplate
import com.android.tools.idea.npw.startup.PromotionTemplateStateService
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Template
import java.util.function.Supplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChooseAndroidProjectStepModel(private val formFactorSupplier: Supplier<List<FormFactor>>, private val initialTarget: String? = null) {
  var onGridItemDoubleClick: () -> Unit = {}

  var chooseAndroidProjectEntries by mutableStateOf<List<ChooseAndroidProjectEntry>>(emptyList())
    private set

  var isLoading by mutableStateOf(false)
    private set

  var selectedAndroidProjectEntry by mutableStateOf<ChooseAndroidProjectEntry?>(null)
    private set

  val canGoForward = snapshotFlow {
    !isLoading &&
      chooseAndroidProjectEntries.isNotEmpty() &&
      selectedAndroidProjectEntry != null &&
      selectedAndroidProjectEntry?.canGoForward?.value ?: false
  }

  suspend fun getAndroidProjectEntries() {
    isLoading = true
    val promotedTemplate = PromotionTemplateStateService.getInstance().consumePromotedTemplate()
    val entries = mutableListOf<ChooseAndroidProjectEntry>()
    var hasMatchedPromotedTemplate = false
    withContext(Dispatchers.IO) {
      entries.addAll(AndroidProjectEntryProvider.getAllProjectEntries())
      formFactorSupplier.get().forEach { formFactor ->
        if (formFactor != FormFactor.AiGlasses) {
          val entry = createFormFactorEntry(formFactor, promotedTemplate)
          if (formFactor == FormFactor.Mobile && selectedAndroidProjectEntry == null && !hasMatchedPromotedTemplate) {
            selectedAndroidProjectEntry = entry
          }
          if (!hasMatchedPromotedTemplate && promotedTemplate?.matches(entry.selectedGridItem, formFactor) == true) {
            selectedAndroidProjectEntry = entry
            hasMatchedPromotedTemplate = true
          }
          entries.add(entry)
        }
      }
    }

    if (!initialTarget.isNullOrBlank()) {
      entries.firstOrNull { matchesTarget(it, initialTarget) }?.let { selectedAndroidProjectEntry = it }
    }

    chooseAndroidProjectEntries = entries
    isLoading = false
  }

  private fun matchesTarget(entry: ChooseAndroidProjectEntry, target: String): Boolean {
    return entry.entryId.contains(target, ignoreCase = true)
  }

  fun updateSelectedCell(entry: ChooseAndroidProjectEntry?) {
    selectedAndroidProjectEntry = entry
  }

  private fun getDefaultSelectedGridItem(
    gridItems: List<GridItem>,
    promotedTemplate: PromotedTemplate?,
    currentFormFactor: FormFactor,
  ): GridItem? {
    if (promotedTemplate != null) {
      if (promotedTemplate.formFactor == currentFormFactor) {
        gridItems.filterIsInstance<TemplateGridItem>().firstOrNull { it.template.name == promotedTemplate.name }?.let { return it }
      }
      gridItems.filterIsInstance<PluginPromotionGridItem>().firstOrNull { it.pluginId == promotedTemplate.pluginId }?.let { return it }
    }
    return gridItems.firstOrNull { it.getTitle() == "Empty Activity" }
      ?: gridItems.filterIsInstance<TemplateGridItem>().firstOrNull { it.template != Template.NoActivity }
      ?: gridItems.firstOrNull()
  }

  private fun createFormFactorEntry(formFactor: FormFactor, promotedTemplate: PromotedTemplate?): FormFactorProjectEntry {
    val gridItems = formFactor.getProjectTemplates()
    return FormFactorProjectEntry(
      formFactor.toString(),
      gridItems,
      getDefaultSelectedGridItem(gridItems, promotedTemplate, formFactor),
      onGridItemDoubleClick = { onGridItemDoubleClick() },
    )
  }
}

private fun PromotedTemplate.matches(gridItem: GridItem?, formFactor: FormFactor): Boolean {
  val promotionMatches = gridItem is PluginPromotionGridItem && gridItem.pluginId == pluginId
  val templateMatches = gridItem is TemplateGridItem && gridItem.template.name == name && formFactor == this.formFactor
  return promotionMatches || templateMatches
}
