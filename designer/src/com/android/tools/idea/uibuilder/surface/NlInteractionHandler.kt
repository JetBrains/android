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
import com.android.tools.idea.uibuilder.model.getLayoutHandler
import com.android.tools.idea.uibuilder.surface.interaction.CanvasResizeInteraction
import com.android.tools.idea.uibuilder.surface.interaction.MarqueeInteraction
import java.awt.Cursor
import java.awt.Rectangle
import org.intellij.lang.annotations.JdkConstants

open class NlInteractionHandler(private val surface: DesignSurface<*>) :
  InteractionHandlerBase(surface) {

  override fun createInteractionOnPressed(
    @SwingCoordinate mouseX: Int,
    @SwingCoordinate mouseY: Int,
    modifiersEx: Int,
  ): Interaction? {
    getViewInResizeZone(mouseX, mouseY)?.let { view ->
      val configuration = view.sceneManager.model.configuration
      return CanvasResizeInteraction(surface as NlDesignSurface, view as ScreenView, configuration)
    }

    val view = surface.getSceneViewAtOrPrimary(mouseX, mouseY) ?: return null
    val screenView = view as ScreenView

    val selectionModel = screenView.selectionModel
    var component = Coordinates.findComponent(screenView, mouseX, mouseY)
    if (component == null) {
      // If we cannot find an element where we clicked, try to use the first element currently
      // selected
      // (if any) to find the view group handler that may want to handle the mousePressed()
      // This allows us to correctly handle elements out of the bounds of the screen view.
      if (!selectionModel.isEmpty) {
        component = selectionModel.primary
      } else {
        return null
      }
    }

    var interaction: Interaction? = null

    // Give a chance to the current selection's parent handler
    if (!selectionModel.isEmpty) {
      val primary = screenView.selectionModel.primary
      val parent = primary?.parent
      if (parent != null) {
        val handler = parent.getLayoutHandler {}
        if (handler != null) {
          interaction = handler.createInteraction(screenView, mouseX, mouseY, primary)
        }
      }
    }

    if (interaction == null) {
      // Check if we have a ViewGroupHandler that might want
      // to handle the entire interaction
      val viewGroupHandler = component?.getLayoutHandler {}
      if (viewGroupHandler != null) {
        interaction = viewGroupHandler.createInteraction(screenView, mouseX, mouseY, component!!)
      }
    }

    if (interaction == null) {
      interaction = SceneInteraction(screenView)
    }
    return interaction
  }

  /**
   * Returns SceneView if it's focused mode and the given [mouseX] and [mouseY] coordinates are
   * within the resizing handle area. If resizing is disabled or the coordinates are outside the
   * resize handler area of SceneView, this method returns null.
   */
  private fun getViewInResizeZone(
    @SwingCoordinate mouseX: Int,
    @SwingCoordinate mouseY: Int,
  ): SceneView? {
    if (surface.sceneViews.size != 1) {
      // Only available in focused mode
      return null
    }
    // if we are hovering any scene return immediately
    val sceneViewUnderMouse = surface.getSceneViewAt(mouseX, mouseY)
    if (sceneViewUnderMouse != null) {
      return null
    }
    val sceneView = surface.sceneViews.single()

    return sceneView.takeIf { sceneView ->
      if (!sceneView.isResizeable || !sceneView.scene.isResizeAvailable) {
        // Resizing is disabled
        return@takeIf false
      }

      val size = sceneView.scaledContentSize
      // Check if the mouse position is in the bottom-right corner of sceneView.
      val resizeZone =
        Rectangle(
          sceneView.x + size.width,
          sceneView.y + size.height,
          NlConstants.RESIZING_HOVERING_SIZE,
          NlConstants.RESIZING_HOVERING_SIZE,
        )
      resizeZone.contains(mouseX, mouseY)
    }
  }

  override fun createInteractionOnDrag(
    @SwingCoordinate mouseX: Int,
    @SwingCoordinate mouseY: Int,
    @JdkConstants.InputEventMask modifiersEx: Int,
  ): Interaction? {
    if (surface.getSceneViewAt(mouseX, mouseY) == null) {
      val focusedSceneView = surface.focusedSceneView ?: return null
      return MarqueeInteraction(focusedSceneView) { surface.repaint() }
    }
    return null
  }

  override fun getCursorWhenNoInteraction(
    @SwingCoordinate mouseX: Int,
    @SwingCoordinate mouseY: Int,
    @JdkConstants.InputEventMask modifiersEx: Int,
  ): Cursor? {
    if (getViewInResizeZone(mouseX, mouseY) != null) {
      return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
    }

    return super.getCursorWhenNoInteraction(mouseX, mouseY, modifiersEx)
  }
}
