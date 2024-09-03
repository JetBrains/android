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
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Backups the state of the foreground app to a file */
internal class BackupForegroundAppAction : AnAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = StudioFlags.BACKUP_ENABLED.get()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val backupManager = BackupManager.getInstance(project)
    val serialNumber = getDeviceSerialNumber(e)
    val deviceProvisioner = project.service<DeviceProvisionerService>().deviceProvisioner
    deviceProvisioner.scope.launch {
      val handle = deviceProvisioner.findConnectedDeviceHandle(DeviceSelector.fromSerialNumber(serialNumber))
      handle?.scope?.launch {
        val applicationId = backupManager.getForegroundApplicationId(serialNumber)
        withContext(uiThread) {
          val backupFile = backupManager.chooseBackupFile(applicationId) ?: return@withContext
          backupManager.backupModal(
            serialNumber,
            applicationId,
            backupFile,
            BackupManager.Source.RUN_MENU,
          )
        }
      }
    }
  }

  private fun getDeviceSerialNumber(e: AnActionEvent): String {
    return SERIAL_NUMBER_KEY.getData(e.dataContext)
        ?: throw RuntimeException("SERIAL_NUMBER_KEY not found in ActionEvent")
  }
}
