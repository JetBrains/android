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
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneInteraction
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.Interaction
import com.android.tools.idea.common.surface.InteractionInformation
import com.android.tools.idea.common.surface.InteractionNonInputEvent
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.SceneViewPanel
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.common.surface.navigateToComponent
import com.android.tools.idea.common.surface.selectComponent
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.android.tools.idea.uibuilder.surface.NlInteractionHandler
import java.awt.MouseInfo
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.JdkConstants

/**
 * [InteractionHandler] mainly based in [NlInteractionHandler], but with some extra code navigation
 * capabilities. When [isSelectionEnabled] returns true, Preview selection capabilities are also
 * added, affecting the navigation logic.
 */
class NavigatingInteractionHandler(
  private val surface: DesignSurface<*>,
  private val navigationHandler: NavigationHandler,
  private val isSelectionEnabled: () -> Boolean = { false },
) : NlInteractionHandler(surface) {

  private val scope = AndroidCoroutineScope(surface)

  override fun singleClick(x: Int, y: Int, modifiersEx: Int) {
    // When the selection capabilities are enabled and a Shift-click (single or double) happens,
    // then no navigation will happen. Only selection may be affected (see
    // mouseReleaseWhenNoInteraction)
    val isToggle = isSelectionEnabled() && isShiftDown(modifiersEx)
    if (!isToggle) {
      // Highlight the clicked widget but keep focus in DesignSurface.
      clickPreview(x, y, false)
    }
  }

  override fun keyPressedWithoutInteraction(keyEvent: KeyEvent): Interaction? {
    val componentToSceneView: MutableMap<NlComponent, SceneView> = mutableMapOf()
    for (sceneView in surface.sceneViews) {
      if (sceneView.firstComponent != null) {
        componentToSceneView.put(sceneView.firstComponent!!, sceneView)
      }
    }
    var selectedComponent =
      componentToSceneView.keys
        .filter { componentToSceneView[it]!!.selectionModel.isSelected(it) }
        .firstOrNull()
    if (selectedComponent == null) {
      return super.keyPressedWithoutInteraction(keyEvent)
    }

    val otherComponentsInView =
      componentToSceneView.entries
        .filter { !it.key.equals(selectedComponent) }
        // Filter components that are in closed sections of the grid view
        .filter { it.value.x > -1 && it.value.y > -1 }
        .mapNotNull { it.key }

    when (keyEvent.keyCode) {
      KeyEvent.VK_LEFT ->
        selectComponentToTheLeft(selectedComponent, otherComponentsInView, componentToSceneView)
      KeyEvent.VK_UP ->
        selectComponentToTheTop(selectedComponent, otherComponentsInView, componentToSceneView)
      KeyEvent.VK_RIGHT ->
        selectComponentToTheRight(selectedComponent, otherComponentsInView, componentToSceneView)
      KeyEvent.VK_DOWN ->
        selectComponentToTheBottom(selectedComponent, otherComponentsInView, componentToSceneView)
    }
    surface.repaint()
    return super.keyPressedWithoutInteraction(keyEvent)
  }

  override fun doubleClick(x: Int, y: Int, modifiersEx: Int) {
    // When the selection capabilities are enabled and a Shift-click (single or double) happens,
    // then
    // no navigation will happen. Only selection may be affected (see mouseReleaseWhenNoInteraction)
    val isToggle = isSelectionEnabled() && isShiftDown(modifiersEx)
    if (!isToggle) {
      // Navigate the caret to the clicked widget and focus on text editor.
      clickPreview(x, y, true)
    }
  }

  override fun popupMenuTrigger(mouseEvent: MouseEvent) {
    // The logic here is very similar to the one in InteractionHandlerBase, but some small
    // adjustments are
    // needed for the Preview selection logic to work properly.
    val x = mouseEvent.x
    val y = mouseEvent.y
    val sceneView = surface.getSceneViewAt(x, y)
    if (sceneView != null) {
      val component = sceneView.sceneManager.model.treeReader.components.firstOrNull()
      if (isSelectionEnabled() && component != null) {
        val wasSelected = sceneView.selectionModel.isSelected(component)
        sceneView.selectComponent(component, allowToggle = false, ignoreIfAlreadySelected = true)
        // If the selection state changed, then force a hover state update
        if (wasSelected != sceneView.selectionModel.isSelected(component)) {
          forceHoverUpdate(sceneView, x, y)
        }
      }
      var targetComponent: JComponent? = null
      // Set the SceneViewPeerPanel as targetComponent, so the DataContext is specific to
      // a sceneView, not generic as the DesignSurface.
      (surface.interactionPane as? SceneViewPanel)
        ?.components
        ?.filterIsInstance<SceneViewPeerPanel>()
        ?.firstOrNull { it.sceneView == sceneView }
        ?.let { targetComponent = it }
      val actions = surface.actionManager.getPopupMenuActions(component)
      surface.showPopup(mouseEvent, actions, "Preview", targetComponent)
    } else {
      surface.selectionModel.clear()
    }
  }

  override fun mouseReleaseWhenNoInteraction(
    @SwingCoordinate x: Int,
    @SwingCoordinate y: Int,
    @JdkConstants.InputEventMask modifiersEx: Int,
  ) {
    if (isSelectionEnabled()) {
      val sceneView = surface.getSceneViewAt(x, y)
      if (sceneView != null) {
        val component = sceneView.sceneManager.model.treeReader.components.firstOrNull()
        // If this is not a "toggle" click and the preview is already selected,
        // then it is a navigation click, and shouldn't impact the selected components.
        val allowToggle = isShiftDown(modifiersEx)
        if (component != null) {
          val wasSelected = sceneView.selectionModel.isSelected(component)
          sceneView.selectComponent(component, allowToggle, ignoreIfAlreadySelected = !allowToggle)
          // If the selection state changed, then force a hover state update
          if (wasSelected != sceneView.selectionModel.isSelected(component)) {
            forceHoverUpdate(sceneView, x, y)
          }
        }
      } else {
        surface.selectionModel.clear()
      }
    }
  }

  override fun createInteractionOnPressed(
    @SwingCoordinate mouseX: Int,
    @SwingCoordinate mouseY: Int,
    modifiersEx: Int,
  ): Interaction? {
    val interaction = super.createInteractionOnPressed(mouseX, mouseY, modifiersEx)
    // SceneInteractions must be ignored as they impact the selection model following
    // a different logic that the one used by this interaction handler.
    if (isSelectionEnabled() && interaction is SceneInteraction) {
      interaction.cancel(
        InteractionNonInputEvent(InteractionInformation(mouseX, mouseY, modifiersEx))
      )
      return null
    }
    return interaction
  }

  override fun mouseExited() {
    val mousePosition = MouseInfo.getPointerInfo().location
    SwingUtilities.convertPointFromScreen(mousePosition, surface.interactionPane)
    // Exiting to a popup from a point within the surface is not considered as exiting the surface.
    // This is needed to keep the hover state of a preview when interacting with its right-click
    // pop-up.
    if (!surface.interactionPane.contains(mousePosition.x, mousePosition.y)) {
      super.mouseExited()
    }
  }

  private fun selectComponentToTheLeft(
    selectedComponent: NlComponent,
    otherComponents: List<NlComponent>,
    componentToSceneView: MutableMap<NlComponent, SceneView>,
  ) {
    var toSelectInCurrentRow =
      otherComponents
        .filter { componentToSceneView[it]!!.y == componentToSceneView[selectedComponent]!!.y }
        .filter { componentToSceneView[it]!!.x < componentToSceneView[selectedComponent]!!.x }
    if ((toSelectInCurrentRow.isNotEmpty()))
      return componentToSceneView[toSelectInCurrentRow.last()]!!.selectComponent(
        toSelectInCurrentRow.last(),
        allowToggle = false,
        ignoreIfAlreadySelected = true,
      )
    var toSelectPreviousRow =
      otherComponents.filter {
        componentToSceneView[it]!!.y < componentToSceneView[selectedComponent]!!.y
      }
    if (toSelectPreviousRow.isNotEmpty()) {
      componentToSceneView[toSelectPreviousRow.last()]!!.selectComponent(
        toSelectPreviousRow.lastOrNull(),
        allowToggle = false,
        ignoreIfAlreadySelected = true,
      )
    }
  }

  private fun selectComponentToTheRight(
    selectedComponent: NlComponent,
    otherComponents: List<NlComponent>,
    componentToSceneView: MutableMap<NlComponent, SceneView>,
  ) {
    var toSelectInCurrentRow =
      otherComponents
        .filter { componentToSceneView[it]!!.x > componentToSceneView[selectedComponent]!!.x }
        .filter { componentToSceneView[it]!!.y == componentToSceneView[selectedComponent]!!.y }
    if ((toSelectInCurrentRow.isNotEmpty()))
      return componentToSceneView[toSelectInCurrentRow.first()]!!.selectComponent(
        toSelectInCurrentRow.first(),
        allowToggle = false,
        ignoreIfAlreadySelected = true,
      )
    var toSelectNextRow =
      otherComponents.filter {
        componentToSceneView[it]!!.y > componentToSceneView[selectedComponent]!!.y
      }
    if (toSelectNextRow.isNotEmpty()) {
      componentToSceneView[toSelectNextRow.first()]!!.selectComponent(
        toSelectNextRow.first(),
        allowToggle = false,
        ignoreIfAlreadySelected = true,
      )
    }
  }

  private fun selectComponentToTheBottom(
    selectedComponent: NlComponent,
    otherComponents: List<NlComponent>,
    componentToSceneView: MutableMap<NlComponent, SceneView>,
  ) {
    var toSelect =
      otherComponents
        .filter { componentToSceneView[it]!!.x == componentToSceneView[selectedComponent]!!.x }
        .filter { componentToSceneView[it]!!.y > componentToSceneView[selectedComponent]!!.y }

    if (toSelect.isNotEmpty())
      componentToSceneView[toSelect.first()]!!.selectComponent(
        toSelect.first(),
        allowToggle = false,
        ignoreIfAlreadySelected = true,
      )
  }

  private fun selectComponentToTheTop(
    selectedComponent: NlComponent,
    otherComponents: List<NlComponent>,
    componentToSceneView: MutableMap<NlComponent, SceneView>,
  ) {
    var toSelect =
      otherComponents
        .filter { componentToSceneView[it]!!.x == componentToSceneView[selectedComponent]!!.x }
        .filter { componentToSceneView[it]!!.y < componentToSceneView[selectedComponent]!!.y }
    if (toSelect.isNotEmpty())
      componentToSceneView[toSelect.last()]!!.selectComponent(
        toSelect.last(),
        allowToggle = false,
        ignoreIfAlreadySelected = true,
      )
  }

  /**
   * Force a hover state update by performing the following steps:
   * 1. Update the sceneManager to make sure that the scene's root and structure is up-to-date.
   * 2. Make sure that all SceneComponents contain their layout and positioning information.
   * 3. Simulate a hover
   */
  private fun forceHoverUpdate(sceneView: SceneView, x: Int, y: Int) {
    sceneView.sceneManager.update()
    sceneView.scene.root?.layout(sceneView.context, System.currentTimeMillis())
    this.hoverWhenNoInteraction(x, y, 0)
  }

  /**
   * Handles a click in a preview. The click is handled asynchronously since finding the component
   * to navigate might be a slow operation.
   */
  private fun clickPreview(
    @SwingCoordinate x: Int,
    @SwingCoordinate y: Int,
    needsFocusEditor: Boolean,
  ) {
    val sceneView = surface.getSceneViewAt(x, y) ?: return
    val androidX = Coordinates.getAndroidXDip(sceneView, x)
    val androidY = Coordinates.getAndroidYDip(sceneView, y)
    val scene = sceneView.scene
    scope.launch(AndroidDispatchers.workerThread) {
      if (!navigationHandler.handleNavigateWithCoordinates(sceneView, x, y, needsFocusEditor)) {
        val sceneComponent =
          scene.findComponent(sceneView.context, androidX, androidY) ?: return@launch
        navigateToComponent(sceneComponent.nlComponent, needsFocusEditor)
      }
    }
  }

  private fun isShiftDown(modifiersEx: Int) = (modifiersEx and (InputEvent.SHIFT_DOWN_MASK)) != 0
}
