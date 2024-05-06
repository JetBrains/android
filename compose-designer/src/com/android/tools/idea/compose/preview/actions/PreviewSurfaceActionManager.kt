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

import com.android.tools.idea.common.actions.CopyResultImageAction
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.InteractiveLabelPanel
import com.android.tools.idea.common.surface.LabelPanel
import com.android.tools.idea.common.surface.LayoutData
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.essentials.ComposePreviewEssentialsModeManager
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.preview.actions.EnableInteractiveAction
import com.android.tools.idea.preview.actions.hideIfRenderErrors
import com.android.tools.idea.preview.actions.visibleOnlyInStaticPreview
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Separator
import javax.swing.JComponent

/** [ActionManager] to be used by the Compose Preview. */
internal class PreviewSurfaceActionManager(
  private val surface: DesignSurface<LayoutlibSceneManager>,
  private val navigationHandler: NavigationHandler,
) : ActionManager<DesignSurface<LayoutlibSceneManager>>(surface) {

  private val sceneManagerProvider: () -> LayoutlibSceneManager? = {
    // Copy the model of the current selected object (if any)
    surface.selectionModel.primary?.model?.let { surface.getSceneManager(it) }
      ?: surface.sceneViewAtMousePosition?.sceneManager as? LayoutlibSceneManager
  }

  private val copyResultImageAction =
    CopyResultImageAction(
      sceneManagerProvider,
      message("copy.result.image.action.title"),
      message("copy.result.image.action.done.text")
    )

  override fun registerActionsShortcuts(component: JComponent) {
    registerAction(copyResultImageAction, IdeActions.ACTION_COPY, component)
  }

  override fun createSceneViewLabel(sceneView: SceneView): LabelPanel {
    return InteractiveLabelPanel(
      LayoutData.fromSceneView(sceneView),
      surface,
      suspend { navigationHandler.handleNavigate(sceneView, false) }
    )
  }

  override fun getPopupMenuActions(leafComponent: NlComponent?): DefaultActionGroup {
    // Copy Image
    val actionGroup = DefaultActionGroup().apply { add(copyResultImageAction) }

    val sceneView = surface.sceneViewAtMousePosition ?: return actionGroup
    // Zoom to Selection
    (surface as? NlDesignSurface)?.let {
      actionGroup.add(ZoomToSelectionAction(surface, sceneView))
    }
    // Jump to Definition
    actionGroup.add(JumpToDefinitionAction(surface, navigationHandler, sceneView))

    return actionGroup
  }

  override fun getToolbarActions(selection: MutableList<NlComponent>): DefaultActionGroup =
    DefaultActionGroup()

  override fun getSceneViewContextToolbarActions(): List<AnAction> =
    listOf(Separator()) +
      listOfNotNull(
          EnableUiCheckAction(),
          AnimationInspectorAction(),
          EnableInteractiveAction(
            isEssentialsModeEnabled = {
              ComposePreviewEssentialsModeManager.isEssentialsModeEnabled
            },
            essentialsModeDescription = message("action.interactive.essentials.mode.description")
          ),
          DeployToDeviceAction(),
        )
        .disabledIfRefreshingOrRenderErrors()
        .hideIfRenderErrors()
        .visibleOnlyInStaticPreview()

  override fun getSceneViewStatusIconAction(): AnAction =
    ComposePreviewStatusIconAction().visibleOnlyInStaticPreview()
}
