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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneInteraction
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.Interaction
import com.android.tools.idea.common.surface.InteractionProviderBase
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.model.viewGroupHandler
import java.awt.Rectangle

class NlInteractionProvider(private val surface: DesignSurface): InteractionProviderBase(surface) {

  override fun createInteractionOnClick(@SwingCoordinate mouseX: Int, @SwingCoordinate mouseY: Int): Interaction? {
    val view = surface.getSceneView(mouseX, mouseY) ?: return null
    val screenView = view as ScreenView
    val size = screenView.size
    val resizeZone = Rectangle(view.getX() + size.width,
                               screenView.y + size.height,
                               NlConstants.RESIZING_HOVERING_SIZE,
                               NlConstants.RESIZING_HOVERING_SIZE)
    if (resizeZone.contains(mouseX, mouseY) && surface.isResizeAvailable) {
      val configuration = surface.configuration!!
      return CanvasResizeInteraction(surface as NlDesignSurface, screenView, configuration)
    }

    val selectionModel = screenView.selectionModel
    var component = Coordinates.findComponent(screenView, mouseX, mouseY)
    if (component == null) {
      // If we cannot find an element where we clicked, try to use the first element currently selected
      // (if any) to find the view group handler that may want to handle the mousePressed()
      // This allows us to correctly handle elements out of the bounds of the screen view.
      if (!selectionModel.isEmpty) {
        component = selectionModel.primary
      }
      else {
        return null
      }
    }

    var interaction: Interaction? = null

    // Give a chance to the current selection's parent handler
    if (!selectionModel.isEmpty) {
      val primary = screenView.selectionModel.primary
      val parent = primary?.parent
      if (parent != null) {
        val handler = parent.viewGroupHandler
        if (handler != null) {
          interaction = handler!!.createInteraction(screenView, mouseX, mouseY, primary)
        }
      }
    }

    if (interaction == null) {
      // Check if we have a ViewGroupHandler that might want
      // to handle the entire interaction
      val viewGroupHandler = component?.viewGroupHandler
      if (viewGroupHandler != null) {
        interaction = viewGroupHandler!!.createInteraction(screenView, mouseX, mouseY, component!!)
      }
    }

    if (interaction == null) {
      interaction = SceneInteraction(screenView)
    }
    return interaction
  }

  override fun createInteractionOnDrag(@SwingCoordinate mouseX: Int, @SwingCoordinate mouseY: Int): Interaction? {
    val sceneView = surface.getSceneView(mouseX, mouseY) ?: return null
    val scene = sceneView.scene
    val selectionModel = sceneView.selectionModel

    val xDp = Coordinates.getAndroidXDip(sceneView, mouseX)
    val yDp = Coordinates.getAndroidYDip(sceneView, mouseY)

    val model = sceneView.model
    var component: SceneComponent? = null

    // Make sure we start from root if we don't have anything selected
    if (selectionModel.isEmpty && !model.components.isEmpty()) {
      selectionModel.setSelection(listOf(model.components[0].root!!))
    }

    // See if you're dragging inside a selected parent; if so, drag the selection instead of any
    // leaf nodes inside it
    val primarySelectedComponent = selectionModel.primary
    val primary = scene.getSceneComponent(primarySelectedComponent)
    if (primary != null && primary.parent != null && primary.containsX(xDp) && primary.containsY(yDp)) {
      component = primary
    }
    if (component == null) {
      component = scene.findComponent(sceneView.context, xDp, yDp)
    }

    if (component?.parent == null) {
      // Dragging on the background/root view: start a marquee selection
      return MarqueeInteraction(sceneView)
    }

    val primaryDraggedComponent = primary ?: component
    val dragged: List<NlComponent>
    // Dragging over a non-root component: move the set of components (if the component dragged over is
    // part of the selection, drag them all, otherwise drag just this component)
    if (surface.selectionModel.isSelected(component.nlComponent)) {
      val selectedDraggedComponents = mutableListOf<NlComponent>()

      val primaryNlComponent: NlComponent?
      // Make sure the primaryDraggedComponent is the first element
      if (primaryDraggedComponent.parent == null) {
        primaryNlComponent = null
      }
      else {
        primaryNlComponent = primaryDraggedComponent.nlComponent
        selectedDraggedComponents.add(primaryNlComponent)
      }

      for (selected in surface.selectionModel.selection) {
        if (!selected.isRoot && selected !== primaryNlComponent) {
          selectedDraggedComponents.add(selected)
        }
      }
      dragged = selectedDraggedComponents
    }
    else {
      dragged = listOf(primaryDraggedComponent.nlComponent)
    }
    return DragDropInteraction(surface, dragged)
  }
}
