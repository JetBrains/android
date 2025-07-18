/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.actions

import com.android.tools.idea.common.actions.CopyResultImageAction
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.sceneview.InteractiveLabelPanel
import com.android.tools.idea.common.surface.sceneview.LabelPanel
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Separator
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Common preview [ActionManager] for the [DesignSurface]. */
open class CommonPreviewActionManager(
  surface: DesignSurface<LayoutlibSceneManager>,
  private val navigationHandler: NavigationHandler,
  supportAnimationPreview: Boolean = true,
  supportInteractivePreview: Boolean = true,
) : ActionManager<DesignSurface<LayoutlibSceneManager>>(surface) {
  private val animationPreviewAction =
    if (supportAnimationPreview) AnimationInspectorAction() else null
  private val interactivePreviewAction =
    if (supportInteractivePreview) EnableInteractiveAction() else null

  protected val copyResultImageAction =
    CopyResultImageAction(
      message("copy.result.image.action.title"),
      message("copy.result.image.action.done.text"),
    )

  override fun registerActionsShortcuts(component: JComponent) {
    registerAction(copyResultImageAction, IdeActions.ACTION_COPY, component)
  }

  override fun getPopupMenuActions(leafComponent: NlComponent?) =
    DefaultActionGroup().apply { add(copyResultImageAction) }

  override fun getToolbarActions(selection: MutableList<NlComponent>) = DefaultActionGroup()

  override fun getSceneViewStatusIconAction(): AnAction =
    PreviewStatusIcon().visibleOnlyInStaticPreview()

  override fun createSceneViewLabel(
    sceneView: SceneView,
    scope: CoroutineScope,
    isPartOfOrganizationGroup: StateFlow<Boolean>,
  ): LabelPanel {
    return InteractiveLabelPanel(
      sceneView.sceneManager.model.displaySettings,
      scope,
      isPartOfOrganizationGroup,
      suspend { navigationHandler.handleNavigate(sceneView, false) },
    )
  }

  override fun getSceneViewContextToolbarActions(): List<AnAction> =
    listOfNotNull(animationPreviewAction, interactivePreviewAction)
      .takeIf { it.isNotEmpty() }
      ?.let {
        listOf(Separator()) +
          it
            .disabledIfRefreshingOrHasErrorsOrProjectNeedsBuild()
            .hideIfRenderErrors()
            .visibleOnlyInStaticPreview()
      } ?: emptyList()
}
