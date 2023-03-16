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

import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.findDeepestHits
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.parseViewInfo
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import javax.swing.SwingUtilities

/**
 * [AnAction] that zooms to the component in a given sceneView that corresponds to the deepest
 * Composable visible at the position where the mouse is located at the moment the action is
 * created.
 */
class ZoomToSelectionAction(
  private val surface: NlDesignSurface,
  private val sceneView: SceneView,
  title: String = message("action.zoom.to.selection")
) : AnAction(title) {

  private val logger = Logger.getInstance(ZoomToSelectionAction::class.java)

  @AndroidCoordinate private val x: Int
  @AndroidCoordinate private val y: Int

  init {
    // Extract the information relative to the mouse position when creating the action,
    // not when clicking on "zoom to selection"
    val mousePosition = MouseInfo.getPointerInfo().location
    SwingUtilities.convertPointFromScreen(mousePosition, surface.interactionPane)
    x = Coordinates.getAndroidX(sceneView, mousePosition.x)
    y = Coordinates.getAndroidY(sceneView, mousePosition.y)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible =
      (sceneView.sceneManager as? LayoutlibSceneManager)?.renderResult != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val deepestViewInfos =
      sceneView.scene.root
        ?.nlComponent
        ?.viewInfo
        ?.let { viewInfo -> parseViewInfo(viewInfo, { it }, logger) }
        ?.findDeepestHits(x, y)
    if (deepestViewInfos.isNullOrEmpty()) {
      // This is expected for example when the Preview contains showSystemUi=true
      // and the "systemUi" is where the right-click happens.
      logger.info("Could not find the view to zoom to, zooming to the whole Preview.")
      surface.zoomAndCenter(sceneView, Rectangle(Point(0, 0), sceneView.scaledContentSize))
      return
    }
    if (deepestViewInfos.size > 1) {
      logger.warn(
        "Expected 1 view to zoom to, but found ${deepestViewInfos.size}, choosing the last one."
      )
    }
    val composeViewInfo = deepestViewInfos.last()
    composeViewInfo.bounds.let {
      val topLeftCorner =
        Point(
          Coordinates.getSwingDimension(sceneView, it.left),
          Coordinates.getSwingDimension(sceneView, it.top)
        )
      val size =
        Dimension(
          Coordinates.getSwingDimension(sceneView, it.width),
          Coordinates.getSwingDimension(sceneView, it.height)
        )
      surface.zoomAndCenter(sceneView, Rectangle(topLeftCorner, size))
    }
  }
}
