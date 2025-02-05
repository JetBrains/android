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
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import kotlinx.coroutines.launch

/**
 * [AnAction] that navigates to a UI component, and that is visible at the position where the mouse
 * is located, at the moment the action is created. For Compose Previews, for example, this means
 * the deepest Composable that is part of the project.
 */
class JumpToDefinitionAction(
  @SwingCoordinate private val x: Int,
  @SwingCoordinate private val y: Int,
  private val navigationHandler: NavigationHandler,
) : AnAction(message("action.jump.to.definition")) {

  override fun update(e: AnActionEvent) {
    val surface = e.getData(DESIGN_SURFACE)
    val sceneView = surface?.getSceneViewAt(x, y)
    e.presentation.isEnabledAndVisible =
      (sceneView?.sceneManager as? LayoutlibSceneManager)?.renderResult != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val surface = e.getRequiredData(DESIGN_SURFACE)
    val sceneView = surface.getSceneViewAt(x, y) ?: return
    AndroidCoroutineScope(sceneView).launch {
      navigationHandler
        .findNavigatablesWithCoordinates(sceneView, x, y, true, false)
        .map { it.navigatable }
        .firstOrNull()
        ?.let {
          // We first try to navigate to specific components within the coordinates, but fall back
          // to a more general scene view navigation if it's not possible.
          navigationHandler.navigateTo(sceneView, it, true)
        } ?: navigationHandler.handleNavigate(sceneView, true)
    }
  }
}
