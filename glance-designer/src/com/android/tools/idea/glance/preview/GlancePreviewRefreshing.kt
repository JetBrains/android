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
import com.android.tools.idea.preview.PreviewElementModelAdapter
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.updatePreviewsAndRefresh
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiFile

internal suspend fun <T : MethodPreviewElement> NlDesignSurface.updateGlancePreviewsAndRefresh(
  previewElementProvider: PreviewElementProvider<T>,
  log: Logger,
  psiFile: PsiFile,
  parentDisposable: Disposable,
  progressIndicator: ProgressIndicator,
  onRenderCompleted: () -> Unit,
  previewElementModelAdapter: PreviewElementModelAdapter<T, NlModel>,
  configureLayoutlibSceneManager:
    (PreviewDisplaySettings, LayoutlibSceneManager) -> LayoutlibSceneManager
): List<T> {
  return updatePreviewsAndRefresh(
    true,
    previewElementProvider,
    log,
    psiFile,
    parentDisposable,
    progressIndicator,
    onRenderCompleted,
    previewElementModelAdapter,
    ::GlanceAppWidgetAdapterLightVirtualFile,
    configureLayoutlibSceneManager
  )
}
