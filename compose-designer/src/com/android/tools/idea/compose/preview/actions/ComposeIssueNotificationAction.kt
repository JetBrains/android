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
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.adtui.compose.InformationPopup
import com.android.tools.adtui.compose.InformationPopupImpl
import com.android.tools.adtui.compose.IssueNotificationAction
import com.android.tools.adtui.compose.actionLink
import com.android.tools.idea.common.actions.ActionButtonWithToolTipDescription
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.findComposePreviewManagersForContext
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.editors.fast.fastPreviewManager
import com.android.tools.idea.editors.shortcuts.asString
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.actions.BuildAndRefresh
import com.android.tools.idea.preview.actions.PreviewStatus
import com.android.tools.idea.preview.actions.ReEnableFastPreview
import com.android.tools.idea.preview.actions.ShowEventLogAction
import com.android.tools.idea.preview.actions.ShowProblemsPanel
import com.android.tools.idea.projectsystem.needsBuild
import com.android.tools.idea.projectsystem.requestBuild
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.AnActionLink
import com.intellij.util.ui.JBUI
import java.lang.ref.WeakReference
import kotlin.reflect.KFunction0
import org.jetbrains.annotations.VisibleForTesting

private val GREEN_REFRESH_BUTTON =
  ColoredIconGenerator.generateColoredIcon(
    AllIcons.Actions.ForceRefresh,
    JBColor(0x59A869, 0x499C54)
  )

@VisibleForTesting
internal fun getStatusInfo(project: Project, dataContext: DataContext): PreviewStatus? {
  val composePreviewManager = dataContext.getData(COMPOSE_PREVIEW_MANAGER) ?: return null
  val previewStatus = composePreviewManager.status()
  val fastPreviewEnabled = project.fastPreviewManager.isEnabled
  return when {
    // No Fast Preview and Preview is out of date (only when is user disabled)
    !fastPreviewEnabled &&
      !project.fastPreviewManager.isAutoDisabled &&
      previewStatus.isOutOfDate -> PreviewStatus.OutOfDate

    // Resources are out of date. FastPreview does not help with this.
    previewStatus.areResourcesOutOfDate -> PreviewStatus.OutOfDate

    // Refresh status
    previewStatus.interactiveMode == ComposePreviewManager.InteractiveMode.STARTING ->
      PreviewStatus.Refreshing(message("notification.interactive.preview.starting"))
    previewStatus.interactiveMode == ComposePreviewManager.InteractiveMode.STOPPING ->
      PreviewStatus.Refreshing(message("notification.interactive.preview.stopping"))
    previewStatus.isRefreshing -> PreviewStatus.Refreshing()

    // Build/Syntax/Render errors
    project.needsBuild -> PreviewStatus.NeedsBuild
    previewStatus.hasSyntaxErrors -> PreviewStatus.SyntaxError
    previewStatus.hasRuntimeErrors -> PreviewStatus.RenderIssues

    // Fast preview refresh/failures
    !fastPreviewEnabled && project.fastPreviewManager.isAutoDisabled ->
      PreviewStatus.FastPreviewFailed
    fastPreviewEnabled && project.fastPreviewManager.isCompiling ->
      PreviewStatus.FastPreviewCompiling

    // Up-to-date
    else -> PreviewStatus.UpToDate
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
  return getStatusInfo(project, dataContext)?.let { previewStatusNotification ->
    val isAutoDisabled =
      previewStatusNotification is PreviewStatus.FastPreviewFailed &&
        project.fastPreviewManager.isAutoDisabled

    with(dataContext) {
      val linksList =
        listOfNotNull(
          createTitleActionLink(fileProvider),
          createErrorsActionLink(previewStatusNotification),
          createReenableFastPreviewActionLink(isAutoDisabled),
          createDisableFastPreviewActionLink(isAutoDisabled),
          createFastPreviewFailedActionLink(previewStatusNotification)
        )
      return@let InformationPopupImpl(
          title = null,
          description = previewStatusNotification.description,
          additionalActions =
            listOfNotNull(StudioFlags.COMPOSE_FAST_PREVIEW.ifEnabled { ToggleFastPreviewAction() }),
          links = linksList
        )
        .also { newPopup ->
          // Register the data provider of the popup to be the same as the one used in the toolbar.
          // This allows for actions within the
          // popup to query for things like the Editor even when the Editor is not directly related
          // to
          // the popup.
          DataManager.registerDataProvider(newPopup.popupComponent) { dataId ->
            dataContext.getData(dataId)
          }
        }
    }
  }
}

private fun DataContext.createFastPreviewFailedActionLink(
  previewStatusNotification: PreviewStatus,
): AnActionLink? =
  previewStatusNotification.takeIf { it is PreviewStatus.FastPreviewFailed }?.let {
    actionLink(
      text = message("fast.preview.disabled.notification.show.details.action.title"),
      action = ShowEventLogAction(),
      delegateDataContext = this
    )
  }

private fun DataContext.createDisableFastPreviewActionLink(isAutoDisabled: Boolean): AnActionLink? =
  isAutoDisabled.takeIf { it }?.let {
    actionLink(
      text = message("fast.preview.disabled.notification.stop.autodisable.action.title"),
      action = ReEnableFastPreview(false),
      delegateDataContext = this
    )
  }

private fun DataContext.createReenableFastPreviewActionLink(
  isAutoDisabled: Boolean,
): AnActionLink? =
  isAutoDisabled.takeIf { it }?.let {
    actionLink(
      text = message("fast.preview.disabled.notification.reenable.action.title"),
      action = ReEnableFastPreview(),
      delegateDataContext = this
    )
  }

private fun DataContext.createErrorsActionLink(it: PreviewStatus): AnActionLink? =
  when (it) {
    is PreviewStatus.SyntaxError, PreviewStatus.RenderIssues ->
      actionLink(message("action.view.problems"), ShowProblemsPanel(), this)
    else -> null
  }

private fun DataContext.createTitleActionLink(
  fileProvider: KFunction0<PsiFile?>,
): AnActionLink =
  actionLink(
    text =
      message("action.build.and.refresh.title").replace("&&", "&") +
        getBuildAndRefreshShortcut().asString(),
    // Remove any ampersand escaping for tooltips (not needed in these links)
    action = BuildAndRefresh(fileProvider),
    delegateDataContext = this
  )

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
 * [AnAction] that triggers a compilation of the current module. The build will automatically
 * trigger a refresh of the surface. The action visibility is controlled by the
 * [PreviewStatus.hasRefreshIcon]
 */
private class ForceCompileAndRefreshActionForNotification(private val surface: DesignSurface<*>) :
  AnAction(
    message("action.build.and.refresh.title"),
    message("action.build.and.refresh.description"),
    GREEN_REFRESH_BUTTON
  ),
  RightAlignedToolbarAction,
  CustomComponentAction {

  override fun actionPerformed(e: AnActionEvent) {
    // Each ComposePreviewManager will avoid refreshing the corresponding previews if it detects
    // that nothing has changed. But we want to always force a refresh when this button is pressed
    findComposePreviewManagersForContext(e.dataContext).forEach { composePreviewManager ->
      composePreviewManager.invalidateSavedBuildStatus()
    }
    if (!requestBuildForSurface(surface)) {
      // If there are no models in the surface, we can not infer which models we should trigger
      // the build for. The fallback is to find the virtual file for the editor and trigger that.
      LangDataKeys.VIRTUAL_FILE.getData(e.dataContext)?.let { surface.project.requestBuild(it) }
    }
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val isRefreshing =
      findComposePreviewManagersForContext(e.dataContext).any { it.status().isRefreshing }
    presentation.isEnabled = !isRefreshing
    templateText?.let {
      presentation.setText("$it${getBuildAndRefreshShortcut().asString()}", false)
    }

    val project = e.project ?: return
    getStatusInfo(project, e.dataContext)?.let { e.presentation.isVisible = it.hasRefreshIcon }
  }

  override fun createCustomComponent(presentation: Presentation, place: String) =
    ActionButtonWithToolTipDescription(this, presentation, place).apply {
      border = JBUI.Borders.empty(1, 2)
    }

  private fun requestBuildForSurface(surface: DesignSurface<*>) =
    surface
      .models
      .map { it.virtualFile }
      .distinct()
      .also { surface.project.requestBuild(it) }
      .isNotEmpty()
}

/**
 * [DefaultActionGroup] that shows the notification chip and the
 * [ForceCompileAndRefreshActionForNotification] button when applicable.
 */
class ComposeNotificationGroup(surface: DesignSurface<*>) :
  DefaultActionGroup(
    listOf(ComposeIssueNotificationAction(), ForceCompileAndRefreshActionForNotification(surface))
  )
