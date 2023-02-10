/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.ddmlib.IDevice
import com.android.tools.adtui.compose.InformationPopup
import com.android.tools.adtui.compose.InformationPopupImpl
import com.android.tools.adtui.compose.IssueNotificationAction
import com.android.tools.idea.actions.BrowserHelpAction
import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.editors.sourcecode.isKotlinFileType
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.components.AnActionLink
import com.intellij.util.ui.JBUI
import java.awt.Insets
import java.util.Collections

internal fun getStatusInfo(project: Project, dataContext: DataContext): LiveEditStatus {
  val liveEditService = LiveEditService.getInstance(project)
  val editor: Editor? = dataContext.getData(CommonDataKeys.EDITOR)
  if (editor != null) {
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    if (!project.isInitialized ||
        psiFile == null ||
        !psiFile.virtualFile.isKotlinFileType() ||
        !editor.document.isWritable) {
      return LiveEditStatus.Disabled
    }

    val insetDevices = LiveEditIssueNotificationAction.deviceMap[project]?.devices()?.let { HashSet<IDevice>(it) } ?: Collections.emptySet()
    return liveEditService.devices()
      .filter { it !in insetDevices }
      .map { liveEditService.editStatus(it) }
      .fold(LiveEditStatus.Disabled, LiveEditStatus::merge)
  }
  else {
    val device = LiveEditIssueNotificationAction.deviceMap[project]?.device(dataContext) ?: return LiveEditStatus.Disabled
    return liveEditService.editStatus(device)
  }
}

/**
 * Creates an [InformationPopup]. The given [dataContext] will be used by the popup to query for
 * things like the current editor.
 */
internal fun defaultCreateInformationPopup(
  project: Project,
  dataContext: DataContext,
): InformationPopup? {
  return getStatusInfo(project, dataContext).let { status ->
    if (status == LiveEditStatus.Disabled) {
      return@let null
    }

    val link = status.actionId?.let {
      val action = ActionManager.getInstance().getAction(it)
      val shortcut = KeymapManager.getInstance()?.activeKeymap?.getShortcuts(it)?.toList()?.firstOrNull()
      AnActionLink("${action.templateText}${if (shortcut != null) " (${KeymapUtil.getShortcutText(shortcut)})" else ""}", action)
    }

    val configureLiveEditAction = ConfigureLiveEditAction()
    return@let InformationPopupImpl(
      null,
      status.description,
      listOfNotNull(ToggleLiveEditStatusAction()),
      listOfNotNull(
        link,
        AnActionLink("View Docs", BrowserHelpAction("Live Edit Docs", "https://developer.android.com/jetpack/compose/tooling/studio#iterative-code-dev")),
        object: AnActionLink("Configure Live Edit", configureLiveEditAction) {
        }.apply {
          setDropDownLinkIcon()
          configureLiveEditAction.parentComponent = this
        }
      )
    ).also { newPopup ->
      // Register the data provider of the popup to be the same as the one used in the toolbar.
      // This allows for actions within the popup to query for things like the Editor even when
      // the Editor is not directly related to the popup.
      DataManager.registerDataProvider(newPopup.popupComponent) { dataId ->
        if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.`is`(dataId)) {
          { id: String -> dataContext.getData(id) }
        }
        else {
          dataContext.getData(dataId)
        }
      }
      configureLiveEditAction.parentDisposable = newPopup
    }
  }
}

/**
 * An interface to exposed to external modules to implement in order to receive device serials and IDevices associated with those serials.
 */
interface DeviceGetter {
  fun serial(dataContext: DataContext): String?
  fun device(dataContext: DataContext): IDevice?
  fun devices(): List<IDevice>
}

/**
 * Action that reports the current state of Live Edit.
 *
 * This action reports:
 * - State of Live Edit or preview out of date if Live Edit is disabled
 * - Syntax errors
 */
class LiveEditIssueNotificationAction(
  createInformationPopup: (Project, DataContext) -> InformationPopup? =
    ::defaultCreateInformationPopup
) : IssueNotificationAction(::getStatusInfo, createInformationPopup) {
  companion object {
    val deviceMap = HashMap<Project, DeviceGetter>()

    fun registerProject(project: Project, deviceGetter: DeviceGetter) {
      deviceMap[project] = deviceGetter
    }

    fun unregisterProject(project: Project) {
      deviceMap.remove(project)
    }
  }

  override fun insets(): Insets {
    return JBUI.insets(1)
  }

  override fun margins(): Insets {
    return JBUI.insets(2)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}

/**
 * [DefaultActionGroup] that shows the notification chip and the
 * [ForceCompileAndRefreshActionForNotification] button when applicable.
 */
class LiveEditNotificationGroup :
  DefaultActionGroup(
    "Live Edit Notification Actions",
    listOf(LiveEditIssueNotificationAction(), ForceCompileAndRefreshActionForNotification())
  ), RightAlignedToolbarAction
