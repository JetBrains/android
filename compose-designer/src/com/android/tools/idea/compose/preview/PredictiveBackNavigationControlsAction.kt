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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.actions.findPreviewManager
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.preview.ComposePreviewElementInstance
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons

/**
 * Action that toggles the visibility of predictive back navigation controls in the bottom panel of the Compose Interactive Preview.
 *
 * When activated, it evaluates whether the current interactive session supports predictive back navigation. If enabled, it interacts with
 * the [InteractivePreviewNavigationController] to either show or hide the navigation controls. It also guarantees that any in-progress back
 * navigation is canceled if the user decides to hide the controls midway.
 */
class PredictiveBackNavigationControlsAction : DumbAwareAction(null, null, StudioIcons.LayoutEditor.Palette.NAV_HOST_FRAGMENT) {

  override fun actionPerformed(e: AnActionEvent) {
    val interactivePreviewNavigationController: InteractivePreviewNavigationController =
      e.dataContext.getData(InteractivePreviewNavigationController.KEY) ?: return
    val selectedPreview =
      e.dataContext.findPreviewManager(PreviewModeManager.KEY)?.mode?.value?.selected as? ComposePreviewElementInstance ?: return
    val zoomController = (e.dataContext.getData(DESIGN_SURFACE) as DesignSurface).zoomController
    // Check if zoom to fit was set before expanding/collapsing the panel
    val isZoomToFit = !zoomController.canZoomToFit()
    if (isZoomToFit) {
      zoomController.resetZoomToFitSettings(shouldWaitForResize = true, shouldWaitForLayoutCreated = false)
    }
    if (interactivePreviewNavigationController.isNavigationControlsShown()) {
      // If there was an interaction with the bottom panel navigation, a back navigation might have
      // been started or be in progress. We ensure to cancel any ongoing navigation before closing
      // the bottom panel.
      interactivePreviewNavigationController.hideNavigationControls()
    } else {
      interactivePreviewNavigationController.showNavigationControls(selectedPreview)
    }
    // If after expanding/collapsing the panel zoom-to-fit was set, we apply again zoom-to-fit so that The preview is always visible.
    if (isZoomToFit) {
      zoomController.zoomToFit()
    }
  }

  override fun update(e: AnActionEvent) {
    val interactivePreviewNavigationController: InteractivePreviewNavigationController =
      e.dataContext.getData(InteractivePreviewNavigationController.KEY) ?: return
    val canShowNavigationPanel = interactivePreviewNavigationController.canShowNavigationPanel()

    e.presentation.isVisible = canShowNavigationPanel && StudioFlags.COMPOSE_INTERACTIVE_PREVIEW_PREDICTIVE_BACK.get()
    e.presentation.text =
      if (interactivePreviewNavigationController.isNavigationControlsShown()) {
        message("action.navigate.hide.options")
      } else {
        message("action.navigate.show.options")
      }
  }

  /** BGT is needed when calling [findPreviewManager] because it accesses the VirtualFile */
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
