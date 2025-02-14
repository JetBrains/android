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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.preview.PreviewElementModelAdapter
import com.android.tools.idea.preview.actions.CommonPreviewActionManager
import com.android.tools.idea.preview.analytics.PreviewRefreshEventBuilder
import com.android.tools.idea.preview.analytics.PreviewRefreshTracker
import com.android.tools.idea.preview.animation.AnimationPreview
import com.android.tools.idea.preview.find.PreviewElementProvider
import com.android.tools.idea.preview.representation.CommonPreviewRepresentation
import com.android.tools.idea.preview.views.CommonNlDesignSurfacePreviewView
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.wear.preview.animation.SupportedWearTileAnimationManager
import com.android.tools.idea.wear.preview.animation.WearTileAnimationPreview
import com.android.tools.idea.wear.preview.animation.analytics.AnimationToolingUsageTracker
import com.android.tools.idea.wear.preview.animation.analytics.WearTileAnimationTracker
import com.android.tools.idea.wear.preview.animation.detectAnimations
import com.android.tools.preview.PreviewElement
import com.android.tools.rendering.RenderAsyncActionExecutor
import com.android.tools.wear.preview.WearTilePreviewElement
import com.google.wireless.android.sdk.stats.PreviewRefreshEvent
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer

private val WEAR_TILE_SUPPORTED_ACTIONS = setOf(NlSupportedActions.TOGGLE_ISSUE_PANEL)

internal class WearTilePreviewRepresentation(
  adapterViewFqcn: String,
  psiFile: PsiFile,
  previewProviderConstructor:
    (SmartPsiElementPointer<PsiFile>) -> PreviewElementProvider<PsiWearTilePreviewElement>,
  previewElementModelAdapterDelegate: PreviewElementModelAdapter<PsiWearTilePreviewElement, NlModel>,
) :
  CommonPreviewRepresentation<PsiWearTilePreviewElement>(
    adapterViewFqcn,
    psiFile,
    previewProviderConstructor,
    previewElementModelAdapterDelegate,
    ::CommonNlDesignSurfacePreviewView,
    ::WearTilePreviewViewModel,
    NlSurfaceBuilder::configureDesignSurface,
    renderingTopic = RenderAsyncActionExecutor.RenderingTopic.WEAR_TILE_PREVIEW,
    createRefreshEventBuilder = { surface ->
      PreviewRefreshEventBuilder(
        PreviewRefreshEvent.PreviewType.WEAR,
        PreviewRefreshTracker.getInstance(surface),
      )
    },
    onAfterRender = ::detectAnimations,
  ) {

  @UiThread
  override fun createAnimationInspector(
    element: PreviewElement<*>
  ): AnimationPreview<SupportedWearTileAnimationManager>? {
    val wearPreviewElement = element as? WearTilePreviewElement<*> ?: return null

    return WearTileAnimationPreview(
      project,
      surface,
      wearPreviewElement,
      WearTileAnimationTracker(AnimationToolingUsageTracker.getInstance(surface)),
    )
  }
}

private fun NlSurfaceBuilder.configureDesignSurface(navigationHandler: NavigationHandler) {
  setActionManagerProvider { CommonPreviewActionManager(it, navigationHandler) }
  setSupportedActions(WEAR_TILE_SUPPORTED_ACTIONS)
  setScreenViewProvider(WEAR_TILE_SCREEN_VIEW_PROVIDER, false)
}
