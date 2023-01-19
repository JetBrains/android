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
package com.android.tools.idea.preview.actions

import com.android.tools.adtui.compose.ComposeStatus
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.editors.fast.fastPreviewManager
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.projectsystem.requestBuild
import com.intellij.icons.AllIcons
import com.intellij.notification.EventLog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorNotifications
import javax.swing.Icon

/**
 * Represents the Preview status to be notified to the user.
 */
sealed class PreviewStatus(
  override val icon: Icon?,
  override val title: String,
  override val description: String,
  /** When true, the refresh icon will be displayed next to the notification chip. */
  override val hasRefreshIcon: Boolean = false,
  override val presentation: ComposeStatus.Presentation? = null
) : ComposeStatus {
  /**
   * The Preview found a syntax error and paused the updates.
   */
  object SyntaxError : PreviewStatus(
    AllIcons.General.InspectionsPause,
    message("notification.syntax.errors.title"),
    message("notification.syntax.errors.description"),
    false)

  /**
   * The Preview found a compilation error and paused the updates.
   */
  object NeedsBuild : PreviewStatus(
    AllIcons.General.Error,
    message("notification.needs.build.broken.title"),
    message("notification.needs.build.broken.description"),
    true,
    ComposeStatus.Presentation.Error)

  /**
   * The Preview is refreshing.
   */
  class Refreshing(detailsMessage: String = message("notification.preview.refreshing.description"))
    : PreviewStatus(
    AnimatedIcon.Default(),
    message("notification.preview.refreshing.title"),
    detailsMessage)

  /**
   * The Preview is out of date. This state will not happen if Fast Preview is enabled.
   */
  object OutOfDate : PreviewStatus(
    AllIcons.General.Warning,
    message("notification.preview.out.of.date.title"),
    message("notification.preview.out.of.date.description"),
    true,
    ComposeStatus.Presentation.Warning)

  /**
   * The Preview is compiling.
   */
  object FastPreviewCompiling : PreviewStatus(
    AnimatedIcon.Default(),
    message("notification.preview.fast.compile.title"),
    message("notification.preview.fast.compile.description"))

  /**
   * An issue was found while rendering the Preview.
   */
  object RenderIssues : PreviewStatus(
    AllIcons.General.Warning,
    message("notification.preview.render.issues.title"),
    message("notification.preview.render.issues.description"),
    true,
    ComposeStatus.Presentation.Warning)

  /**
   * The Preview has failed to compile a fast change.
   */
  object FastPreviewFailed : PreviewStatus(
    AllIcons.General.InspectionsPause,
    message("notification.preview.fast.disabled.reason.compiler.error.title"),
    message("notification.preview.fast.disabled.reason.compiler.error.description"),
    true,
    ComposeStatus.Presentation.Error)

  /**
   * The Preview is fully up to date.
   */
  object UpToDate : PreviewStatus(
    AllIcons.General.InspectionsOK,
    message("notification.preview.up.to.date.title"),
    message("notification.preview.up.to.date.description"))
}

/**
 * [AnAction] that will show the Event Log.
 */
class ShowEventLogAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    EventLog.getEventLog(project)?.activate(null) ?: ToolWindowManager.getInstance(project).getToolWindow("Notifications")?.activate(null)
  }
}

/**
 * [AnAction] that re-enable the Fast Preview if disabled.
 */
class ReEnableFastPreview(private val allowAutoDisable: Boolean = true) : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (!allowAutoDisable) {
      project.fastPreviewManager.allowAutoDisable = false
    }
    project.fastPreviewManager.enable()
    PlatformCoreDataKeys.VIRTUAL_FILE.getData(e.dataContext)?.let {
      EditorNotifications.getInstance(project).updateNotifications(it)
    }
  }
}

/**
 * [AnAction] that requests a build of the file returned by [fileProvider] and its dependencies.
 */
class BuildAndRefresh(private val fileProvider: () -> PsiFile?) : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val file = fileProvider() ?: return
    file.project.requestBuild(file.virtualFile)
  }
}

/**
 * [AnAction] that shows the "Problems" panel with the "Design Tools" tab selected. The name "Design Tools" is different depends on
 * different tools. e.g. it shows "Compose" when using Compose Preview, shows "Layout and Qualifiers" when using Layout Editor.
 *
 */
class ShowProblemsPanel : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    IssuePanelService.getInstance(project).setIssuePanelVisibility(true, IssuePanelService.Tab.DESIGN_TOOLS)
  }
}
