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
import com.android.tools.idea.common.surface.InteractionHandlerBase
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.navigateToComponent
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.model.viewGroupHandler
import org.intellij.lang.annotations.JdkConstants
import java.awt.Cursor
import java.awt.Rectangle

class NlInteractionHandler(private val surface: DesignSurface): InteractionHandlerBase(surface) {

  override fun createInteractionOnPressed(@SwingCoordinate mouseX: Int, @SwingCoordinate mouseY: Int, modifiersEx: Int): Interaction? {
    val view = surface.getSceneView(mouseX, mouseY) ?: return null
    val screenView = view as ScreenView
    if (isInResizeZone(view, mouseX, mouseY)) {
      val configuration = view.sceneManager.model.configuration
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

  /**
   * Returns whether the given [mouseX] and [mouseY] coordinates are within the resizing handle area.
   * If resizing is disabled or the coordinates are outside of the resize handler area, this method returns false.
   */
  private fun isInResizeZone(sceneView: SceneView, @SwingCoordinate mouseX: Int, @SwingCoordinate mouseY: Int): Boolean {
    if (!sceneView.isResizeable || !sceneView.scene.isResizeAvailable) {
      // Resizing is disabled
      return false
    }

    val size = sceneView.scaledContentSize
    // Check if the mouse position is at the bottom-right corner of sceneView.
    val resizeZone = Rectangle(sceneView.x + size.width,
                               sceneView.y + size.height,
                               NlConstants.RESIZING_HOVERING_SIZE,
                               NlConstants.RESIZING_HOVERING_SIZE)
    return resizeZone.contains(mouseX, mouseY)
  }

  override fun createInteractionOnDrag(@SwingCoordinate mouseX: Int,
                                       @SwingCoordinate mouseY: Int,
                                       @JdkConstants.InputEventMask modifiersEx: Int): Interaction? {
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

  override fun singleClick(@SwingCoordinate x: Int, @SwingCoordinate y: Int, @JdkConstants.InputEventMask modifiersEx: Int) {
    if ((surface as NlDesignSurface).isPreviewSurface) {
      // Highlight the clicked widget but keep focus in DesignSurface.
      // TODO: Remove this after when b/136174865 is implemented, which removes the preview mode.
      clickPreview(x, y, false)
    }
    else {
      super.singleClick(x, y, modifiersEx)
    }
  }

  override fun doubleClick(@SwingCoordinate x: Int, @SwingCoordinate y: Int, @JdkConstants.InputEventMask modifiersEx: Int) {
    if ((surface as NlDesignSurface).isPreviewSurface) {
      // Navigate the caret to the clicked widget and focus on text editor.
      // TODO: Remove this after when b/136174865 is implemented, which removes the preview mode.
      clickPreview(x, y, true)
    }
    else {
      super.doubleClick(x, y, modifiersEx)
    }
  }

  private fun clickPreview(@SwingCoordinate x: Int, @SwingCoordinate y: Int, needsFocusEditor: Boolean) {
    val sceneView = surface.getSceneView(x, y) ?: return
    val androidX = Coordinates.getAndroidXDip(sceneView, x)
    val androidY = Coordinates.getAndroidYDip(sceneView, y)

    val sceneComponent = sceneView.scene.findComponent(sceneView.context, androidX, androidY) ?: return

    if ((surface as NlDesignSurface).navigationHandler?.handleNavigate(sceneView,
                                                                       sceneComponent,
                                                                       x, y,
                                                                       needsFocusEditor) != true) {
      navigateToComponent(sceneComponent.nlComponent, needsFocusEditor)
    }
  }

  override fun getCursorWhenNoInteraction(@SwingCoordinate mouseX: Int,
                                          @SwingCoordinate mouseY: Int,
                                          @JdkConstants.InputEventMask modifiersEx: Int): Cursor? {
    val sceneView = surface.getSceneView(mouseX, mouseY)
    if (sceneView != null) {
      // Check if the mouse position is at the bottom-right corner of sceneView.
      if (isInResizeZone(sceneView, mouseX, mouseY)) {
        return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
      }
    }
    return super.getCursorWhenNoInteraction(mouseX, mouseY, modifiersEx)
  }
}
