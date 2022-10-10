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
package com.android.tools.idea.preview

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.navigateToComponent
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlInteractionHandler
import kotlinx.coroutines.launch

class NavigatingInteractionHandler(private val surface: DesignSurface<*>) :
  NlInteractionHandler(surface) {

  private val scope = AndroidCoroutineScope(surface)
  override fun singleClick(x: Int, y: Int, modifiersEx: Int) {
    // Highlight the clicked widget but keep focus in DesignSurface.
    clickPreview(x, y, false)
  }

  override fun doubleClick(x: Int, y: Int, modifiersEx: Int) {
    // Navigate the caret to the clicked widget and focus on text editor.
    clickPreview(x, y, true)
  }

  /**
   * Handles a click in a preview. The click is handled asynchronously since finding the component
   * to navigate might be a slow operation.
   */
  private fun clickPreview(
    @SwingCoordinate x: Int,
    @SwingCoordinate y: Int,
    needsFocusEditor: Boolean
  ) {
    val sceneView = surface.getSceneViewAtOrPrimary(x, y) ?: return
    val androidX = Coordinates.getAndroidXDip(sceneView, x)
    val androidY = Coordinates.getAndroidYDip(sceneView, y)
    val navHandler = (surface as NlDesignSurface).navigationHandler ?: return
    val scene = sceneView.scene
    scope.launch(AndroidDispatchers.workerThread) {
      val sceneComponent =
        scene.findComponent(sceneView.context, androidX, androidY) ?: return@launch

      if (!navHandler.handleNavigate(sceneView, sceneComponent, x, y, needsFocusEditor)) {
        navigateToComponent(sceneComponent.nlComponent, needsFocusEditor)
      }
    }
  }
}
