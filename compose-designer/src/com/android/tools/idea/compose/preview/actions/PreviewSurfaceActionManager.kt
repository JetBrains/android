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

import com.android.flags.ifEnabled
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.ComposeStudioBotActionFactory
import com.android.tools.idea.compose.preview.actions.glasses.GlassesBlendDropdownAction
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.subComponentProvider
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

    val convertedPoint =
      SwingUtilities.convertPoint(mouseEvent.component, mouseEvent.point, surface.interactionPane)

    // Zoom to Selection
    actionGroup.add(
      ZoomToSelectionAction(convertedPoint.x, convertedPoint.y, ::subComponentProvider)
    )
    // Jump to Definition
    actionGroup.add(JumpToDefinitionAction(convertedPoint.x, convertedPoint.y, navigationHandler))
    // View in Focus mode
    actionGroup.add(ViewInFocusModeAction())
    // Add toolbar actions in the context-menu as a redundant entry point
    getPreviewActions().takeIf { it.isNotEmpty() }?.forEach { actionGroup.add(it) }
    getAiActionGroup(shouldShowInDropDown = true)?.let { actionGroup.add(it) }
    return actionGroup
  }

  override fun getSceneViewContextToolbarActions(): List<AnAction?> =
    listOfNotNull(
      StudioFlags.COMPOSE_PREVIEW_AI_GLASSES_PREVIEW.ifEnabled { GlassesBlendDropdownAction() }
    )

  override fun getSceneViewContextToolbarOverflowActions(): List<AnAction> {
    val aiActionGroup = getAiActionGroup(shouldShowInDropDown = false)
    val previewActions = getPreviewActions()
    return previewActions + listOfNotNull(aiActionGroup)
  }

  private fun getPreviewActions(): List<AnAction> =
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

  private fun getAiActionGroup(shouldShowInDropDown: Boolean): AnAction? {
    val factory = ComposeStudioBotActionFactory.EP_NAME.extensionList.firstOrNull() ?: return null

    val action =
      if (shouldShowInDropDown) {
        factory.previewAgentsDropDownAction()
      } else {
        factory.previewAgentsActionGroup()
      }
    return action?.visibleOnlyInStaticPreview()
  }
}
