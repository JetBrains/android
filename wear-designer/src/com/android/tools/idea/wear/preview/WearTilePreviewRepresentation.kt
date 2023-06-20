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
package com.android.tools.idea.wear.preview

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.preview.PreviewElementModelAdapter
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.representation.CommonPreviewRepresentation
import com.android.tools.idea.preview.views.CommonNlDesignSurfacePreviewView
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.intellij.psi.PsiFile

private val WEAR_TILE_SUPPORTED_ACTIONS = setOf(NlSupportedActions.TOGGLE_ISSUE_PANEL)

internal class WearTilePreviewRepresentation(
  adapterViewFqcn: String,
  psiFile: PsiFile,
  previewProvider: PreviewElementProvider<WearTilePreviewElement>,
  previewElementModelAdapterDelegate: PreviewElementModelAdapter<WearTilePreviewElement, NlModel>,
) :
  CommonPreviewRepresentation<WearTilePreviewElement>(
    adapterViewFqcn,
    psiFile,
    previewProvider,
    previewElementModelAdapterDelegate,
    ::CommonNlDesignSurfacePreviewView,
    ::WearTilePreviewViewModel,
    NlDesignSurface.Builder::configureDesignSurface
  )

private fun NlDesignSurface.Builder.configureDesignSurface() {
  setActionManagerProvider(::WearTilePreviewActionManager)
  setSupportedActions(WEAR_TILE_SUPPORTED_ACTIONS)
  setScreenViewProvider(WEAR_TILE_SCREEN_VIEW_PROVIDER, false)
}