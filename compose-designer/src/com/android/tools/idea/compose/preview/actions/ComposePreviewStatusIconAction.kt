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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.error.SceneViewIssueNodeVisitor
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.common.error.setIssuePanelVisibility
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.editors.fast.fastPreviewManager
import com.android.tools.idea.uibuilder.scene.hasRenderErrors
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.AnimatedIcon
import icons.StudioIcons

/**
 * [AnAction] that can be used to show an icon according to the Compose Preview status
 */
internal class ComposePreviewStatusIconAction(private val sceneView: SceneView?) : AnAction() {
  override fun update(e: AnActionEvent) {
    val composePreviewManager = e.getData(COMPOSE_PREVIEW_MANAGER) ?: return
    val project = e.project ?: return
    val previewStatus = composePreviewManager.status()
    val fastPreviewEnabled = project.fastPreviewManager.isEnabled
    val fastPreviewAutoDisabled = project.fastPreviewManager.isAutoDisabled
    e.presentation.apply {
      val newIcon = when {
        // loading
        previewStatus.interactiveMode.isStartingOrStopping() || previewStatus.isRefreshing ||
          project.fastPreviewManager.isCompiling -> AnimatedIcon.Default()
        // errors
        sceneView?.hasRenderErrors() == true -> StudioIcons.Common.WARNING
        // ok
        else -> AllIcons.General.InspectionsOK
      }

      isVisible = when {
        previewStatus.hasSyntaxErrors -> false
        fastPreviewEnabled -> true
        else -> !(previewStatus.isOutOfDate || fastPreviewAutoDisabled || project.needsBuild)
      }

      // Enable the icon to be clickable only when render/runtime errors were found, so that a
      // click action in such cases would open the issues panel with more info about the errors.
      isEnabled = isVisible && newIcon === StudioIcons.Common.WARNING

      if (isEnabled) {
        icon = newIcon
        text = message("action.open.issues.panel.title")
      }
      else {
        disabledIcon = newIcon
        text = null
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getData(DESIGN_SURFACE)?.setIssuePanelVisibility(show = true, userInvoked = true) {
      if (sceneView == null) {
        return@setIssuePanelVisibility
      }
      val project = e.project ?: return@setIssuePanelVisibility
      val service = IssuePanelService.getInstance(project)
      service.setSelectedNode(SceneViewIssueNodeVisitor(sceneView))
    }
  }
}
