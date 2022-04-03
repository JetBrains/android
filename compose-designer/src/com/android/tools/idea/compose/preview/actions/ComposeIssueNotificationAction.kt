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
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.fast.FastPreviewManager
import com.android.tools.idea.compose.preview.fast.fastPreviewManager
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.requestBuild
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.intellij.icons.AllIcons
import com.intellij.notification.EventLog
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorNotifications
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.AnActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import java.lang.ref.WeakReference
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.border.Border

/**
 * Utility getter that indicates if the project needs a build. This is the case if the previews build is not valid, like after a clean or
 * cancelled, or if it has failed.
 */
private val Project.needsBuild: Boolean
  get() {
    val lastBuildResult = ProjectSystemService.getInstance(project = this).projectSystem.getBuildManager().getLastBuildResult()
    return lastBuildResult.status == ProjectSystemBuildManager.BuildStatus.CANCELLED ||
           lastBuildResult.status == ProjectSystemBuildManager.BuildStatus.FAILED ||
           lastBuildResult.mode == ProjectSystemBuildManager.BuildMode.CLEAN
  }

private const val ACTION_BACKGROUND_ALPHA = 0x30
private const val ACTION_BORDER_ALPHA = 0xC8
private const val ACTION_BORDER_ARC_SIZE = 5
private const val ACTION_BORDER_THICKNESS = 1

/**
 * Represents the Compose Preview status to be notified to the user.
 */
private sealed class ComposePreviewStatusNotification(
  val icon: Icon?,
  val title: String,
  val description: String,
  val presentation: Presentation? = null
) {
  companion object {
    val PRESENTATION = Key<Presentation>("ComposePreviewStatusNotificationPresentation")
  }

  /**
   * Enum representing the different UI color states that the action might have for the border and background.
   */
  enum class Presentation(baseColorLight: Int, baseColorDark: Int = baseColorLight) {
    Error(0xFF0000),
    Warning(0xFDFF00);

    val color = JBColor(UIUtil.toAlpha(Color(baseColorLight), ACTION_BACKGROUND_ALPHA),
                        UIUtil.toAlpha(Color(baseColorDark), ACTION_BACKGROUND_ALPHA))
    val border = RoundedLineBorder(UIUtil.toAlpha(color, ACTION_BORDER_ALPHA),
                                   ACTION_BORDER_ARC_SIZE,
                                   ACTION_BORDER_THICKNESS)
  }


  /**
   * The Preview found a syntax error and paused the updates.
   */
  object SyntaxError : ComposePreviewStatusNotification(
    AllIcons.General.Error,
    message("notification.syntax.errors.title"),
    message("notification.syntax.errors.description"),
    Presentation.Error)

  /**
   * The Preview found a compilation error and paused the updates.
   */
  object NeedsBuild : ComposePreviewStatusNotification(
    AllIcons.General.Error,
    message("notification.needs.build.broken.title"),
    message("notification.needs.build.broken.description"),
    Presentation.Warning)

  /**
   * The Preview is refreshing.
   */
  class Refreshing(detailsMessage: String = message("notification.preview.refreshing.description"))
    : ComposePreviewStatusNotification(
    AnimatedIcon.Default(),
    message("notification.preview.refreshing.title"),
    detailsMessage)

  /**
   * The Preview is out of date. This state will not happen if Fast Preview is enabled.
   */
  object OutOfDate : ComposePreviewStatusNotification(
    AllIcons.General.Warning,
    message("notification.preview.out.of.date.title"),
    message("notification.preview.out.of.date.description"),
    Presentation.Warning)

  /**
   * The Preview is compiling.
   */
  object FastPreviewCompiling : ComposePreviewStatusNotification(
    AnimatedIcon.Default(),
    message("notification.preview.fast.compile.title"),
    message("notification.preview.fast.compile.description"))

  /**
   * The Preview has failed to compile a fast change.
   */
  object FastPreviewFailed : ComposePreviewStatusNotification(
    AllIcons.General.InspectionsPause,
    message("notification.preview.fast.disabled.reason.compiler.error.title"),
    message("notification.preview.fast.disabled.reason.compiler.error.description"),
    Presentation.Error)

  /**
   * The Preview is fully up to date.
   */
  object UpToDate : ComposePreviewStatusNotification(
    AllIcons.General.InspectionsOK,
    message("notification.preview.up.to.date.title"),
    message("notification.preview.up.to.date.description"))
}

private fun ComposePreviewManager.getStatusInfo(project: Project): ComposePreviewStatusNotification {
  val previewStatus = status()
 return when {
    // Refresh status
   previewStatus.interactiveMode == ComposePreviewManager.InteractiveMode.STARTING ->
      ComposePreviewStatusNotification.Refreshing(message("notification.interactive.preview.starting"))
    previewStatus.interactiveMode == ComposePreviewManager.InteractiveMode.STOPPING ->
      ComposePreviewStatusNotification.Refreshing(message("notification.interactive.preview.stopping"))
    previewStatus.isRefreshing -> ComposePreviewStatusNotification.Refreshing()

    // Build/Syntax errors
    project.needsBuild -> ComposePreviewStatusNotification.NeedsBuild
    previewStatus.hasSyntaxErrors -> ComposePreviewStatusNotification.SyntaxError

    !FastPreviewManager.getInstance(project).isEnabled && previewStatus.isOutOfDate -> ComposePreviewStatusNotification.OutOfDate

    // Fast preview refresh/failures
    project.fastPreviewManager.isAutoDisabled -> ComposePreviewStatusNotification.FastPreviewFailed
    project.fastPreviewManager.isCompiling -> ComposePreviewStatusNotification.FastPreviewCompiling

    // Up-to-date
    else -> ComposePreviewStatusNotification.UpToDate
  }
}

/**
 * [AnAction] that will show the Event Log.
 */
private class ShowEventLogAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    EventLog.getEventLog(project)?.activate(null)
  }
}

/**
 * [AnAction] that re-enable the Fast Preview if disabled.
 */
private class ReEnableFastPreview(private val allowAutoDisable: Boolean = true) : AnAction() {
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
 * [AnAction] that re-enable the Fast Preview if disabled.
 */
private class BuildAndRefresh(composePreviewManager: ComposePreviewManager) : AnAction() {
  private val composePreviewManager = WeakReference(composePreviewManager)
  override fun actionPerformed(e: AnActionEvent) {
    val file = composePreviewManager.get()?.previewedFile ?: return
    requestBuild(file.project, file.virtualFile, true)
  }
}

/**
 * Creates a new [AnActionLink] with the given [text]. The returned [AnActionLink] will use the given [delegateDataContext] to obtain
 * the associated information when calling into the [action].
 */
fun actionLink(text: String, action: AnAction, delegateDataContext: DataContext): AnActionLink =
  AnActionLink(text, action).apply {
    dataProvider = DataProvider { dataId -> delegateDataContext.getData(dataId) }
  }

/**
 * Action that reports the current state of the Compose Preview. Local issues for a given preview are reported as part of the preview itself
 * and not in this action.
 * This action reports:
 * - State of Live Edit or preview out of date if Live Edit is disabled
 * - Syntax errors
 *
 * Clicking on the action will open a pop-up with additional details and action buttons.
 */
class ComposeIssueNotificationAction : AnAction(), CustomComponentAction, Disposable {
  override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
    object : ActionButtonWithText(this, presentation, place, Dimension(0, 0)) {
      private val insets = JBUI.insets(3)

      override fun isBackgroundSet(): Boolean =
        myPresentation.getClientProperty(ComposePreviewStatusNotification.PRESENTATION) != null || super.isBackgroundSet()

      override fun getBackground(): Color? =
        myPresentation.getClientProperty(ComposePreviewStatusNotification.PRESENTATION)?.color ?: super.getBackground()

      override fun getBorder(): Border =
        myPresentation.getClientProperty(ComposePreviewStatusNotification.PRESENTATION)?.border ?: JBUI.Borders.empty()

      override fun getMargins(): Insets = insets

    }.apply {
      setHorizontalTextPosition(SwingConstants.LEADING)
      font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL)
      foreground = UIUtil.getLabelDisabledForeground()
    }

  override fun displayTextInToolbar(): Boolean = true

  override fun update(e: AnActionEvent) {
    val composePreviewManager = e.getData(COMPOSE_PREVIEW_MANAGER) ?: return
    val project = e.project ?: return
    val presentation = e.presentation
    composePreviewManager.getStatusInfo(project).let {
      presentation.icon = it.icon
      presentation.text = it.title
      presentation.description = it.description
      presentation.putClientProperty(ComposePreviewStatusNotification.PRESENTATION, it.presentation)
    }
  }

  /**
   * The currently opened popup.
   */
  private var popup: InformationPopup? = null

  override fun actionPerformed(e: AnActionEvent) {
    val composePreviewManager = e.getData(COMPOSE_PREVIEW_MANAGER) ?: return
    val project = e.project ?: return
    composePreviewManager.getStatusInfo(project).let {
      val isAutoDisabled = it is ComposePreviewStatusNotification.FastPreviewFailed && project.fastPreviewManager.isAutoDisabled
      popup = InformationPopup(
        null,
        it.description,
        listOfNotNull(
          StudioFlags.COMPOSE_FAST_PREVIEW.ifEnabled { ToggleFastPreviewAction() }
        ),
        listOfNotNull(
          if (it is ComposePreviewStatusNotification.NeedsBuild)
            actionLink(message("fast.preview.disabled.notification.reenable.action.title"), BuildAndRefresh(composePreviewManager), e.dataContext)
          else null,
          if (isAutoDisabled)
            actionLink(message("fast.preview.disabled.notification.reenable.action.title"), ReEnableFastPreview(), e.dataContext)
          else null,
          if (isAutoDisabled)
            actionLink(message("fast.preview.disabled.notification.stop.autodisable.action.title"), ReEnableFastPreview(false), e.dataContext)
          else null,
          if (it is ComposePreviewStatusNotification.FastPreviewFailed)
            actionLink(message("fast.preview.disabled.notification.show.details.action.title"), ShowEventLogAction(), e.dataContext)
          else null
        )).also { newPopup ->
        Disposer.register(this, newPopup)
        newPopup.showPopup(e.inputEvent)
      }
    }
  }

  override fun dispose() {}
}