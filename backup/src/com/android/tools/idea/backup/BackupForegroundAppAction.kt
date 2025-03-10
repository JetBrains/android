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

import com.android.adblib.DeviceSelector
import com.android.tools.idea.backup.BackupBundle.message
import com.android.tools.idea.backup.BackupManager.Source.BACKUP_FOREGROUND_APP_ACTION
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Backups the state of the foreground app to a file */
internal class BackupForegroundAppAction(
  private val actionHelper: ActionHelper = ActionHelperImpl(),
  private val dialogFactory: DialogFactory = DialogFactoryImpl(),
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
    val deviceProvisioner = project.service<DeviceProvisionerService>().deviceProvisioner
    deviceProvisioner.scope.launch {
      if (serialNumber == null) {
        project.showDialog(message("error.device.not.ready"))
        return@launch
      }
      if (!backupManager.isDeviceSupported(serialNumber)) {
        project.showDialog(message("error.device.not.supported"))
        return@launch
      }
      val isCompatible = actionHelper.checkCompatibleApps(project, serialNumber)
      if (!isCompatible) {
        project.showDialog(message("error.applications.not.installed"))
        return@launch
      }
      val handle =
        deviceProvisioner.findConnectedDeviceHandle(DeviceSelector.fromSerialNumber(serialNumber))
      handle?.scope?.launch {
        val applicationId = backupManager.getForegroundApplicationId(serialNumber)
        withContext(Dispatchers.EDT) {
          backupManager.showBackupDialog(serialNumber, applicationId, BACKUP_FOREGROUND_APP_ACTION)
        }
      }
    }
  }

  private fun getDeviceSerialNumber(e: AnActionEvent): String? {
    return SERIAL_NUMBER_KEY.getData(e.dataContext)
  }

  private suspend fun Project.showDialog(message: String) {
    dialogFactory.showDialog(this@showDialog, message("backup.app.action.error.title"), message)
  }
}
