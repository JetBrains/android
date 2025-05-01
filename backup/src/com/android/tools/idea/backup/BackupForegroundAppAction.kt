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

import com.android.tools.idea.backup.BackupBundle.message
import com.android.tools.idea.backup.BackupManager.Source.BACKUP_FOREGROUND_APP_ACTION
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

/** Backups the state of the foreground app to a file */
internal class BackupForegroundAppAction(
  private val dialogFactory: DialogFactory = DialogFactoryImpl()
) : AnAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    if (!StudioFlags.BACKUP_ENABLED.get()) {
      return
    }
    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val backupManager = BackupManager.getInstance(project)
    val serialNumber = getDeviceSerialNumber(e)
    if (serialNumber == null) {
      project.showDialog(message("error.device.not.ready"))
      return
    }
    backupManager.showBackupDialog(serialNumber, null, BACKUP_FOREGROUND_APP_ACTION)
  }

  private fun getDeviceSerialNumber(e: AnActionEvent): String? {
    return SERIAL_NUMBER_KEY.getData(e.dataContext)
  }

  private fun Project.showDialog(message: String) {
    dialogFactory.showDialog(this@showDialog, message("backup.app.action.error.title"), message)
  }
}
