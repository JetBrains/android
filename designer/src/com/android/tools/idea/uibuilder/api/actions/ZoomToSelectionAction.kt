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
package com.android.tools.idea.uibuilder.api.actions

import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.uibuilder.model.h
import com.android.tools.idea.uibuilder.model.w
import com.android.tools.idea.uibuilder.model.x
import com.android.tools.idea.uibuilder.model.y
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

/**
 * [AnAction] that zooms the current [com.android.tools.idea.common.model.NlComponent] under the
 * mouse in a given sceneView that.
 */
class ZoomToSelectionAction
@JvmOverloads
constructor(private val surface: NlDesignSurface, title: String = "Zoom to Selection") :
  AnAction(title) {
  override fun update(e: AnActionEvent) {
    val sceneView = surface.focusedSceneView
    e.presentation.isEnabledAndVisible =
      surface.selectionModel.primary != null &&
        sceneView != null &&
        (sceneView.sceneManager as? LayoutlibSceneManager)?.renderResult != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val sceneView = surface.focusedSceneView ?: return
    val component = surface.selectionModel.primary ?: return
    val topLeftCorner =
      Point(
        Coordinates.getSwingDimension(sceneView, component.x),
        Coordinates.getSwingDimension(sceneView, component.y),
      )
    val size =
      Dimension(
        Coordinates.getSwingDimension(sceneView, component.w),
        Coordinates.getSwingDimension(sceneView, component.h),
      )
    surface.zoomAndCenter(sceneView, Rectangle(topLeftCorner, size))
  }
}
