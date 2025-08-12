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
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.android.tools.idea.uibuilder.surface.NlInteractionHandler
import com.android.tools.idea.uibuilder.surface.PreviewNavigatableWrapper
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import java.awt.MouseInfo
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.JdkConstants

/**
 * [InteractionHandler] mainly based in [NlInteractionHandler], but with some extra code navigation
 * capabilities. When [isSelectionEnabled] is true, Preview selection capabilities are also added,
 * affecting the navigation logic.
 */
class NavigatingInteractionHandler(
  private val surface: DesignSurface<*>,
  private val navigationHandler: NavigationHandler,
  private val isSelectionEnabled: Boolean = false,
) : NlInteractionHandler(surface) {

  private val scope = AndroidCoroutineScope(surface)

  private var popUpComponent: ActionPopupMenu? = null

  override fun singleClick(mouseEvent: MouseEvent, modifiersEx: Int) {
    // When the selection capabilities are enabled and a Shift-click (single or double) happens,
    // then no navigation will happen. Only selection may be affected (see
    // mouseReleaseWhenNoInteraction)
    val isToggle = isSelectionEnabled && isShiftDown(modifiersEx)
    if (!isToggle) {
      // Highlight the clicked widget but keep focus in DesignSurface.
      clickPreview(mouseEvent, false, modifiersEx)
    }
  }

  override fun keyPressedWithoutInteraction(keyEvent: KeyEvent): Interaction? {
    val componentToSceneView: MutableMap<NlComponent, SceneView> = mutableMapOf()
    for (sceneView in surface.sceneViews) {
      sceneView.firstComponent?.let { componentToSceneView[it] = sceneView }
    }
    val selectedComponent =
      componentToSceneView.keys.firstOrNull {
        componentToSceneView[it]!!.selectionModel.isSelected(it)
      } ?: return super.keyPressedWithoutInteraction(keyEvent)

    val otherComponentsInView =
      componentToSceneView.entries
        .filter { it.key != selectedComponent }
        // Filter components that are in closed sections of the grid view
        .filter { it.value.x >= 0 && it.value.y >= 0 }
        .map { it.key }

    when (keyEvent.keyCode) {
      KeyEvent.VK_LEFT ->
        selectComponentToTheLeft(selectedComponent, otherComponentsInView, componentToSceneView)
      KeyEvent.VK_UP ->
        selectComponentAbove(selectedComponent, otherComponentsInView, componentToSceneView)
      KeyEvent.VK_RIGHT ->
        selectComponentToTheRight(selectedComponent, otherComponentsInView, componentToSceneView)
      KeyEvent.VK_DOWN ->
        selectComponentBelow(selectedComponent, otherComponentsInView, componentToSceneView)
    }
    surface.repaint()
    return super.keyPressedWithoutInteraction(keyEvent)
  }

  override fun doubleClick(mouseEvent: MouseEvent, modifiersEx: Int) {
    // When the selection capabilities are enabled and a Shift-click (single or double) happens,
    // then no navigation will happen. Only selection may be affected (see
    // mouseReleaseWhenNoInteraction)
    val isToggle = isSelectionEnabled && isShiftDown(modifiersEx)
    if (!isToggle) {
      // Navigate the caret to the clicked widget and focus on text editor.
      clickPreview(mouseEvent, true, modifiersEx)
    }
  }

  override fun popupMenuTrigger(mouseEvent: MouseEvent) {
    // The logic here is very similar to the one in InteractionHandlerBase, but some small
    // adjustments are needed for the Preview selection logic to work properly.
    val x = mouseEvent.x
    val y = mouseEvent.y
    val sceneView = surface.getSceneViewAt(x, y)
    if (sceneView != null) {
      val component = sceneView.sceneManager.model.treeReader.components.firstOrNull()
      if (isSelectionEnabled && component != null) {
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
      popUpComponent = surface.showPopup(mouseEvent, actions, "Preview", targetComponent)
    } else {
      surface.selectionModel.clear()
    }
  }

  /** Resizing is allowed only in Focus mode. */
  override fun getViewInResizeZone(mouseX: Int, mouseY: Int): SceneView? {
    return super.getViewInResizeZone(mouseX, mouseY)?.takeIf { sceneView ->
      sceneView.sceneManager.model.dataProvider?.getData(PreviewModeManager.KEY)?.mode?.value is
        PreviewMode.Focus
    }
  }

  override fun mouseReleaseWhenNoInteraction(
    @SwingCoordinate x: Int,
    @SwingCoordinate y: Int,
    @JdkConstants.InputEventMask modifiersEx: Int,
  ) {
    if (isSelectionEnabled) {
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
    if (isSelectionEnabled && interaction is SceneInteraction) {
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
    if (popUpComponent?.component?.isShowing != true) {
      super.mouseExited()
    }
  }

  override fun onCaretMoved(lineNumber: Int) {
    for (sceneView in surface.sceneViews) {
      scope.launch(Dispatchers.Default) {
        sceneView!!.clearHighlight()
        var rectanglesToHightlight = navigationHandler.findBoundsOfComponents(sceneView, lineNumber)
        rectanglesToHightlight.forEach({
          val x = Coordinates.getSwingX(sceneView, it.x)
          val y = Coordinates.getSwingY(sceneView, it.y)
          val width = Coordinates.getSwingDimension(sceneView, it.width)
          val height = Coordinates.getSwingDimension(sceneView, it.height)
          sceneView!!.highlighBox(x, y, width, height)
        })
      }
    }
    surface.repaint()
  }

  private fun selectComponent(
    comp: NlComponent,
    componentToSceneView: Map<NlComponent, SceneView>,
  ) {
    componentToSceneView[comp]!!.selectComponent(
      component = comp,
      allowToggle = false,
      ignoreIfAlreadySelected = true,
    )
  }

  private fun selectComponentToTheLeft(
    selectedComponent: NlComponent,
    otherComponents: List<NlComponent>,
    componentToSceneView: Map<NlComponent, SceneView>,
  ) {
    val selectedSceneView = componentToSceneView[selectedComponent]!!
    otherComponents
      // First, try to select the right-most component located on the left of the selected
      // component, in the same row.
      .filter { componentToSceneView[it]!!.isLeftOf(selectedSceneView) }
      .maxByOrNull { componentToSceneView[it]!!.x }
      ?.let {
        selectComponent(it, componentToSceneView)
        return@selectComponentToTheLeft
      }

    // Then, try to select the last component of the previous row.
    otherComponents
      .lastOrNull { componentToSceneView[it]!!.y < selectedSceneView.y }
      ?.let { selectComponent(it, componentToSceneView) }
  }

  private fun selectComponentToTheRight(
    selectedComponent: NlComponent,
    otherComponents: List<NlComponent>,
    componentToSceneView: Map<NlComponent, SceneView>,
  ) {
    val selectedSceneView = componentToSceneView[selectedComponent]!!
    // First, try to select the left-most component located on the right of the selected
    // component, in the same row.
    otherComponents
      .filter { componentToSceneView[it]!!.isRightOf(selectedSceneView) }
      .minByOrNull { componentToSceneView[it]!!.x }
      ?.let {
        selectComponent(it, componentToSceneView)
        return@selectComponentToTheRight
      }

    // Then, try to select the first component of the next row.
    otherComponents
      .firstOrNull { componentToSceneView[it]!!.y > selectedSceneView.y }
      ?.let { selectComponent(it, componentToSceneView) }
  }

  private fun selectComponentBelow(
    selectedComponent: NlComponent,
    otherComponents: List<NlComponent>,
    componentToSceneView: Map<NlComponent, SceneView>,
  ) {
    val selectedSceneView = componentToSceneView[selectedComponent]!!
    // Select the closest component located below the selected component, if there is one.
    otherComponents
      .filter { componentToSceneView[it]!!.y > selectedSceneView.y }
      .minByOrNull {
        val sceneView = componentToSceneView[it]!!
        val dx = sceneView.x - selectedSceneView.x
        val dy = sceneView.y - selectedSceneView.y
        dx * dx + dy * dy
      }
      ?.let { selectComponent(it, componentToSceneView) }
  }

  private fun selectComponentAbove(
    selectedComponent: NlComponent,
    otherComponents: List<NlComponent>,
    componentToSceneView: Map<NlComponent, SceneView>,
  ) {
    val selectedSceneView = componentToSceneView[selectedComponent]!!
    // Select the closest component located on top of the selected component, if there is one.
    otherComponents
      .filter { componentToSceneView[it]!!.y < selectedSceneView.y }
      .minByOrNull {
        val sceneView = componentToSceneView[it]!!
        val dx = sceneView.x - selectedSceneView.x
        val dy = sceneView.y - selectedSceneView.y
        dx * dx + dy * dy
      }
      ?.let { selectComponent(it, componentToSceneView) }
  }

  private fun SceneView.isRightOf(other: SceneView): Boolean = y == other.y && x > other.x

  private fun SceneView.isLeftOf(other: SceneView): Boolean = y == other.y && x < other.x

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
  private fun clickPreview(mouseEvent: MouseEvent, needsFocusEditor: Boolean, modifiersEx: Int) {
    val x = mouseEvent.x
    val y = mouseEvent.y
    val sceneView = surface.getSceneViewAt(x, y) ?: return
    val androidX = Coordinates.getAndroidXDip(sceneView, x)
    val androidY = Coordinates.getAndroidYDip(sceneView, y)
    val isOptionDown = isOptionDown(modifiersEx)
    val scene = sceneView.scene
    scope.launch(AndroidDispatchers.workerThread) {
      val navigatables =
        navigationHandler.findNavigatablesWithCoordinates(
          sceneView,
          x,
          y,
          needsFocusEditor,
          isOptionDown,
        )

      if (isOptionDown && navigatables.isNotEmpty()) {
        // Open a pop up menu with all components under coordinates
        val actions = createActionGroup(sceneView, navigatables)
        withContext(uiThread) { surface.showPopup(mouseEvent, actions, "Navigatables") }
        return@launch
      }

      val navigated =
        navigatables.firstOrNull()?.let {
          navigationHandler.navigateTo(sceneView, it.navigatable!!, needsFocusEditor)
        }
        ?: run {
          if (needsFocusEditor) {
            // Only allow default navigation when double clicking since it might take us to a
            // different file
            navigationHandler.handleNavigate(sceneView, true)
          }
          return@run false
        }
      if (!navigated) {
        val sceneComponent =
          scene.findComponent(sceneView.context, androidX, androidY) ?: return@launch
        withContext(uiThread) { navigateToComponent(sceneComponent.nlComponent, needsFocusEditor) }
      }
    }
  }

  // Create an action group with actions to navigate to components. This will be called when Option
  // + clicking on component.
  private fun createActionGroup(
    sceneView: SceneView,
    navigatables: List<PreviewNavigatableWrapper>,
  ): DefaultActionGroup {
    val defaultGroup = DefaultActionGroup()
    navigatables.forEach {
      val name = it.name
      it.navigatable?.let {
        defaultGroup.addAction(
          object : AnAction(name) {
            override fun actionPerformed(e: AnActionEvent) {
              scope.launch(AndroidDispatchers.workerThread) {
                navigationHandler.navigateTo(sceneView, it, false)
              }
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
              return ActionUpdateThread.BGT
            }
          }
        )
      }
    }
    return defaultGroup
  }

  // TODO(b/257534922): Make sure that this modifier works for linux as well.
  private fun isOptionDown(modifiersEx: Int) = (modifiersEx and (InputEvent.ALT_DOWN_MASK)) != 0

  private fun isShiftDown(modifiersEx: Int) = (modifiersEx and (InputEvent.SHIFT_DOWN_MASK)) != 0
}