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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import java.awt.Rectangle

/**
 * [AnAction] that zooms to the component in a given sceneView that corresponds to the deepest
 * Composable visible at the position where the mouse is located at the moment the action is
 * created.
 */
class ZoomToSelectionAction(
  @SwingCoordinate private val x: Int,
  @SwingCoordinate private val y: Int,
  val zoomTargetProvider:
    ( sceneView: SceneView, x: Int, y: Int, logger: Logger) -> Rectangle?,
) : AnAction(message("action.zoom.to.selection")) {

  private val logger = Logger.getInstance(ZoomToSelectionAction::class.java)

  override fun update(e: AnActionEvent) {
    val surface = e.getData(DESIGN_SURFACE) as? NlDesignSurface
    val sceneView = surface?.getSceneViewAt(x, y)
    e.presentation.isEnabledAndVisible =
      (sceneView?.sceneManager as? LayoutlibSceneManager)?.renderResult != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val surface = e.getRequiredData(DESIGN_SURFACE) as NlDesignSurface
    val sceneView = surface.getSceneViewAt(x, y) ?: return
    var zoomTarget: Rectangle = zoomTargetProvider(sceneView, x, y, logger) ?: return
    surface.zoomAndCenter(sceneView, zoomTarget)
  }
}
