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
package com.android.tools.idea.compose.preview.scene

import com.android.flags.ifEnabled
import com.android.tools.idea.common.surface.DEVICE_CONFIGURATION_SHAPE_POLICY
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.common.surface.SQUARE_SHAPE_POLICY
import com.android.tools.idea.common.surface.SceneLayer
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.compose.preview.util.isRootComponentSelected
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.idea.uibuilder.surface.ScreenViewLayer
import com.android.tools.idea.uibuilder.surface.ScreenViewProvider
import com.android.tools.idea.uibuilder.surface.layer.BorderColor
import com.android.tools.idea.uibuilder.surface.layer.BorderLayer
import com.android.tools.idea.uibuilder.surface.layer.ClassLoadingDebugLayer
import com.android.tools.idea.uibuilder.surface.layer.DiagnosticsLayer
import com.android.tools.idea.uibuilder.surface.layer.UiCheckWarningLayer
import com.android.tools.idea.uibuilder.surface.sizepolicy.ImageContentSizePolicy
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.analysis.problemsView.toolWindow.ProblemsView

class ComposeScreenViewProvider(private val previewManager: ComposePreviewManager) :
  ScreenViewProvider {
  override var colorBlindFilter: ColorBlindMode = ColorBlindMode.NONE
  override val displayName: String = "Compose"

  override fun createPrimarySceneView(
    surface: NlDesignSurface,
    manager: LayoutlibSceneManager,
  ): ScreenView =
    ScreenView.newBuilder(surface, manager)
      .withLayersProvider {
        ImmutableList.builder<Layer>()
          .apply {
            if (it.hasBorderLayer()) {
              add(
                BorderLayer(it, true, { surface.isRotating }) { sceneView ->
                  when {
                    StudioFlags.COMPOSE_PREVIEW_SELECTION.get() &&
                      sceneView.isRootComponentSelected() -> BorderColor.SELECTED
                    sceneView == surface.sceneViewAtMousePosition -> BorderColor.HOVERED
                    else -> BorderColor.DEFAULT_WITHOUT_SHADOW
                  }
                }
              )
            }
            add(ScreenViewLayer(it, colorBlindFilter, surface, surface::rotateSurfaceDegree))
            add(
              SceneLayer(surface, it, false).apply {
                isShowOnHover = true
                setShowOnHoverFilter { sceneView ->
                  (previewManager.mode.value.isNormal ||
                    previewManager.mode.value is PreviewMode.UiCheck) &&
                    (!StudioFlags.COMPOSE_PREVIEW_SELECTION.get() ||
                      sceneView.isRootComponentSelected())
                }
              }
            )
            add(
              UiCheckWarningLayer(it) {
                previewManager.mode.value is PreviewMode.UiCheck &&
                  ProblemsView.getToolWindow(surface.project)
                    ?.contentManager
                    ?.selectedContent
                    ?.tabName == surface.visualLintIssueProvider.uiCheckInstanceId
              }
            )
            StudioFlags.NELE_CLASS_PRELOADING_DIAGNOSTICS.ifEnabled {
              add(ClassLoadingDebugLayer(surface.models.first().facet.module))
            }
            StudioFlags.NELE_RENDER_DIAGNOSTICS.ifEnabled {
              add(DiagnosticsLayer(surface, surface.project))
            }
          }
          .build()
      }
      .withShapePolicy(
        if (
          PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.getData(manager.model.dataContext)
            ?.displaySettings
            ?.showDecoration == true
        )
          DEVICE_CONFIGURATION_SHAPE_POLICY
        else SQUARE_SHAPE_POLICY
      )
      .decorateContentSizePolicy { policy -> ImageContentSizePolicy(policy) }
      .build()

  override val surfaceType: LayoutEditorState.Surfaces = LayoutEditorState.Surfaces.SCREEN_SURFACE
}
