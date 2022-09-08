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
package com.android.tools.idea.glance.preview

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.common.error.SceneViewIssueNodeVisitor
import com.android.tools.idea.common.error.setIssuePanelVisibility
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.glance.preview.GlancePreviewBundle.message
import com.android.tools.idea.glance.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.uibuilder.scene.hasRenderErrors
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import icons.StudioIcons
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
val PREVIEW_VIEW_MODEL_STATUS = DataKey.create<PreviewViewModelStatus>("PreviewViewModelStatus")

/** [AnAction] that can be used to show an icon according to the [PreviewViewModelStatus]. */
internal class GlancePreviewStatusIcon(private val sceneView: SceneView?) : AnAction() {
  override fun update(e: AnActionEvent) {
    val previewViewModelStatus = e.getData(PREVIEW_VIEW_MODEL_STATUS) ?: return
    e.presentation.apply {
      if (sceneView?.hasRenderErrors() == true && !previewViewModelStatus.isRefreshing) {
        isVisible = true
        isEnabled = true
        icon = StudioIcons.Common.WARNING
        text = message("action.open.issues.panel.title")
      } else {
        isVisible = false
        isEnabled = false
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
