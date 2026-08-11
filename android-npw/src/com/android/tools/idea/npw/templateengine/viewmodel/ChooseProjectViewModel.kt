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
package com.android.tools.idea.npw.templateengine.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.template.engine.TemplateDefinition
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.npw.project.AndroidProjectEntryProvider
import com.android.tools.idea.npw.project.ChooseAndroidProjectEntry
import com.android.tools.idea.npw.templateengine.services.TemplateRegistryService
import com.android.tools.idea.npw.templateengine.ui.TemplateEngineTemplateGridProjectEntry

class ChooseProjectViewModel(private val registry: TemplateRegistryService) {

  val templateDefinitions: List<TemplateDefinition>
    get() = registry.getTemplateDefinitions()

  val errorMessage: String?
    get() = registry.getLastErrorMessage()

  // Keep external plugin entries (like 'Create with AI') first, followed by standard category entries.
  val categories: List<ChooseAndroidProjectEntry> = run {
    val rawEntries = AndroidProjectEntryProvider.getAllProjectEntries()
    val standardEntries = rawEntries.filterIsInstance<TemplateEngineTemplateGridProjectEntry>()
    val externalEntries = rawEntries.filter { it !is TemplateEngineTemplateGridProjectEntry }
    externalEntries + standardEntries
  }

  var selectedCategory by
    mutableStateOf<ChooseAndroidProjectEntry?>(
      categories.filterIsInstance<TemplateEngineTemplateGridProjectEntry>().firstOrNull { it.formFactor == FormFactor.MOBILE }
        ?: categories.firstOrNull()
    )

  var selectedTemplate: TemplateDefinition?
    get() {
      val category = selectedCategory
      if (category is TemplateEngineTemplateGridProjectEntry) {
        return category.selectedTemplate
      }
      return null
    }
    set(value) {
      val category = selectedCategory
      if (category is TemplateEngineTemplateGridProjectEntry && value != null && category.templates.contains(value)) {
        category.selectedTemplate = value
      }
    }
}
