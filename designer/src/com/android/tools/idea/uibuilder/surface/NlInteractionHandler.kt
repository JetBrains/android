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
import com.android.tools.idea.common.surface.navigateToComponent
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.model.viewGroupHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.JdkConstants
import java.awt.Cursor
import java.awt.Rectangle

class NlInteractionHandler(private val surface: DesignSurface): InteractionHandlerBase(surface) {
  private val scope = AndroidCoroutineScope(surface)

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

  /**
   * Handles a click in a preview. The click is handled asynchronously since finding the component to navigate might be a
   * slow operation.
   */
  private fun clickPreview(@SwingCoordinate x: Int, @SwingCoordinate y: Int, needsFocusEditor: Boolean) {
    val sceneView = surface.getSceneViewAtOrPrimary(x, y) ?: return
    val androidX = Coordinates.getAndroidXDip(sceneView, x)
    val androidY = Coordinates.getAndroidYDip(sceneView, y)
    val navHandler = (surface as NlDesignSurface).navigationHandler ?: return
    val scene = sceneView.scene
    scope.launch(workerThread) {
      val sceneComponent = scene.findComponent(sceneView.context, androidX, androidY) ?: return@launch

      if (!navHandler.handleNavigate(sceneView,
                                     sceneComponent,
                                     x, y,
                                     needsFocusEditor)) {
        navigateToComponent(sceneComponent.nlComponent, needsFocusEditor)
      }
    }
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
