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

import com.android.tools.idea.backup.BackupAppAction.BackupInfo.Invalid
import com.android.tools.idea.backup.BackupAppAction.BackupInfo.Valid
import com.android.tools.idea.backup.BackupBundle.message
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import kotlinx.coroutines.launch

/** Backups the state of an app to a file */
internal class BackupAppAction(private val actionHelper: ActionHelper = ActionHelperImpl()) :
  AnAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    val project = e.project ?: return
    if (!StudioFlags.BACKUP_SHOW_BACKUP_ACTION_IN_MAIN_TOOLBAR.get()) {
      // For now, the only place this is shown is the main toolbar
      return
    }
    e.presentation.isVisible = true
    e.presentation.isEnabled = actionHelper.getApplicationId(project) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    project.coroutineScope(uiThread).launch {
      when (val backupInfo = e.getBackupInfo()) {
        is Invalid ->
          actionHelper.showWarning(
            project,
            message("backup.app.action.error.title"),
            backupInfo.reason,
          )
        is Valid -> doBackup(project, backupInfo)
      }
    }
  }

  private suspend fun doBackup(project: Project, backupInfo: Valid) {
    val backupManager = BackupManager.getInstance(project)
    val applicationId = backupInfo.applicationId
    val backupFile = backupManager.chooseBackupFile(applicationId) ?: return
    backupManager.backupModal(backupInfo.serialNumber, applicationId, backupFile)
  }

  private suspend fun AnActionEvent.getBackupInfo(): BackupInfo {
    val project = project ?: throw IllegalStateException("Missing project")
    val applicationId =
      actionHelper.getApplicationId(project)
        ?: return Invalid(message("error.incompatible.run.config"))

    val targetCount = actionHelper.getDeployTargetCount(project)
    when {
      targetCount == 0 -> return Invalid(message("error.device.not.running"))
      targetCount > 1 -> return Invalid(message("error.multiple.devices"))
    }

    // TODO(b/348406593) Validate GMS version

    val serialNumber =
      actionHelper.getDeployTargetSerial(project)
        ?: return Invalid(message("error.device.not.running"))
    return Valid(applicationId, serialNumber)
  }

  private sealed class BackupInfo {
    class Valid(val applicationId: String, val serialNumber: String) : BackupInfo()

    class Invalid(val reason: String) : BackupInfo()
  }
}
