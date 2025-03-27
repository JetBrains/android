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

package com.android.tools.idea.backup

import com.android.tools.idea.actions.disableRichTooltip
import com.android.tools.idea.actions.enableRichTooltip
import com.android.tools.idea.backup.BackupAppAction.BackupInfo.Invalid
import com.android.tools.idea.backup.BackupAppAction.BackupInfo.Valid
import com.android.tools.idea.backup.BackupBundle.message
import com.android.tools.idea.backup.BackupManager.Source.BACKUP_APP_ACTION
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Backups the state of an app to a file */
internal class BackupAppAction(
  private val actionHelper: ActionHelper = ActionHelperImpl(),
  private val dialogFactory: DialogFactory = DialogFactoryImpl(),
) : AnAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    if (!StudioFlags.BACKUP_ENABLED.get()) {
      // For now, the only place this is shown is the main toolbar
      return
    }
    e.presentation.isVisible = true
    val project = e.project ?: return
    if (actionHelper.getApplicationId(project) == null) {
      e.presentation.enableRichTooltip(this, "Selected run configuration is not an app")
      return
    }
    e.presentation.disableRichTooltip()
    e.presentation.isEnabled = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    project.coroutineScope.launch {
      when (val backupInfo = e.getBackupInfo()) {
        is Invalid -> backupInfo.showDialog(project)
        is Valid -> doBackup(project, backupInfo)
      }
    }
  }

  private suspend fun doBackup(project: Project, backupInfo: Valid) {
    val backupManager = BackupManager.getInstance(project)
    withContext(Dispatchers.EDT) {
      backupManager.showBackupDialog(
        backupInfo.serialNumber,
        backupInfo.applicationId,
        BACKUP_APP_ACTION,
      )
    }
  }

  private suspend fun AnActionEvent.getBackupInfo(): BackupInfo {
    val project = project ?: throw IllegalStateException("Missing project")
    val backupManager = BackupManager.getInstance(project)
    val serialNumber =
      actionHelper.getDeployTargetSerial(project)
        ?: return Invalid(message("error.device.not.running"))
    if (!backupManager.isDeviceSupported(serialNumber)) {
      return Invalid(message("error.device.not.supported"))
    }
    val applicationId =
      actionHelper.getApplicationId(project)
        ?: return Invalid(message("error.incompatible.run.config"))

    val targetCount = actionHelper.getDeployTargetCount(project)
    return when {
      targetCount == 0 -> Invalid(message("error.device.not.running"))
      targetCount > 1 -> Invalid(message("error.multiple.devices"))
      !backupManager.isInstalled(serialNumber, applicationId) ->
        Invalid(message("error.application.not.installed"))
      else -> Valid(applicationId, serialNumber)
    }
  }

  private sealed class BackupInfo {
    class Valid(val applicationId: String, val serialNumber: String) : BackupInfo()

    class Invalid(val reason: String) : BackupInfo()
  }

  private suspend fun Invalid.showDialog(project: Project) {
    dialogFactory.showDialog(project, message("backup.app.action.error.title"), reason)
  }
}
