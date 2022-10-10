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
import com.android.tools.idea.common.scene.SceneInteraction
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.Interaction
import com.android.tools.idea.common.surface.InteractionHandlerBase
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.model.viewGroupHandler
import org.intellij.lang.annotations.JdkConstants
import java.awt.Cursor
import java.awt.Rectangle

open class NlInteractionHandler(private val surface: DesignSurface<*>): InteractionHandlerBase(surface) {

  override fun createInteractionOnPressed(@SwingCoordinate mouseX: Int, @SwingCoordinate mouseY: Int, modifiersEx: Int): Interaction? {
    val view = surface.getSceneViewAtOrPrimary(mouseX, mouseY) ?: return null
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
    if (surface.getSceneViewAt(mouseX, mouseY) == null) {
      val focusedSceneView = surface.focusedSceneView ?: return null
      return MarqueeInteraction(focusedSceneView)
    }
    return null
  }

  override fun getCursorWhenNoInteraction(@SwingCoordinate mouseX: Int,
                                          @SwingCoordinate mouseY: Int,
                                          @JdkConstants.InputEventMask modifiersEx: Int): Cursor? {
    val sceneView = surface.getSceneViewAtOrPrimary(mouseX, mouseY)
    if (sceneView != null) {
      // Check if the mouse position is at the bottom-right corner of sceneView.
      if (isInResizeZone(sceneView, mouseX, mouseY)) {
        return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
      }
    }
    return super.getCursorWhenNoInteraction(mouseX, mouseY, modifiersEx)
  }
}
