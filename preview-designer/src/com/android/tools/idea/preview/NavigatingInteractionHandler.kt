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
import com.android.tools.idea.common.editor.showPopup
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneInteraction
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.Interaction
import com.android.tools.idea.common.surface.InteractionInformation
import com.android.tools.idea.common.surface.InteractionNonInputEvent
import com.android.tools.idea.common.surface.navigateToComponent
import com.android.tools.idea.common.surface.selectComponent
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlInteractionHandler
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.JdkConstants
import java.awt.event.InputEvent
import java.awt.event.MouseEvent

/**
 * [InteractionHandler] mainly based in [NlInteractionHandler], but with some extra code navigation
 * capabilities.
 * When [isSelectionEnabled] returns true, Preview selection capabilities are also added, affecting
 * the navigation logic.
 */
class NavigatingInteractionHandler(private val surface: DesignSurface<*>,
                                   private val isSelectionEnabled: () -> Boolean = { false }) :
  NlInteractionHandler(surface) {

  private val scope = AndroidCoroutineScope(surface)
  override fun singleClick(x: Int, y: Int, modifiersEx: Int) {
    // When the selection capabilities are enabled and a Shift-click (single or double) happens, then
    // no navigation will happen. Only selection may be affected (see mouseReleaseWhenNoInteraction)
    val isToggle = isSelectionEnabled() && isShiftDown(modifiersEx)
    if (!isToggle) {
      // Highlight the clicked widget but keep focus in DesignSurface.
      clickPreview(x, y, false)
    }
  }

  override fun doubleClick(x: Int, y: Int, modifiersEx: Int) {
    // When the selection capabilities are enabled and a Shift-click (single or double) happens, then
    // no navigation will happen. Only selection may be affected (see mouseReleaseWhenNoInteraction)
    val isToggle = isSelectionEnabled() && isShiftDown(modifiersEx)
    if (!isToggle) {
      // Navigate the caret to the clicked widget and focus on text editor.
      clickPreview(x, y, true)
    }
  }

  override fun popupMenuTrigger(mouseEvent: MouseEvent) {
    // The logic here is very similar to the one in InteractionHandlerBase, but some small adjustments are
    // needed for the Preview selection logic to work properly.
    val x = mouseEvent.x
    val y = mouseEvent.y
    val sceneView = surface.getSceneViewAt(x, y)
    if (sceneView != null) {
      val component = sceneView.sceneManager.model.components.firstOrNull()
      if (isSelectionEnabled() && component != null) {
        sceneView.selectComponent(component, allowToggle = false, ignoreIfAlreadySelected = true)
      }
      val actions = surface.actionManager.getPopupMenuActions(component)
      surface.showPopup(mouseEvent, actions, "Preview")
    }
    else {
      surface.selectionModel.clear()
    }
  }

  override fun mouseReleaseWhenNoInteraction(@SwingCoordinate x: Int,
                                             @SwingCoordinate y: Int,
                                             @JdkConstants.InputEventMask modifiersEx: Int) {
    if (isSelectionEnabled()) {
      val sceneView = surface.getSceneViewAt(x, y)
      if (sceneView != null) {
        // If this is not a "toggle" click and the preview is already selected,
        // then it is a navigation click, and shouldn't impact the selected components.
        val allowToggle = isShiftDown(modifiersEx)
        if (sceneView.sceneManager.model.components.isNotEmpty()) {
          sceneView.selectComponent(
            sceneView.sceneManager.model.components[0],
            allowToggle,
            ignoreIfAlreadySelected = !allowToggle
          )
        }
      }
      else {
        surface.selectionModel.clear()
      }
    }
  }

  override fun createInteractionOnPressed(@SwingCoordinate mouseX: Int, @SwingCoordinate mouseY: Int, modifiersEx: Int): Interaction? {
    val interaction = super.createInteractionOnPressed(mouseX, mouseY, modifiersEx)
    // SceneInteractions must be ignored as they impact the selection model following
    // a different logic that the one used by this interaction handler.
    if (isSelectionEnabled() && interaction is SceneInteraction) {
      interaction.cancel(InteractionNonInputEvent(InteractionInformation(mouseX, mouseY, modifiersEx)))
      return null
    }
    return interaction
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
    val sceneView = surface.getSceneViewAt(x, y) ?: return
    val androidX = Coordinates.getAndroidXDip(sceneView, x)
    val androidY = Coordinates.getAndroidYDip(sceneView, y)
    val navHandler = (surface as NlDesignSurface).navigationHandler ?: return
    val scene = sceneView.scene
    scope.launch(AndroidDispatchers.workerThread) {
      if (!navHandler.handleNavigate(sceneView, x, y, needsFocusEditor)) {
        val sceneComponent = scene.findComponent(sceneView.context, androidX, androidY) ?: return@launch
        navigateToComponent(sceneComponent.nlComponent, needsFocusEditor)
      }
    }
  }

  private fun isShiftDown(modifiersEx: Int) = (modifiersEx and (InputEvent.SHIFT_DOWN_MASK)) != 0
}
