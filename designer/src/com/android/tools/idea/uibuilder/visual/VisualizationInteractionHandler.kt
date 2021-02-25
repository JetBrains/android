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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceShortcut
import com.android.tools.idea.common.surface.Interaction
import com.android.tools.idea.common.surface.InteractionHandler
import com.android.tools.idea.uibuilder.editor.LayoutNavigationManager
import com.android.tools.idea.uibuilder.surface.PanInteraction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import org.intellij.lang.annotations.JdkConstants
import java.awt.Component
import java.awt.Cursor
import java.awt.dnd.DropTargetDragEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

class VisualizationInteractionHandler(private val surface: DesignSurface,
                                      private val getModelsProviderFunc: () -> VisualizationModelsProvider) : InteractionHandler {
  override fun createInteractionOnPressed(@SwingCoordinate mouseX: Int,
                                          @SwingCoordinate mouseY: Int,
                                          @JdkConstants.InputEventMask modifiersEx: Int): Interaction? = null

  override fun createInteractionOnDrag(@SwingCoordinate mouseX: Int,
                                       @SwingCoordinate mouseY: Int,
                                       @JdkConstants.InputEventMask modifiersEx: Int): Interaction? = null

  override fun createInteractionOnDragEnter(dragEvent: DropTargetDragEvent): Interaction? = null

  override fun createInteractionOnMouseWheelMoved(mouseWheelEvent: MouseWheelEvent): Interaction? = null

  override fun mouseReleaseWhenNoInteraction(@SwingCoordinate x: Int,
                                             @SwingCoordinate y: Int,
                                             @JdkConstants.InputEventMask modifiersEx: Int) = Unit

  override fun singleClick(@SwingCoordinate x: Int, @SwingCoordinate y: Int, @JdkConstants.InputEventMask modifiersEx: Int) = Unit

  override fun doubleClick(@SwingCoordinate x: Int, @SwingCoordinate y: Int, @JdkConstants.InputEventMask modifiersEx: Int) {
    val view = surface.getHoverSceneView(x, y) ?: return
    val sourceFile = surface.sceneManager?.model?.virtualFile ?: return
    val targetFile = view.sceneManager.model.virtualFile
    LayoutNavigationManager.getInstance(surface.project).pushFile(sourceFile, targetFile)
  }

  override fun hoverWhenNoInteraction(@SwingCoordinate mouseX: Int,
                                      @SwingCoordinate mouseY: Int,
                                      @JdkConstants.InputEventMask modifiersEx: Int) {
    val sceneView = surface.getHoverSceneView(mouseX, mouseY)
    if (sceneView != null) {
      val name = sceneView.sceneManager.model.configuration.toTooltips()
      surface.setDesignToolTip(name)
    }
    else {
      surface.setDesignToolTip(null)
    }
  }

  override fun popupMenuTrigger(mouseEvent: MouseEvent) {
    // For now only custom models mode has popup menu.
    val customModelsProvider = getModelsProviderFunc.invoke() as? CustomModelsProvider ?: return

    val mouseX = mouseEvent.x
    val mouseY = mouseEvent.y
    val sceneView = surface.getHoverSceneView(mouseX, mouseY) ?: return

    val hoveredManager = sceneView.sceneManager
    val primarySceneManager = surface.sceneManager

    val group = DefaultActionGroup().apply {
      // Do not allow to delete the default NlModel (which is the primary one)
      add(RemoveCustomModelAction(customModelsProvider, hoveredManager.model, hoveredManager != primarySceneManager))
      // TODO: add edit and copy options.
    }

    val actionManager = ActionManager.getInstance()
    val invoker = mouseEvent.source as? Component ?: surface

    if (group.childrenCount != 0) {
      val popupMenu = actionManager.createActionPopupMenu(ActionPlaces.POPUP, group)
      popupMenu.component.show(invoker, mouseEvent.x, mouseEvent.y)
    }
  }

  override fun getCursorWhenNoInteraction(@SwingCoordinate mouseX: Int,
                                          @SwingCoordinate mouseY: Int,
                                          @JdkConstants.InputEventMask modifiersEx: Int): Cursor? = null

  override fun keyPressedWithoutInteraction(keyEvent: KeyEvent): Interaction? {
    return if (keyEvent.keyCode == DesignSurfaceShortcut.PAN.keyCode) PanInteraction(surface) else null
  }

  override fun keyReleasedWithoutInteraction(keyEvent: KeyEvent) = Unit
}

private class RemoveCustomModelAction(val provider: CustomModelsProvider, val model: NlModel, val enabled: Boolean) :
  AnAction("Remove Configuration", "Remove a custom configuration", null) {

  override fun actionPerformed(e: AnActionEvent) = provider.removeCustomConfigurationAttributes(model)

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = enabled
    e.presentation.isVisible = true
    e.presentation.description = if (enabled) "" else "Cannot remove default preview"
  }
}
