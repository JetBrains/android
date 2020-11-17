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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.NopSelectionModel
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.actions.PreviewSurfaceActionManager
import com.android.tools.idea.uibuilder.actions.SurfaceLayoutManagerOption
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.SingleDirectionLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.VerticalOnlyLayoutManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import java.util.function.BiFunction

/**
 * List of available layouts for the Compose Preview Surface.
 */
internal val PREVIEW_LAYOUT_MANAGER_OPTIONS = listOf(
  SurfaceLayoutManagerOption(message("vertical.layout"),
                             VerticalOnlyLayoutManager(NlConstants.DEFAULT_SCREEN_OFFSET_X, NlConstants.DEFAULT_SCREEN_OFFSET_Y,
                                                       NlConstants.SCREEN_DELTA, NlConstants.SCREEN_DELTA,
                                                       SingleDirectionLayoutManager.Alignment.CENTER)),
  SurfaceLayoutManagerOption(message("grid.layout"),
                             GridSurfaceLayoutManager(NlConstants.DEFAULT_SCREEN_OFFSET_X, NlConstants.DEFAULT_SCREEN_OFFSET_Y,
                                                      NlConstants.SCREEN_DELTA, NlConstants.SCREEN_DELTA),
                             DesignSurface.SceneViewAlignment.LEFT)
)

/**
 * Default layout manager selected in the preview.
 */
internal val DEFAULT_PREVIEW_LAYOUT_MANAGER = PREVIEW_LAYOUT_MANAGER_OPTIONS.first().layoutManager

/**
 * Creates a [NlDesignSurface] setup for the Compose preview.
 */
internal fun createPreviewDesignSurface(
  project: Project,
  navigationHandler: NlDesignSurface.NavigationHandler,
  delegateInteractionHandler: DelegateInteractionHandler,
  psiFilePointer: SmartPsiElementPointer<PsiFile>,
  previewRepresentation: ComposePreviewRepresentation,
  sceneManagerProvider: BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager>): NlDesignSurface =
  NlDesignSurface.builder(project, previewRepresentation)
    .setIsPreview(true)
    .showModelNames()
    .setNavigationHandler(navigationHandler)
    .setLayoutManager(DEFAULT_PREVIEW_LAYOUT_MANAGER)
    .setActionManagerProvider { surface -> PreviewSurfaceActionManager(surface) }
    .setInteractionHandlerProvider { delegateInteractionHandler }
    .setActionHandler { surface -> PreviewSurfaceActionHandler(surface) }
    .setSceneManagerProvider(sceneManagerProvider)
    .setEditable(true)
    .setDelegateDataProvider {
      return@setDelegateDataProvider when (it) {
        COMPOSE_PREVIEW_MANAGER.name -> previewRepresentation
        // The Compose preview NlModels do not point to the actual file but to a synthetic file
        // generated for Layoutlib. This ensures we return the right file.
        CommonDataKeys.VIRTUAL_FILE.name -> psiFilePointer.virtualFile
        CommonDataKeys.PROJECT.name -> project
        else -> null
      }
    }
    .setSelectionModel(NopSelectionModel)
    .build()
    .apply {
      setScreenViewProvider(NlScreenViewProvider.COMPOSE, false)
      setMaxFitIntoZoomLevel(2.0) // Set fit into limit to 200%
    }