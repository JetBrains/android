/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.adtui.stdui.CommonToggleButton
import com.android.tools.idea.common.actions.CopyResultImageAction
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.JComponent

/**
 * [ActionManager] to be used by the Compose Preview.
 */
internal class PreviewSurfaceActionManager(private val surface: DesignSurface) : ActionManager<DesignSurface>(surface) {
  private val copyResultImageAction = CopyResultImageAction(
    {
      // Copy the model of the current selected object (if any)
      surface.selectionModel.primary?.model?.let {
        return@CopyResultImageAction surface.getSceneManager(it) as LayoutlibSceneManager
      }

      // If no model is selected, copy the image under the mouse
      val mouseLocation = surface.getMousePosition(true) ?: return@CopyResultImageAction null
      surface.getHoverSceneView(mouseLocation.x, mouseLocation.y)?.sceneManager as? LayoutlibSceneManager
    })

  override fun registerActionsShortcuts(component: JComponent) {
    registerAction(copyResultImageAction, IdeActions.ACTION_COPY, component)
  }

  override fun getPopupMenuActions(leafComponent: NlComponent?): DefaultActionGroup = DefaultActionGroup().apply {
    add(copyResultImageAction)
  }

  override fun getToolbarActions(component: NlComponent?, newSelection: MutableList<NlComponent>): DefaultActionGroup =
    DefaultActionGroup()

  override fun getSceneViewContextToolbar(sceneView: SceneView): JComponent? = Box.createHorizontalBox().apply {
    isOpaque = false

    // For now, we just display a mock toolbar. This will be replaced in the future with SceneView the toolbar.
    add(CommonToggleButton("Interactive", null).apply {
      addChangeListener {
        sceneView.scene.sceneManager.model.dataContext.getData(COMPOSE_PREVIEW_MANAGER)?.isInteractive = isSelected
      }
    }, BorderLayout.LINE_END)
    add(CommonButton(StudioIcons.Shell.Toolbar.RUN), BorderLayout.LINE_END)
  }
}