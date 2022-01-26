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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.findComposePreviewManagersForContext
import com.android.tools.idea.compose.preview.isAnyPreviewRefreshing
import com.android.tools.idea.compose.preview.liveEdit.PreviewLiveEditManager
import com.android.tools.idea.compose.preview.liveEdit.fastCompileAsync
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags.COMPOSE_LIVE_EDIT_PREVIEW
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.ui.JBColor

private fun ProjectSystemBuildManager.BuildStatus.isSuccessOrUnknown() =
  this == ProjectSystemBuildManager.BuildStatus.SUCCESS || this == ProjectSystemBuildManager.BuildStatus.UNKNOWN

private val SINGLE_FILE_REFRESH_ICON = ColoredIconGenerator.generateColoredIcon(AllIcons.Actions.ForceRefresh,
                                                                                JBColor(0x5969A8, 0x49549C))
/**
 * [AnAction] that triggers a compilation of the current module. The build will automatically trigger a refresh
 * of the surface.
 */
internal class SingleFileCompileAction :
  AnAction(message("action.live.edit.refresh.title"), null, SINGLE_FILE_REFRESH_ICON) {

  override fun actionPerformed(e: AnActionEvent) {
    val previewManager = findComposePreviewManagersForContext(e.dataContext).singleOrNull() ?: return
    val file = previewManager.previewedFile ?: return

    FileDocumentManager.getInstance().saveAllDocuments()
    fastCompileAsync(previewManager, file) {
      (previewManager as ComposePreviewRepresentation).forceRefresh()
    }
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val presentation = e.presentation
    if (project == null) {
      presentation.isEnabledAndVisible = false
      return
    }

    val isBuildSuccessfulOrUnknown = project.getProjectSystem().getBuildManager()
                                     .getLastBuildResult()
                                     .status
                                     .isSuccessOrUnknown() ?: false
    val isPreviewRefreshing = isAnyPreviewRefreshing(e.dataContext)
    val previewLiveEditManager = PreviewLiveEditManager.getInstance(project)
    presentation.isVisible = previewLiveEditManager.isEnabled
    presentation.isEnabled =
      previewLiveEditManager.isAvailable &&
      presentation.isVisible &&
      !isPreviewRefreshing &&
      isBuildSuccessfulOrUnknown
    presentation.description = if (isBuildSuccessfulOrUnknown)
      message("action.live.edit.refresh.description")
    else
      message("action.live.edit.refresh.description.needs.build")
  }
}