/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.tools.adtui.InformationPopup
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.editors.fast.fastPreviewManager
import com.android.tools.idea.editors.shortcuts.asString
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.actions.BuildAndRefresh
import com.android.tools.idea.preview.actions.IssueNotificationAction
import com.android.tools.idea.preview.actions.PreviewStatusNotification
import com.android.tools.idea.preview.actions.ReEnableFastPreview
import com.android.tools.idea.preview.actions.ShowEventLogAction
import com.android.tools.idea.preview.actions.ShowProblemsPanel
import com.android.tools.idea.preview.actions.actionLink
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import java.lang.ref.WeakReference
import org.jetbrains.annotations.VisibleForTesting

/**
 * Utility getter that indicates if the project needs a build. This is the case if the previews
 * build is not valid, like after a clean or cancelled, or if it has failed.
 */
internal val Project.needsBuild: Boolean
  get() {
    val lastBuildResult =
      ProjectSystemService.getInstance(project = this)
        .projectSystem
        .getBuildManager()
        .getLastBuildResult()
    return lastBuildResult.status == ProjectSystemBuildManager.BuildStatus.CANCELLED ||
      lastBuildResult.status == ProjectSystemBuildManager.BuildStatus.FAILED ||
      lastBuildResult.mode == ProjectSystemBuildManager.BuildMode.CLEAN
  }

@VisibleForTesting
internal fun getStatusInfo(project: Project, dataContext: DataContext): PreviewStatusNotification? {
  val composePreviewManager = dataContext.getData(COMPOSE_PREVIEW_MANAGER) ?: return null
  val previewStatus = composePreviewManager.status()
  val fastPreviewEnabled = project.fastPreviewManager.isEnabled
  return when {
    // No Fast Preview and Preview is out of date (only when is user disabled)
    !fastPreviewEnabled &&
      !project.fastPreviewManager.isAutoDisabled &&
      previewStatus.isOutOfDate -> PreviewStatusNotification.OutOfDate

    // Refresh status
    previewStatus.interactiveMode == ComposePreviewManager.InteractiveMode.STARTING ->
      PreviewStatusNotification.Refreshing(message("notification.interactive.preview.starting"))
    previewStatus.interactiveMode == ComposePreviewManager.InteractiveMode.STOPPING ->
      PreviewStatusNotification.Refreshing(message("notification.interactive.preview.stopping"))
    previewStatus.isRefreshing -> PreviewStatusNotification.Refreshing()

    // Build/Syntax/Render errors
    project.needsBuild -> PreviewStatusNotification.NeedsBuild
    previewStatus.hasSyntaxErrors -> PreviewStatusNotification.SyntaxError
    previewStatus.hasRuntimeErrors -> PreviewStatusNotification.RenderIssues

    // Fast preview refresh/failures
    !fastPreviewEnabled && project.fastPreviewManager.isAutoDisabled ->
      PreviewStatusNotification.FastPreviewFailed
    fastPreviewEnabled && project.fastPreviewManager.isCompiling ->
      PreviewStatusNotification.FastPreviewCompiling

    // Up-to-date
    else -> PreviewStatusNotification.UpToDate
  }
}

private class ComposePreviewManagerFileProvider(dataContext: DataContext) : () -> PsiFile? {
  private val composePreviewManager = WeakReference(dataContext.getData(COMPOSE_PREVIEW_MANAGER))

  override fun invoke(): PsiFile? {
    return composePreviewManager.get()?.previewedFile
  }
}

/**
 * Creates an [InformationPopup]. The given [dataContext] will be used by the popup to query for
 * things like the current editor.
 */
@VisibleForTesting
fun defaultCreateInformationPopup(
  project: Project,
  dataContext: DataContext,
): InformationPopup? {
  val fileProvider = ComposePreviewManagerFileProvider(dataContext)::invoke
  return getStatusInfo(project, dataContext)?.let {
    val isAutoDisabled =
      it is PreviewStatusNotification.FastPreviewFailed && project.fastPreviewManager.isAutoDisabled
    return@let InformationPopup(
        null,
        it.description,
        listOfNotNull(StudioFlags.COMPOSE_FAST_PREVIEW.ifEnabled { ToggleFastPreviewAction() }),
        listOfNotNull(
          actionLink(
            message("action.build.and.refresh.title").replace("&&", "&") +
              getBuildAndRefreshShortcut()
                .asString(), // Remove any ampersand escaping for tooltips (not needed in these
            // links)
            BuildAndRefresh(fileProvider),
            dataContext
          ),
          when (it) {
            is PreviewStatusNotification.SyntaxError, PreviewStatusNotification.RenderIssues ->
              actionLink(message("action.view.problems"), ShowProblemsPanel(), dataContext)
            else -> null
          },
          if (isAutoDisabled)
            actionLink(
              message("fast.preview.disabled.notification.reenable.action.title"),
              ReEnableFastPreview(),
              dataContext
            )
          else null,
          if (isAutoDisabled)
            actionLink(
              message("fast.preview.disabled.notification.stop.autodisable.action.title"),
              ReEnableFastPreview(false),
              dataContext
            )
          else null,
          if (it is PreviewStatusNotification.FastPreviewFailed)
            actionLink(
              message("fast.preview.disabled.notification.show.details.action.title"),
              ShowEventLogAction(),
              dataContext
            )
          else null
        )
      )
      .also { newPopup ->
        // Register the data provider of the popup to be the same as the one used in the toolbar.
        // This allows for actions within the
        // popup to query for things like the Editor even when the Editor is not directly related to
        // the popup.
        DataManager.registerDataProvider(newPopup.component()) { dataId ->
          dataContext.getData(dataId)
        }
      }
  }
}

/**
 * Action that reports the current state of the Compose Preview.
 *
 * This action reports:
 * - State of Live Edit or preview out of date if Live Edit is disabled
 * - Syntax errors
 */
class ComposeIssueNotificationAction(
  createInformationPopup: (Project, DataContext) -> InformationPopup? =
    ::defaultCreateInformationPopup
) : IssueNotificationAction(::getStatusInfo, createInformationPopup)

/**
 * [ForceCompileAndRefreshAction] where the visibility is controlled by the
 * [PreviewStatusNotification.hasRefreshIcon].
 */
private class ForceCompileAndRefreshActionForNotification(surface: DesignSurface<*>) :
  ForceCompileAndRefreshAction(surface), RightAlignedToolbarAction {
  override fun update(e: AnActionEvent) {
    super.update(e)

    val project = e.project ?: return

    getStatusInfo(project, e.dataContext)?.let { e.presentation.isVisible = it.hasRefreshIcon }
  }
}

/**
 * [DefaultActionGroup] that shows the notification chip and the
 * [ForceCompileAndRefreshActionForNotification] button when applicable.
 */
class ComposeNotificationGroup(surface: DesignSurface<*>) :
  DefaultActionGroup(
    listOf(ComposeIssueNotificationAction(), ForceCompileAndRefreshActionForNotification(surface))
  )
