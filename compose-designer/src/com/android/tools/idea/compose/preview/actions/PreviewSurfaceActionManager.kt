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

import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.ComposeStudioBotActionFactory
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.zoomTargetProvider
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.actions.AnimationInspectorAction
import com.android.tools.idea.preview.actions.BackNavigationAction
import com.android.tools.idea.preview.actions.CommonPreviewActionManager
import com.android.tools.idea.preview.actions.EnableInteractiveAction
import com.android.tools.idea.preview.actions.JumpToDefinitionAction
import com.android.tools.idea.preview.actions.ViewInFocusModeAction
import com.android.tools.idea.preview.actions.ZoomToSelectionAction
import com.android.tools.idea.preview.actions.disabledIfRefreshingOrHasErrorsOrProjectNeedsBuild
import com.android.tools.idea.preview.actions.hideIfRenderErrors
import com.android.tools.idea.preview.actions.visibleOnlyInFocus
import com.android.tools.idea.preview.actions.visibleOnlyInInteractive
import com.android.tools.idea.preview.actions.visibleOnlyInStaticPreview
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/** [ActionManager] to be used by the Compose Preview. */
internal class PreviewSurfaceActionManager(
  private val surface: DesignSurface<LayoutlibSceneManager>,
  private val navigationHandler: NavigationHandler,
) : CommonPreviewActionManager(surface, navigationHandler) {

  override fun getPopupMenuActions(
    leafComponent: NlComponent?,
    mouseEvent: MouseEvent,
  ): DefaultActionGroup {
    // Copy Image
    val actionGroup = DefaultActionGroup().apply { add(copyResultImageAction) }

    val mousePosition = mouseEvent.point

    SwingUtilities.convertPointFromScreen(mousePosition, surface.interactionPane)
    // Zoom to Selection
    actionGroup.add(ZoomToSelectionAction(mousePosition.x, mousePosition.y, ::zoomTargetProvider))
    // Jump to Definition
    actionGroup.add(JumpToDefinitionAction(mousePosition.x, mousePosition.y, navigationHandler))
    // View in Focus mode
    actionGroup.add(ViewInFocusModeAction())
    // Toggle Resize Panel (only in focus mode)
    actionGroup.add(ToggleResizePanelVisibilityAction().visibleOnlyInFocus())
    // Add toolbar actions in the context-menu as a redundant entry point
    getSceneViewContextToolbarActions().takeIf { it.isNotEmpty() }?.forEach { actionGroup.add(it) }
    // Add action to transform UI with AI
    if (StudioFlags.COMPOSE_PREVIEW_TRANSFORM_UI_WITH_AI.get()) {
      ComposeStudioBotActionFactory.EP_NAME.extensionList.firstOrNull()?.let {
        it.transformPreviewAction()?.let { action ->
          actionGroup.add(action.visibleOnlyInStaticPreview())
        }
      }
    }
    if (StudioFlags.COMPOSE_UI_CHECK_FIX_WITH_AI.get()) {
      ComposeStudioBotActionFactory.EP_NAME.extensionList.firstOrNull()?.let {
        it.fixVisualLintIssuesAction()?.let { action ->
          actionGroup.add(action.visibleOnlyInStaticPreview())
        }
      }
    }

    return actionGroup
  }

  override fun getSceneViewContextToolbarActions(): List<AnAction> =
    listOf(Separator()) +
      listOfNotNull(
          SavePreviewInNewSizeAction().visibleOnlyInFocus(),
          EnableUiCheckAction(),
          AnimationInspectorAction(
            defaultModeDescription = message("action.animation.inspector.description")
          ),
          EnableInteractiveAction(),
          DeployToDeviceAction(),
        )
        .disabledIfRefreshingOrHasErrorsOrProjectNeedsBuild()
        .hideIfRenderErrors()
        .visibleOnlyInStaticPreview() +
      listOf(BackNavigationAction().visibleOnlyInInteractive())
        .disabledIfRefreshingOrHasErrorsOrProjectNeedsBuild()
}
