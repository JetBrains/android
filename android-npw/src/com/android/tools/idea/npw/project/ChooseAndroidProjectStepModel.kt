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

private fun PromotedTemplate.matches(gridItem: GridItem?, currentFormFactor: FormFactor): Boolean {
  val isPromotedMatch = gridItem is PluginPromotionGridItem && gridItem.pluginId == this.pluginId
  val isRegularMatch = gridItem is TemplateGridItem && gridItem.template.name == this.name && currentFormFactor == this.formFactor
  return isPromotedMatch || isRegularMatch
}

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
    val stateService = PromotionTemplateStateService.getInstance()
    val promotedTemplate = stateService.consumePromotedTemplate()
    val entries = mutableListOf<ChooseAndroidProjectEntry>()
    var hasMatchedPromotedTemplate = false
    withContext(Dispatchers.IO) {
      entries.addAll(AndroidProjectEntryProvider.getAllProjectEntries())
      formFactorSupplier.get().forEach {
        if (it != FormFactor.AiGlasses) {
          val entry = createFormFactorEntry(it, promotedTemplate)
          if (it == FormFactor.Mobile && selectedAndroidProjectEntry == null && !hasMatchedPromotedTemplate) {
            // Default to Phone & Tablet if no promoted template matched yet
            selectedAndroidProjectEntry = entry
          }
          val selectedGridItem = entry.selectedGridItem
          if (!hasMatchedPromotedTemplate && promotedTemplate?.matches(selectedGridItem, it) == true) {
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
    emptyItemLabel: String = "Empty Activity",
  ): GridItem? {
    if (promotedTemplate != null) {
      if (promotedTemplate.formFactor == currentFormFactor) {
        val regularMatch = gridItems.filterIsInstance<TemplateGridItem>().firstOrNull { it.template.name == promotedTemplate.name }
        if (regularMatch != null) return regularMatch
      }
      val promotionMatch = gridItems.filterIsInstance<PluginPromotionGridItem>().firstOrNull { it.pluginId == promotedTemplate.pluginId }
      if (promotionMatch != null) return promotionMatch
    }
    return gridItems.firstOrNull { it.title == emptyItemLabel }
      ?: gridItems.filterIsInstance<TemplateGridItem>().firstOrNull { it.template != Template.NoActivity }
      ?: gridItems.firstOrNull()
  }

  private fun createFormFactorEntry(formFactor: FormFactor, promotedTemplate: PromotedTemplate?): FormFactorProjectEntry {
    val gridItems = formFactor.getProjectTemplates()
    return FormFactorProjectEntry(
      formFactor.toString(),
      gridItems,
      getDefaultSelectedGridItem(gridItems = gridItems, promotedTemplate = promotedTemplate, currentFormFactor = formFactor),
      onGridItemDoubleClick = { onGridItemDoubleClick() },
    )
  }
}
