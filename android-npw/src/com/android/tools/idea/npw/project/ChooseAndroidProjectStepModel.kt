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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.project.ChooseAndroidProjectStep.Companion.getProjectTemplates
import com.android.tools.idea.npw.project.ChooseAndroidProjectStep.Companion.getTemplateTitle
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Template
import java.util.function.Supplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChooseAndroidProjectStepModel(private val formFactorSupplier: Supplier<List<FormFactor>>) {
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
    val entries = mutableListOf<ChooseAndroidProjectEntry>()
    withContext(Dispatchers.IO) {
      formFactorSupplier.get().forEach { entries.add(createFormFactorEntry(it)) }
      if (StudioFlags.GEMINI_NEW_PROJECT_AGENT.get()) entries.add(createGeminiEntry())
    }
    chooseAndroidProjectEntries = entries
    isLoading = false
  }

  fun updateSelectedCell(entry: ChooseAndroidProjectEntry?) {
    selectedAndroidProjectEntry = entry
  }

  private fun getDefaultSelectedTemplateIndex(
    templates: List<Template>,
    emptyItemLabel: String = "Empty Activity",
  ): Template? =
    templates.firstOrNull { it.getTemplateTitle() == emptyItemLabel }
      ?: templates.firstOrNull { it != Template.NoActivity }

  private fun createFormFactorEntry(formFactorInfo: FormFactor): FormFactorProjectEntry {
    val templates = formFactorInfo.getProjectTemplates()
    return FormFactorProjectEntry(
      formFactorInfo.toString(),
      templates,
      getDefaultSelectedTemplateIndex(templates),
    )
  }

  private fun createGeminiEntry() = GeminiProjectEntry()
}
