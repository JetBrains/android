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
package com.android.tools.idea.glance.preview.actions

import com.android.tools.adtui.InformationPopup
import com.android.tools.adtui.InformationPopupImpl
import com.android.tools.idea.editors.shortcuts.asString
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.glance.preview.GlancePreviewBundle.message
import com.android.tools.idea.glance.preview.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.actions.BuildAndRefresh
import com.android.tools.idea.preview.actions.IssueNotificationAction
import com.android.tools.idea.preview.actions.PreviewStatusNotification
import com.android.tools.idea.preview.actions.ShowProblemsPanel
import com.android.tools.idea.preview.actions.actionLink
import com.android.tools.idea.projectsystem.needsBuild
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project

/**
 * Provides [PreviewStatusNotification] based on [Project] and [PreviewViewModelStatus] (passed
 * through [DataContext]) states.
 */
internal fun getStatusInfo(project: Project, dataContext: DataContext): PreviewStatusNotification? {
  val viewModelStatus = dataContext.getData(PREVIEW_VIEW_MODEL_STATUS) ?: return null
  return when {
    // Refresh status
    viewModelStatus.isRefreshing -> PreviewStatusNotification.Refreshing()

    // Out of date
    viewModelStatus.isOutOfDate -> PreviewStatusNotification.OutOfDate

    // Build/Syntax/Render errors
    project.needsBuild -> PreviewStatusNotification.NeedsBuild
    viewModelStatus.hasSyntaxErrors -> PreviewStatusNotification.SyntaxError
    viewModelStatus.hasErrorsAndNeedsBuild -> PreviewStatusNotification.RenderIssues

    // Up-to-date
    else -> PreviewStatusNotification.UpToDate
  }
}

/**
 * Provides [InformationPopup] based on [Project] and [PreviewViewModelStatus] (passed through
 * [DataContext]) states.
 */
internal fun createInformationPopup(project: Project, dataContext: DataContext): InformationPopup? {
  return getStatusInfo(project, dataContext)?.let {
    val viewModelStatus = dataContext.getData(PREVIEW_VIEW_MODEL_STATUS) ?: return null
    return@let InformationPopupImpl(
      null,
      it.description,
      listOf(),
      listOfNotNull(
        actionLink(
          message("action.build.and.refresh.title").replace("&&", "&") +
            getBuildAndRefreshShortcut().asString(),
          BuildAndRefresh { viewModelStatus.previewedFile },
          dataContext
        ),
        when (it) {
          is PreviewStatusNotification.SyntaxError, PreviewStatusNotification.RenderIssues ->
            actionLink(message("action.view.problems"), ShowProblemsPanel(), dataContext)
          else -> null
        }
      )
    )
  }
}

/** [IssueNotificationAction] for Glance previews. */
class GlanceIssueNotificationAction :
  IssueNotificationAction(::getStatusInfo, ::createInformationPopup)
