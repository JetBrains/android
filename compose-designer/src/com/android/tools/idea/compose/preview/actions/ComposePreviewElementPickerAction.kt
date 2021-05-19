/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT
import com.android.tools.idea.compose.preview.pickers.PsiPickerManager
import com.android.tools.idea.compose.preview.pickers.properties.PsiCallPropertyModel
import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.ui.AnActionButton

/**
 * An action that opens the picker for a `@Preview` annotation.
 */
class ComposePreviewElementPickerAction(private val dataContextProvider: () -> DataContext) : AnActionButton("Picker") {
  override fun updateButton(e: AnActionEvent) {
    e.presentation.isEnabled = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val modelDataContext = dataContextProvider()
    val previewElement = (modelDataContext.getData(COMPOSE_PREVIEW_ELEMENT) as? PreviewElementInstance) ?: return

    val model = PsiCallPropertyModel.fromPreviewElement(project, previewElement)
    PsiPickerManager.showForEvent(e, model)
  }
}