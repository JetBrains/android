/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.actions

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.representation.PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.uibuilder.layout.option.GalleryLayoutManager
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger

/**
 * [AnAction] that open the selected sceneView in Gallery Mode. The current mouse position
 * determines the selected sceneView when the action is created. The action is not enabled if the
 * current mode is Gallery already.
 */
class ViewInGalleryAction(
  @SwingCoordinate private val x: Int,
  @SwingCoordinate private val y: Int,
) : AnAction(message("action.view.in.gallery")) {

  private val logger = Logger.getInstance(ViewInGalleryAction::class.java)

  override fun update(e: AnActionEvent) {
    val surface = e.getData(DESIGN_SURFACE) as? NlDesignSurface
    val sceneView = surface?.getSceneViewAt(x, y)

    // The action is not visible if the open-to-gallery flag is disabled.
    e.presentation.isVisible = StudioFlags.VIEW_IN_GALLERY.get()

    val isGallery: Boolean = surface?.isLayoutGallery() ?: false
    val hasRendered: Boolean =
      (sceneView?.sceneManager as? LayoutlibSceneManager)?.renderResult != null

    // Disable the button if:
    // * SceneView has not finished to render yet.
    // * If we are in Gallery mode already.
    e.presentation.isEnabled = !isGallery && hasRendered
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val surface = e.getData(DESIGN_SURFACE) as NlDesignSurface
    val previewElementInstance =
      surface
        .getSceneViewAt(x, y)
        ?.sceneManager
        ?.model
        ?.dataContext
        ?.getData(PREVIEW_ELEMENT_INSTANCE)

    if (previewElementInstance == null) {
      logger.error("Cannot find any preview element instance")
      return
    }

    val modeManager = e.dataContext.findPreviewManager(PreviewModeManager.KEY)
    if (modeManager == null) {
      logger.error("Cannot find any preview manager")
      return
    }
    modeManager.setMode(PreviewMode.Gallery(previewElementInstance))
  }

  private fun NlDesignSurface.isLayoutGallery() =
    layoutManagerSwitcher?.currentLayout?.value?.layoutManager is GalleryLayoutManager
}
