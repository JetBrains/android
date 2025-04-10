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

import com.android.tools.adtui.status.IdeStatus
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.rendering.tokens.requestBuildArtifactsForRendering
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiFile
import com.intellij.ui.AnimatedIcon
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import javax.swing.Icon

/** Represents the Preview status to be notified to the user. */
sealed class PreviewStatus(
  override val icon: Icon?,
  override val title: String,
  override val description: String,
  override val presentation: IdeStatus.Presentation? = null,
  /** When true, the refresh icon will be displayed next to the notification chip. */
  val hasRefreshIcon: Boolean = false,
) : IdeStatus {
  /** The Preview found a syntax error and paused the updates. */
  object SyntaxError :
    PreviewStatus(
      AllIcons.General.InspectionsPause,
      message("notification.syntax.errors.title"),
      message("notification.syntax.errors.description"),
      null,
      false,
    )

  /** The Preview found a compilation error and paused the updates. */
  object NeedsBuild :
    PreviewStatus(
      AllIcons.General.Error,
      message("notification.needs.build.broken.title"),
      message("notification.needs.build.broken.description"),
      IdeStatus.Presentation.Error,
      true,
    )

  /** The Preview is refreshing. */
  class Refreshing(
    detailsMessage: String = message("notification.preview.refreshing.description")
  ) :
    PreviewStatus(
      AnimatedIcon.Default(),
      message("notification.preview.refreshing.title"),
      detailsMessage,
    )

  /** The Preview is out of date. This state will not happen if Fast Preview is enabled. */
  object OutOfDate :
    PreviewStatus(
      AllIcons.General.Warning,
      message("notification.preview.out.of.date.title"),
      message("notification.preview.out.of.date.description"),
      IdeStatus.Presentation.Warning,
      true,
    )

  /** The Preview is compiling. */
  object FastPreviewCompiling :
    PreviewStatus(
      AnimatedIcon.Default(),
      message("notification.preview.fast.compile.title"),
      message("notification.preview.fast.compile.description"),
    )

  /** An issue was found while rendering the Preview. */
  object RenderIssues :
    PreviewStatus(
      AllIcons.General.Warning,
      message("notification.preview.render.issues.title"),
      message("notification.preview.render.issues.description"),
      IdeStatus.Presentation.Warning,
      true,
    )

  /** The Preview is fully up to date. */
  object UpToDate :
    PreviewStatus(
      AllIcons.General.InspectionsOK,
      message("notification.preview.up.to.date.title"),
      message("notification.preview.up.to.date.description"),
    )
}

/**
 * [AnAction] that requests a build of the file returned by [fileProvider] and its dependencies.
 *
 * @param [fileProvider] is lambda providing the [PsiFile] used to request the build. This lambda
 *   will be called under a read lock.
 */
class BuildAndRefresh(@RequiresReadLock private val fileProvider: () -> PsiFile?) : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    ReadAction.nonBlocking<PsiFile?> { fileProvider() }
      .submit(AppExecutorUtil.getAppExecutorService())
      .onSuccess {
        val file = it ?: return@onSuccess
        ApplicationManager.getApplication().executeOnPooledThread {
          file.project.requestBuildArtifactsForRendering(file.virtualFile)
        }
      }
  }
}

/**
 * [AnAction] that shows the "Problems" panel with the "Design Tools" tab selected. The name "Design
 * Tools" is different depends on different tools. e.g. it shows "Compose" when using Compose
 * Preview, shows "Layout and Qualifiers" when using Layout Editor.
 */
class ShowProblemsPanel : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    IssuePanelService.getInstance(project).showSharedIssuePanel()
  }
}
