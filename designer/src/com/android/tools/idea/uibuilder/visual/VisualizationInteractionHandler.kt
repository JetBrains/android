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

import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.editor.DesignToolsSplitEditor
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceShortcut
import com.android.tools.idea.common.surface.Interaction
import com.android.tools.idea.common.surface.InteractionHandler
import com.android.tools.idea.common.surface.navigateToComponent
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.uibuilder.editor.LayoutNavigationManager
import com.android.tools.idea.uibuilder.surface.PanInteraction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import org.intellij.lang.annotations.JdkConstants
import java.awt.Component
import java.awt.Cursor
import java.awt.dnd.DropTargetDragEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

class VisualizationInteractionHandler(private val surface: DesignSurface<*>,
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

  override fun singleClick(@SwingCoordinate x: Int, @SwingCoordinate y: Int, @JdkConstants.InputEventMask modifiersEx: Int) {
    val view = surface.getSceneViewAt(x, y) ?: return
    val xDp = Coordinates.getAndroidXDip(view, x)
    val yDp = Coordinates.getAndroidYDip(view, y)
    val clickedComponent = view.scene.findComponent(view.context, xDp, yDp) ?: return
    navigateToComponent(clickedComponent.nlComponent, false)
  }

  override fun doubleClick(@SwingCoordinate x: Int, @SwingCoordinate y: Int, @JdkConstants.InputEventMask modifiersEx: Int) {
    val view = surface.getSceneViewAt(x, y) ?: return

    val currentEditor = FileEditorManager.getInstance(surface.project).selectedEditor ?: return
    val sourceFile = currentEditor.file ?: return
    val targetFile = view.sceneManager.model.virtualFile

    if (sourceFile == targetFile) {
      // Same file, just apply the config to it.
      val surfaceInLayoutEditor = (currentEditor as? DesignToolsSplitEditor)?.designerEditor?.component?.surface
      val configInLayoutEditor = surfaceInLayoutEditor?.models?.firstOrNull()?.configuration
      if (configInLayoutEditor != null) {
        applyConfiguration(configInLayoutEditor, view.configuration)
        surfaceInLayoutEditor.zoomToFit()
      }
    }
    else {
      // Open another file, or switch to it if it has been open. Then, apply the config to it.
      LayoutNavigationManager.getInstance(surface.project).pushFile(sourceFile, targetFile) { newEditor ->
        val surfaceInDestinationEditor = (newEditor as? DesignToolsSplitEditor)?.designerEditor?.component?.surface
        val configInDestinationEditor = surfaceInDestinationEditor?.models?.firstOrNull()?.configuration
        if (configInDestinationEditor != null) {
          applyConfiguration(configInDestinationEditor, view.configuration)
          surfaceInDestinationEditor.zoomToFit()
        }
      }
    }
  }

  private fun applyConfiguration(destination: Configuration, source: Configuration) {
    with(destination) {
      startBulkEditing()
      setDevice(source.device, true)
      deviceState = source.deviceState
      nightMode = source.nightMode
      uiMode = source.uiMode
      locale = source.locale
      finishBulkEditing()
    }
  }

  override fun zoom(type: ZoomType, mouseX: Int, mouseY: Int) {
    surface.zoom(type, mouseX, mouseY)
  }

  override fun hoverWhenNoInteraction(@SwingCoordinate mouseX: Int,
                                      @SwingCoordinate mouseY: Int,
                                      @JdkConstants.InputEventMask modifiersEx: Int) {
    val sceneView = surface.getSceneViewAt(mouseX, mouseY)
    if (sceneView != null) {
      val context = sceneView.context
      context.setMouseLocation(mouseX, mouseY)
      sceneView.scene.mouseHover(context, Coordinates.getAndroidXDip(sceneView, mouseX), Coordinates.getAndroidYDip(sceneView, mouseY), modifiersEx)
    }
    surface.onHover(mouseX, mouseY)
  }

  override fun stayHovering(mouseX: Int, mouseY: Int) {
    surface.onHover(mouseX, mouseY)
  }

  override fun popupMenuTrigger(mouseEvent: MouseEvent) {
    // For now only custom models mode has popup menu.
    val customModelsProvider = getModelsProviderFunc.invoke() as? CustomModelsProvider ?: return

    val mouseX = mouseEvent.x
    val mouseY = mouseEvent.y
    val sceneView = surface.getSceneViewAt(mouseX, mouseY) ?: return

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

  override fun mouseExited() {
    // Call onHover on each SceneView, with coordinates that are sure to be outside.
    surface.sceneManagers.flatMap { it.sceneViews }.forEach {
      it.scene.mouseHover(it.context, Int.MIN_VALUE, Int.MIN_VALUE, 0)
      it.onHover(Int.MIN_VALUE, Int.MIN_VALUE)
    }
  }
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
