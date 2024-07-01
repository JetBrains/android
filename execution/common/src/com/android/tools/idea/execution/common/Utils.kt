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
package com.android.tools.idea.execution.common

import com.android.backup.BackupProgressListener
import com.android.backup.BackupResult.Error
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.tools.idea.backup.BackupManager
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.execution.common.stats.track
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.impl.isOfSameType
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.tools.projectWizard.core.asPath


fun RunnerAndConfigurationSettings.getProcessHandlersForDevices(project: Project, devices: List<IDevice>): List<ProcessHandler> {
  return ExecutionManagerImpl.getInstance(project)
    .getRunningDescriptors { it.isOfSameType(this) }
    .mapNotNull { it.processHandler }
    .filter { AndroidSessionInfo.from(it)?.devices?.intersect(devices.toSet())?.isNotEmpty() == true }
}


/**
 * Clears app storage data.
 *
 * If the app is installed on the device, executes `pm clear <package>`.
 */
fun clearAppStorage(project: Project, device: IDevice, packageName: String, stats: RunStats) {
  stats.track("CLEAR_APP_STORAGE_TASK") {
    val packageList = device.shellToString("pm list packages $packageName")
    if (packageList.contains("^package:${packageName.replace(".", "\\.")}$".toRegex())) {
      val result = device.shellToString("pm clear $packageName").trim()
      if (result != "Success") {
        val message = "Failed to clear app storage for $packageName on device ${device.name}"
        RunConfigurationNotifier.notifyWarning(project, "", message)
      }
    }
  }
}

suspend fun restoreAppFromFile(project: Project, device: IDevice, backupFile: String, stats: RunStats) {
  stats.track("RESTORE_APP") {
    val backupManager = BackupManager.getInstance(project)
    val result = backupManager.restore(device.serialNumber, backupFile.asPath(), notify = false)
    if (result is Error) {
      val message = "Failed to restore app from backup on device ${device.name}\n${result.throwable.message}"
      throw ExecutionException(message, result.throwable)
    }
  }
}


private fun IDevice.shellToString(command: String): String {
  val receiver = CollectingOutputReceiver()
  executeShellCommand(command, receiver)
  return receiver.output
}
