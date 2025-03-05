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

import com.android.tools.adtui.status.InformationPopup
import com.android.tools.adtui.status.InformationPopupImpl
import com.android.tools.adtui.status.IssueNotificationAction
import com.android.tools.idea.editors.fast.fastPreviewManager
import com.android.tools.idea.editors.shortcuts.asString
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.fast.FastPreviewSurface
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.projectsystem.needsBuild
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.components.AnActionLink
import com.intellij.util.ui.JBUI
import java.awt.Insets
import java.lang.ref.WeakReference
import kotlin.reflect.KFunction0
import org.jetbrains.annotations.VisibleForTesting

/** Returns the status when Fast Preview is disabled. */
private fun getStatus(project: Project, previewStatus: PreviewViewModelStatus) =
  when {
    previewStatus.isRefreshing -> PreviewStatus.Refreshing()

    // No Fast Preview and Preview is out of date (only when is user disabled)
    !project.fastPreviewManager.isAutoDisabled && previewStatus.isOutOfDate ->
      PreviewStatus.OutOfDate

    // Resources are out of date. FastPreview does not help with this.
    previewStatus.areResourcesOutOfDate -> PreviewStatus.OutOfDate

    // Build/Syntax/Render errors
    project.needsBuild -> PreviewStatus.NeedsBuild
    previewStatus.hasSyntaxErrors -> PreviewStatus.SyntaxError
    previewStatus.hasRenderErrors -> PreviewStatus.RenderIssues

    // Fast preview refresh/failures
    project.fastPreviewManager.isAutoDisabled -> PreviewStatus.FastPreviewFailed
    project.fastPreviewManager.isCompiling -> PreviewStatus.FastPreviewCompiling

    // Up-to-date
    else -> PreviewStatus.UpToDate
  }

/** Returns the status when Fast Preview is enabled. */
private fun getStatusForFastPreview(project: Project, previewStatus: PreviewViewModelStatus) =
  when {
    previewStatus.isRefreshing -> PreviewStatus.Refreshing()

    // Syntax errors take precedence
    previewStatus.hasSyntaxErrors -> PreviewStatus.SyntaxError

    // Code is out of date
    previewStatus.isOutOfDate -> PreviewStatus.OutOfDate

    // Resources are out of date. FastPreview does not help with this.
    previewStatus.areResourcesOutOfDate -> PreviewStatus.OutOfDate
    project.needsBuild -> PreviewStatus.NeedsBuild
    previewStatus.hasRenderErrors -> PreviewStatus.RenderIssues
    project.fastPreviewManager.isCompiling -> PreviewStatus.FastPreviewCompiling

    // Up-to-date
    else -> PreviewStatus.UpToDate
  }

fun getStatusInfo(project: Project, dataContext: DataContext): PreviewStatus? {
  val previewStatus = dataContext.getData(PREVIEW_VIEW_MODEL_STATUS) ?: return null
  val fastPreviewEnabled = project.fastPreviewManager.isEnabled
  return if (fastPreviewEnabled) getStatusForFastPreview(project, previewStatus)
  else getStatus(project, previewStatus)
}

private class FileProvider(dataContext: DataContext) : () -> PsiFile? {
  private val status = WeakReference(dataContext.getData(PREVIEW_VIEW_MODEL_STATUS))

  override fun invoke(): PsiFile? {
    return status.get()?.previewedFile
  }
}

/**
 * Creates an [InformationPopup]. The given [dataContext] will be used by the popup to query for
 * things like the current editor.
 */
@VisibleForTesting
fun defaultCreateInformationPopup(project: Project, dataContext: DataContext): InformationPopup? {
  val fileProvider = FileProvider(dataContext)::invoke
  return getStatusInfo(project, dataContext)?.let { previewStatusNotification ->
    val isAutoDisabled =
      previewStatusNotification is PreviewStatus.FastPreviewFailed &&
        project.fastPreviewManager.isAutoDisabled

    val linksList =
      listOfNotNull(
        createTitleActionLink(fileProvider),
        createErrorsActionLink(previewStatusNotification),
        createReenableFastPreviewActionLink(isAutoDisabled),
        createDisableFastPreviewActionLink(isAutoDisabled),
        createFastPreviewFailedActionLink(previewStatusNotification),
      )
    return@let InformationPopupImpl(
      title = null,
      description = previewStatusNotification.description,
      additionalActions =
        listOf(
          ToggleFastPreviewAction(
            fastPreviewSurfaceProvider = { dataContext ->
              dataContext.findPreviewManager(FastPreviewSurface.KEY)
            }
          )
        ),
      links = linksList,
    )
  }
}

private fun createFastPreviewFailedActionLink(
  previewStatusNotification: PreviewStatus
): AnActionLink? =
  previewStatusNotification
    .takeIf { it is PreviewStatus.FastPreviewFailed }
    ?.let {
      AnActionLink(
        text = message("fast.preview.disabled.notification.show.details.action.title"),
        anAction = ShowEventLogAction(),
      )
    }

private fun createDisableFastPreviewActionLink(isAutoDisabled: Boolean): AnActionLink? =
  isAutoDisabled
    .takeIf { it }
    ?.let {
      AnActionLink(
        text = message("fast.preview.disabled.notification.stop.autodisable.action.title"),
        anAction = ReEnableFastPreview(false),
      )
    }

private fun createReenableFastPreviewActionLink(isAutoDisabled: Boolean): AnActionLink? =
  isAutoDisabled
    .takeIf { it }
    ?.let {
      AnActionLink(
        text = message("fast.preview.disabled.notification.reenable.action.title"),
        anAction = ReEnableFastPreview(),
      )
    }

private fun createErrorsActionLink(it: PreviewStatus): AnActionLink? =
  when (it) {
    is PreviewStatus.SyntaxError,
    PreviewStatus.RenderIssues -> AnActionLink(message("action.view.problems"), ShowProblemsPanel())
    else -> null
  }

private fun createTitleActionLink(fileProvider: KFunction0<PsiFile?>): AnActionLink =
  AnActionLink(
    text =
      message("action.build.and.refresh.title").replace("&&", "&") +
        getBuildAndRefreshShortcut().asString(),
    // Remove any ampersand escaping for tooltips (not needed in these links)
    anAction = BuildAndRefresh(fileProvider),
  )

/** Common [IssueNotificationAction] that can be used for most previews. */
open class CommonIssueNotificationAction(
  createInformationPopup: (Project, DataContext) -> InformationPopup? = { project, dataContext ->
    defaultCreateInformationPopup(project, dataContext)
  }
) : IssueNotificationAction(::getStatusInfo, createInformationPopup), RightAlignedToolbarAction {
  override fun margins(): Insets {
    return JBUI.insets(3)
  }
}
