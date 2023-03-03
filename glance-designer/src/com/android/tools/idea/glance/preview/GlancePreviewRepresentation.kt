/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.idea.preview.PreviewElementModelAdapter
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.representation.CommonPreviewRepresentation
import com.android.tools.idea.preview.views.CommonNlDesignSurfacePreviewView
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.intellij.psi.PsiFile

private val GLANCE_APPWIDGET_SUPPORTED_ACTIONS = setOf(NlSupportedActions.TOGGLE_ISSUE_PANEL)

/** A [PreviewRepresentation] for glance [PreviewElement]s */
internal class GlancePreviewRepresentation<T : MethodPreviewElement>(
  adapterViewFqcn: String,
  psiFile: PsiFile,
  previewProvider: PreviewElementProvider<T>,
  previewElementModelAdapterDelegate: PreviewElementModelAdapter<T, NlModel>,
) :
  CommonPreviewRepresentation<T>(
    adapterViewFqcn,
    psiFile,
    previewProvider,
    previewElementModelAdapterDelegate,
    ::CommonNlDesignSurfacePreviewView,
    ::GlancePreviewViewModel,
    NlDesignSurface.Builder::configureDesignSurface
  )

private fun NlDesignSurface.Builder.configureDesignSurface() {
  setActionManagerProvider(::GlancePreviewActionManager)
  setSupportedActions(GLANCE_APPWIDGET_SUPPORTED_ACTIONS)
  setScreenViewProvider(GLANCE_SCREEN_VIEW_PROVIDER, false)
}
