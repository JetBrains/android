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
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.error.setIssuePanelVisibilityNoTracking
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.editors.fast.fastPreviewManager
import com.android.tools.idea.editors.shortcuts.asString
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.requestBuild
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.notification.EventLog
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorNotifications
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.AnActionLink
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JToolTip
import javax.swing.SwingConstants
import javax.swing.border.Border

/**
 * Utility getter that indicates if the project needs a build. This is the case if the previews build is not valid, like after a clean or
 * cancelled, or if it has failed.
 */
internal val Project.needsBuild: Boolean
  get() {
    val lastBuildResult = ProjectSystemService.getInstance(project = this).projectSystem.getBuildManager().getLastBuildResult()
    return lastBuildResult.status == ProjectSystemBuildManager.BuildStatus.CANCELLED ||
           lastBuildResult.status == ProjectSystemBuildManager.BuildStatus.FAILED ||
           lastBuildResult.mode == ProjectSystemBuildManager.BuildMode.CLEAN
  }

private const val ACTION_BACKGROUND_ALPHA = 0x1A
private const val ACTION_BORDER_ALPHA = 0xBF
private const val ACTION_BORDER_ARC_SIZE = 5
private const val ACTION_BORDER_THICKNESS = 1

private fun chipBorder(color: Color): Border = RoundedLineBorder(UIUtil.toAlpha(color, ACTION_BORDER_ALPHA),
                                                                 ACTION_BORDER_ARC_SIZE,
                                                                 ACTION_BORDER_THICKNESS)

/**
 * Represents the Preview status to be notified to the user.
 */
sealed class PreviewStatusNotification(
  val icon: Icon?,
  val title: String,
  val description: String,
  /** When true, the refresh icon will be displayed next to the notification chip. */
  val hasRefreshIcon: Boolean = false,
  val presentation: Presentation? = null
) {
  companion object {
    val PRESENTATION = Key<Presentation>("PreviewStatusNotificationPresentation")

    /**
     * When not null, this will define the text alignment in the notification chip. One of [SwingConstants.LEADING] or
     * [SwingConstants.TRAILING].
     */
    val TEXT_ALIGNMENT = Key<Int>("PreviewStatusNotificationTextAlignment")
  }

  /**
   * Enum representing the different UI color states that the action might have for the border and background.
   */
  enum class Presentation(baseColorLight: Int, baseColorDark: Int = baseColorLight) {
    Error(0xE53E4D),
    Warning(0xEDA200);

    val color = JBColor(UIUtil.toAlpha(Color(baseColorLight), ACTION_BACKGROUND_ALPHA),
                        UIUtil.toAlpha(Color(baseColorDark), ACTION_BACKGROUND_ALPHA))
    val border = chipBorder(color)
  }


  /**
   * The Preview found a syntax error and paused the updates.
   */
  object SyntaxError : PreviewStatusNotification(
    AllIcons.General.InspectionsPause,
    message("notification.syntax.errors.title"),
    message("notification.syntax.errors.description"),
    false)

  /**
   * The Preview found a compilation error and paused the updates.
   */
  object NeedsBuild : PreviewStatusNotification(
    AllIcons.General.Error,
    message("notification.needs.build.broken.title"),
    message("notification.needs.build.broken.description"),
    true,
    Presentation.Error)

  /**
   * The Preview is refreshing.
   */
  class Refreshing(detailsMessage: String = message("notification.preview.refreshing.description"))
    : PreviewStatusNotification(
    AnimatedIcon.Default(),
    message("notification.preview.refreshing.title"),
    detailsMessage)

  /**
   * The Preview is out of date. This state will not happen if Fast Preview is enabled.
   */
  object OutOfDate : PreviewStatusNotification(
    AllIcons.General.Warning,
    message("notification.preview.out.of.date.title"),
    message("notification.preview.out.of.date.description"),
    true,
    Presentation.Warning)

  /**
   * The Preview is compiling.
   */
  object FastPreviewCompiling : PreviewStatusNotification(
    AnimatedIcon.Default(),
    message("notification.preview.fast.compile.title"),
    message("notification.preview.fast.compile.description"))

  /**
   * An issue was found while rendering the Preview.
   */
  object RenderIssues : PreviewStatusNotification(
    AllIcons.General.Warning,
    message("notification.preview.render.issues.title"),
    message("notification.preview.render.issues.description"),
    true,
    Presentation.Warning
  )

  /**
   * The Preview has failed to compile a fast change.
   */
  object FastPreviewFailed : PreviewStatusNotification(
    AllIcons.General.InspectionsPause,
    message("notification.preview.fast.disabled.reason.compiler.error.title"),
    message("notification.preview.fast.disabled.reason.compiler.error.description"),
    true,
    Presentation.Error)

  /**
   * The Preview is fully up to date.
   */
  object UpToDate : PreviewStatusNotification(
    AllIcons.General.InspectionsOK,
    message("notification.preview.up.to.date.title"),
    message("notification.preview.up.to.date.description"))
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
    !fastPreviewEnabled && project.fastPreviewManager.isAutoDisabled -> PreviewStatusNotification.FastPreviewFailed
    fastPreviewEnabled && project.fastPreviewManager.isCompiling -> PreviewStatusNotification.FastPreviewCompiling

    // Up-to-date
    else -> PreviewStatusNotification.UpToDate
  }
}

/**
 * [AnAction] that will show the Event Log.
 */
private class ShowEventLogAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    EventLog.getEventLog(project)?.activate(null) ?: ToolWindowManager.getInstance(project).getToolWindow("Notifications")?.activate(null)
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

private class ComposePreviewManagerFileProvider(dataContext: DataContext): () -> PsiFile? {
  private val composePreviewManager = WeakReference(dataContext.getData(COMPOSE_PREVIEW_MANAGER))

  override fun invoke(): PsiFile? {
    return composePreviewManager.get()?.previewedFile
  }
}

/**
 * [AnAction] that re-enable the Fast Preview if disabled.
 */
private class BuildAndRefresh(private val fileProvider: () -> PsiFile?) : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val file = fileProvider() ?: return
    file.project.requestBuild(file.virtualFile)
  }
}

/**
 * [AnAction] that shows the "Problems" panel.
 */
private class ShowProblemsPanel : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ProblemsView.getToolWindow(project)?.show()
  }
}

/**
 * [AnAction] that shows the "Problems" panel.
 */
private class ShowIssuesPanel : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.getData(DESIGN_SURFACE)?.let { surface ->
      surface.setIssuePanelVisibilityNoTracking(true, true)
    }
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
 * Creates an [InformationPopup]. The given [dataContext] will be used by the popup to query for things like the current editor.
 */
@VisibleForTesting
fun defaultCreateInformationPopup(
  project: Project,
  dataContext: DataContext,
): InformationPopup? {
  val fileProvider = ComposePreviewManagerFileProvider(dataContext)::invoke
  return getStatusInfo(project, dataContext)?.let {
    val isAutoDisabled = it is PreviewStatusNotification.FastPreviewFailed && project.fastPreviewManager.isAutoDisabled
    return@let InformationPopup(
      null,
      it.description,
      listOfNotNull(
        StudioFlags.COMPOSE_FAST_PREVIEW.ifEnabled { ToggleFastPreviewAction() }
      ),
      listOfNotNull(
        actionLink(
          message("action.build.and.refresh.title")
            .replace("&&", "&") + getBuildAndRefreshShortcut().asString(), // Remove any ampersand escaping for tooltips (not needed in these links)
          BuildAndRefresh(fileProvider), dataContext),
        when (it) {
          is PreviewStatusNotification.SyntaxError -> actionLink(message("action.view.problems"), ShowProblemsPanel(), dataContext)
          is PreviewStatusNotification.RenderIssues -> actionLink(message("action.view.problems"), ShowIssuesPanel(), dataContext)
          else -> null
        },
        if (isAutoDisabled)
          actionLink(message("fast.preview.disabled.notification.reenable.action.title"), ReEnableFastPreview(), dataContext)
        else null,
        if (isAutoDisabled)
          actionLink(message("fast.preview.disabled.notification.stop.autodisable.action.title"), ReEnableFastPreview(false), dataContext)
        else null,
        if (it is PreviewStatusNotification.FastPreviewFailed)
          actionLink(message("fast.preview.disabled.notification.show.details.action.title"), ShowEventLogAction(), dataContext)
        else null
      )).also { newPopup ->
      // Register the data provider of the popup to be the same as the one used in the toolbar. This allows for actions within the
      // popup to query for things like the Editor even when the Editor is not directly related to the popup.
      DataManager.registerDataProvider(newPopup.component()) { dataId -> dataContext.getData(dataId) }
    }
  }
}

/**
 * Action that reports the current state of the Preview. Local issues for a given preview are reported as part of the preview itself
 * and not in this action.
 *
 * Clicking on the action will open a pop-up with additional details and action buttons.
 */
@VisibleForTesting
open class IssueNotificationAction(
  private val createStatusInfo: (Project, DataContext) -> PreviewStatusNotification?,
  private val createInformationPopup: (Project, DataContext) -> InformationPopup?
) : AnAction(), RightAlignedToolbarAction, CustomComponentAction, Disposable {  /**
   * [Alarm] used to trigger the popup as a hint.
   */
  private val popupAlarm = Alarm()

  /**
   * [MouseAdapter] that schedules the popup.
   */
  private val mouseListener = object : MouseAdapter() {
    override fun mouseEntered(me: MouseEvent) {
      popupAlarm.cancelAllRequests()
      popupAlarm.addRequest(
        {
          if (popup?.isVisible() == true) return@addRequest // Do not show if already showing
          val anActionEvent = AnActionEvent.createFromInputEvent(
            me,
            ActionPlaces.EDITOR_POPUP,
            PresentationFactory().getPresentation(this@IssueNotificationAction),
            ActionToolbar.getDataContextFor(me.component),
            false, true)
          showPopup(anActionEvent)
        },
        Registry.intValue("ide.tooltip.initialReshowDelay"))
    }

    override fun mouseExited(me: MouseEvent) {
      popupAlarm.cancelAllRequests()
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
    object : ActionButtonWithText(this, presentation, place, Dimension(0, 0)) {
      private val insets = JBUI.insets(3)
      private val actionPresentation: PreviewStatusNotification.Presentation?
        get() = myPresentation.getClientProperty(PreviewStatusNotification.PRESENTATION)
      val textAlignment: Int
        get() = myPresentation.getClientProperty(PreviewStatusNotification.TEXT_ALIGNMENT) ?: SwingConstants.LEADING

      private val font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL)

      private val textColor = JBColor(Gray._110, Gray._187)

      override fun isBackgroundSet(): Boolean =
        actionPresentation != null || super.isBackgroundSet()

      override fun getBackground(): Color? =
        actionPresentation?.color ?: super.getBackground()

      override fun getFont() = font

      override fun getForeground() = textColor

      override fun getBorder(): Border =
        if (popState == POPPED)
          chipBorder(JBUI.CurrentTheme.ActionButton.hoverBorder())
        else
          actionPresentation?.border ?: JBUI.Borders.empty()

      override fun getMargins(): Insets = insets

      override fun addNotify() {
        super.addNotify()
        addMouseListener(mouseListener)
        setHorizontalTextPosition(textAlignment)
      }

      override fun removeNotify() {
        removeMouseListener(mouseListener)
        super.removeNotify()
      }

      override fun createToolTip(): JToolTip? = null

      // Do not display the regular tooltip
      override fun updateToolTipText() {}

    }.apply {
      setHorizontalTextPosition(textAlignment)
    }

  override fun displayTextInToolbar(): Boolean = true

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val presentation = e.presentation
    createStatusInfo(project, e.dataContext)?.let {
      presentation.icon = it.icon
      presentation.text = it.title
      presentation.description = it.description
      presentation.putClientProperty(PreviewStatusNotification.PRESENTATION, it.presentation)
      val isErrorOrWarningIcon = it.icon == AllIcons.General.Error || it.icon == AllIcons.General.Warning
      presentation.putClientProperty(PreviewStatusNotification.TEXT_ALIGNMENT,
                                     if (isErrorOrWarningIcon) SwingConstants.TRAILING else SwingConstants.LEADING)
    }
  }

  /**
   * The currently opened popup.
   */
  private var popup: InformationPopup? = null

  /**
   * Shows the actions popup.
   */
  private fun showPopup(e: AnActionEvent) {
    popupAlarm.cancelAllRequests()
    val project = e.project ?: return
    popup = createInformationPopup(project, e.dataContext)?.also { newPopup ->
      Disposer.register(this, newPopup)
      newPopup.showPopup(e.inputEvent)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    showPopup(e)
  }

  override fun dispose() {
    popup?.hidePopup()
    popupAlarm.cancelAllRequests()
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
  createInformationPopup: (Project, DataContext) -> InformationPopup? = ::defaultCreateInformationPopup
) : IssueNotificationAction(
  ::getStatusInfo,
  createInformationPopup
)

/**
 * [ForceCompileAndRefreshAction] where the visibility is controlled by the [PreviewStatusNotification.hasRefreshIcon].
 */
private class ForceCompileAndRefreshActionForNotification(surface: DesignSurface<*>) : ForceCompileAndRefreshAction(
  surface), RightAlignedToolbarAction {
  override fun update(e: AnActionEvent) {
    super.update(e)

    val project = e.project ?: return

    getStatusInfo(project, e.dataContext)?.let {
      e.presentation.isVisible = it.hasRefreshIcon
    }
  }
}

/**
 * [DefaultActionGroup] that shows the notification chip and the [ForceCompileAndRefreshActionForNotification] button when applicable.
 */
class ComposeNotificationGroup(surface: DesignSurface<*>) : DefaultActionGroup(
  listOf(
    ComposeIssueNotificationAction(),
    ForceCompileAndRefreshActionForNotification(surface))
)
