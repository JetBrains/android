/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.glance.preview

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.updatePreviewsAndRefresh
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiFile

/**
 * Returns a number indicating how [el1] [MethodPreviewElement] is to the [el2]
 * [MethodPreviewElement]. 0 meaning they are equal and higher the number the more dissimilar they
 * are. This allows for, when re-using models, the model with the most similar
 * [MethodPreviewElement] is re-used. When the user is just switching groups or selecting a specific
 * model, this allows switching to the existing preview faster.
 *
 * TODO(b/239802877): Add this an instance of PreviewElementModelAdapter
 */
fun calcGlanceElementsAffinity(el1: MethodPreviewElement, el2: MethodPreviewElement?): Int {
  if (el2 == null) return 3

  return when {
    // These are the same
    el1 == el2 -> 0

    // The method and display settings are the same
    el1.methodFqcn == el2.methodFqcn && el1.displaySettings == el2.displaySettings -> 1

    // The name of the @Composable method matches but other settings might be different
    el1.methodFqcn == el2.methodFqcn -> 2

    // No match
    else -> 4
  }
}

internal suspend fun <T : MethodPreviewElement> NlDesignSurface.updateGlancePreviewsAndRefresh(
  previewElementProvider: PreviewElementProvider<T>,
  log: Logger,
  psiFile: PsiFile,
  parentDisposable: Disposable,
  progressIndicator: ProgressIndicator,
  onRenderCompleted: () -> Unit,
  previewElementToXml: (T) -> String,
  dataContextProvider: (T) -> DataContext,
  modelToPreview: NlModel.() -> T?,
  configureLayoutlibSceneManager:
    (PreviewDisplaySettings, LayoutlibSceneManager) -> LayoutlibSceneManager
): List<T> {
  val debugLogger = if (log.isDebugEnabled) GlanceDebugLogger<T>(log) else null
  return updatePreviewsAndRefresh(
    true,
    previewElementProvider,
    debugLogger,
    psiFile,
    parentDisposable,
    progressIndicator,
    onRenderCompleted,
    previewElementToXml,
    dataContextProvider,
    modelToPreview,
    ::calcGlanceElementsAffinity,
    MethodPreviewElement::applyTo,
    ::GlanceAppWidgetAdapterLightVirtualFile,
    configureLayoutlibSceneManager
  )
}
