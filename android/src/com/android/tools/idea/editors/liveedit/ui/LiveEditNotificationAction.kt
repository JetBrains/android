/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.editors.liveedit.ui

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.status.IdeStatus
import com.android.tools.adtui.status.InformationPopup
import com.android.tools.adtui.status.InformationPopupImpl
import com.android.tools.adtui.status.IssueNotificationAction
import com.android.tools.adtui.status.POPUP_ACTION
import com.android.tools.adtui.status.REFRESH_BUTTON
import com.android.tools.adtui.ui.NotificationHolderPanel
import com.android.tools.idea.actions.BrowserHelpAction
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.liveedit.LiveEditService
import com.android.tools.idea.editors.liveedit.LiveEditService.Companion.LiveEditTriggerMode.ON_SAVE
import com.android.tools.idea.editors.liveedit.LiveEditService.Companion.PIGGYBACK_ACTION_ID
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.deployment.liveedit.LiveEditProjectMonitor
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.android.tools.idea.util.CommonAndroidUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.putUserData
import com.intellij.ui.components.AnActionLink
import com.intellij.util.containers.CollectionFactory.createWeakIdentityMap
import com.intellij.util.ui.JBUI
import org.jetbrains.android.refactoring.project
import java.awt.Insets

/**
 * Action that reports the current state of Live Edit.
 *
 * This action reports:
 * - State of Live Edit or preview out of date if Live Edit is disabled
 * - Syntax errors
 */
class LiveEditNotificationAction : IssueNotificationAction(::getStatusInfo, ::createInformationPopup) {

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    if (StudioFlags.LIVE_EDIT_COMPACT_STATUS_BUTTON.get()) {
      val project = e.project ?: return
      val status = getStatusInfo(project, e.dataContext)
      if (shouldHide(status, e.dataContext)) {
        presentation.isEnabledAndVisible = false
        return
      }
      presentation.icon = when (status.redeployMode) {
        LiveEditStatus.RedeployMode.REFRESH -> REFRESH_BUTTON
        LiveEditStatus.RedeployMode.RERUN -> AllIcons.Actions.Restart
        LiveEditStatus.RedeployMode.NONE -> status.icon
      }
      presentation.description = status.description
      val notificationPanel = NotificationHolderPanel.fromDataContext(e)
      if (notificationPanel != null) {
        project.service<NotificationPresenter>().showNotification(notificationPanel, status.notificationText)
      }
      presentation.isEnabledAndVisible = true
    } else {
      presentation.isEnabledAndVisible = false
    }
  }

  override fun margins(): Insets {
    return JBUI.insets(2)
  }

  @UiThread
  override fun shouldHide(status: IdeStatus, dataContext: DataContext): Boolean {
    return shouldHideImpl(status, dataContext)
  }

  @UiThread
  override fun shouldSimplify(status: IdeStatus, dataContext: DataContext): Boolean {
    return status.shouldSimplify
  }

  override fun getDisposableParentForPopup(e: AnActionEvent): Disposable? {
    return e.project?.let { LiveEditService.getInstance(it) }
  }
}

@Service(Service.Level.PROJECT)
internal class NotificationPresenter {

  private val notificationState = createWeakIdentityMap<NotificationHolderPanel, String>(10, 0.5f)

  /** Shows a fade-out notification if it is different from the previously shown in the same panel. */
  fun showNotification(notificationPanel: NotificationHolderPanel, text: String?) {
    val lastNotificationText = notificationState[notificationPanel]
    if (lastNotificationText != text) {
      if (text.isNullOrBlank()) {
        notificationState.remove(notificationPanel)
      } else {
        notificationState[notificationPanel] = text
        notificationPanel.showFadeOutNotification(text)
      }
    }
  }
}

@UiThread
private fun shouldHideImpl(status: IdeStatus, dataContext: DataContext): Boolean {
  dataContext.project?.let { if (!CommonAndroidUtil.getInstance().isAndroidProject(it)) return true } ?: return true
  if (status != LiveEditStatus.Disabled) {
    // Always show when it's an active status, even if error.
    return false
  }
  // Only show for running devices tool window.
  val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return true
  val serial = dataContext.getData(SERIAL_NUMBER_KEY) ?: return true
  val bridge = AdbService.getInstance().getDebugBridge(project).let {
    if (!it.isDone || it.isCancelled) {
      null
    } else {
      try {
        it.get()
      } catch (_: Exception) {
        null
      }
    }
  }
  val device = bridge?.devices?.find { it.serialNumber == serial } ?: return true
  // Hide status when the device doesn't support Live Edit.
  if (!LiveEditProjectMonitor.supportLiveEdits(device)) {
    return true
  }
  // Hide status when the project isn't Compose.
  if (!LiveEditService.usesCompose(project)) {
    return true
  }
  // Hide status when Live Edit is already enabled (note: status is Disabled if we get to this part of the code).
  return LiveEditApplicationConfiguration.getInstance().isLiveEdit
}

/**
 * Creates an [InformationPopup]. The given [dataContext] will be used by the popup to query for
 * things like the current editor.
 */
private fun createInformationPopup(project: Project, dataContext: DataContext) : InformationPopup? {
  return getStatusInfo(project, dataContext).let { status ->
    if (shouldHideImpl(status, dataContext)) {
      return@let null
    }

    val redeployActionId = when (status.redeployMode) {
      LiveEditStatus.RedeployMode.REFRESH -> "Compose.Live.Edit.Refresh"
      LiveEditStatus.RedeployMode.RERUN -> "Run"
      else -> null
    }
    val redeployLink: AnActionLink? = redeployActionId?.let { createActionLink(it) }

    val statusActionId = when (status.actionId) {
      null, redeployActionId -> null
      REFRESH_ACTION_ID -> getRefreshActionId()
      else -> status.actionId
    }
    val statusActionLink = statusActionId?.let { createActionLink(it) }

    val upgradeAssistant =
      if (status.title == LiveEditStatus.OutOfDate.title &&
          status.description.contains(LiveEditUpdateException.Error.UNSUPPORTED_BUILD_LIBRARY_DESUGAR.message)) {
        AnActionLink("View Upgrade Assistant", object : AnAction() {
          override fun actionPerformed(e: AnActionEvent) {
            ActionUtil.invokeAction(ActionManager.getInstance().getAction("AgpUpgrade"), e, null)
          }
        })
      } else {
        null
      }

    val configureLiveEditAction = ConfigureLiveEditAction()
    return@let InformationPopupImpl(
      null,
      if (LiveEditService.isLeTriggerManual()) status.descriptionManualMode ?: status.description else status.description,
      emptyList(),
      listOfNotNull(
        redeployLink,
        statusActionLink,
        upgradeAssistant,
        AnActionLink("View Docs",
                     BrowserHelpAction("Live Edit Docs",
                                       "https://developer.android.com/jetpack/compose/tooling/iterative-development#live-edit")),
        AnActionLink("Configure Live Edit", configureLiveEditAction).apply {
          setDropDownLinkIcon()
          configureLiveEditAction.parentComponent = this
          putUserData(POPUP_ACTION, true)
        }
      )
    ).also { newPopup ->
      configureLiveEditAction.parentDisposable = newPopup
    }
  }
}

private fun getRefreshActionId(): String =
  if (LiveEditApplicationConfiguration.getInstance().leTriggerMode == ON_SAVE) PIGGYBACK_ACTION_ID else MANUAL_LIVE_EDIT_ACTION_ID

private fun createActionLink(actionId: String): AnActionLink? {
  val action = ActionManager.getInstance().getAction(actionId) ?: return null
  val shortcut = KeymapManager.getInstance()?.activeKeymap?.getShortcuts(actionId)?.toList()?.firstOrNull()
  val shortcutText = shortcut?.let { " (${KeymapUtil.getShortcutText(it)})" } ?: ""
  return AnActionLink("${action.templateText}$shortcutText", action)
}
