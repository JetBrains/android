/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.naveditor.surface

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates.getAndroidXDip
import com.android.tools.idea.common.model.Coordinates.getAndroidYDip
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneInteraction
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.Interaction
import com.android.tools.idea.common.surface.InteractionHandlerBase
import com.android.tools.idea.naveditor.scene.NavSceneManager
import com.android.tools.idea.uibuilder.surface.interaction.MarqueeInteraction
import org.intellij.lang.annotations.JdkConstants

class NavInteractionHandler(private val surface: DesignSurface<NavSceneManager>): InteractionHandlerBase(surface) {

  override fun createInteractionOnPressed(@SwingCoordinate mouseX: Int,
                                          @SwingCoordinate mouseY: Int,
                                          @JdkConstants.InputEventMask modifiersEx: Int): Interaction? {
    val sceneView = surface.focusedSceneView ?: return null
    return SceneInteraction(sceneView);
  }

  override fun createInteractionOnDrag(@SwingCoordinate mouseX: Int,
                                       @SwingCoordinate mouseY: Int,
                                       @JdkConstants.InputEventMask modifiersEx: Int): Interaction? {
    val sceneView = surface.focusedSceneView ?: return null
    val scene = sceneView.scene
    val selectionModel = sceneView.selectionModel

    val xDp = getAndroidXDip(sceneView, mouseX)
    val yDp = getAndroidYDip(sceneView, mouseY)

    val model = sceneView.sceneManager.model

    // Make sure we start from root if we don't have anything selected
    if (selectionModel.isEmpty && !model.components.isEmpty()) {
      selectionModel.setSelection(listOf(model.components[0].root!!))
    }

    // See if you're dragging inside a selected parent; if so, drag the selection instead of any
    // leaf nodes inside it
    var component: SceneComponent? = null
    val primary = scene.getSceneComponent(selectionModel.primary)
    if (primary != null && primary.parent != null && primary.containsX(xDp) && primary.containsY(yDp)) {
      component = primary
    }
    if (component == null) {
      component = scene.findComponent(sceneView.context, xDp, yDp)
    }

    if (component?.parent == null) {
      // Dragging on the background/root view: start a marquee selection
      return MarqueeInteraction(sceneView) { surface.repaint() }
    }

    return null
  }
}
