/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.monitor.processes

import com.android.adblib.ConnectedDevice
import com.android.adblib.activityManager
import com.android.adblib.ddmlibcompatibility.debugging.associatedIDevice
import com.android.adblib.serialNumber
import com.android.adblib.tools.debugging.appProcessTracker
import com.android.adblib.tools.debugging.isTrackAppSupported
import com.android.adblib.tools.debugging.jdwpProcessTracker
import com.android.adblib.tools.debugging.sendDdmsExit
import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.Client
import com.android.tools.idea.backup.BackupManager
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.execution.common.debug.AndroidDebugger
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState
import com.android.tools.idea.execution.common.debug.RunConfigurationWithDebugger
import com.android.tools.idea.execution.common.debug.utils.AndroidConnectDebugger
import com.intellij.execution.RunManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path

@UiThread
@Service(Service.Level.PROJECT)
class DeviceProcessService @NonInjectable constructor(private val connectDebuggerAction: (debugger: AndroidDebugger<AndroidDebuggerState>, client: Client, config: RunConfigurationWithDebugger) -> Unit) {

  @Suppress("unused")
  constructor(project: Project) : this({ debugger, client, config ->
   /* AppExecutorUtil.getAppExecutorService().execute {
      AndroidConnectDebugger.closeOldSessionAndRun(project, debugger, client, config)
    }*/
  })
  /**
   * The [CoroutineDispatcher] used for asynchronous work that **cannot** happen on the EDT thread.
   */
  private val workerThreadDispatcher: CoroutineDispatcher = AndroidDispatchers.workerThread
  private val uiThreadDispatcher: CoroutineDispatcher = AndroidDispatchers.uiThread

  /**
   * Kills the [process] on the [ConnectedDevice]
   */
  suspend fun killProcess(process: ProcessInfo, device: ConnectedDevice) {
    if (process.deviceSerialNumber == device.serialNumber) {
      // Run this in a worker thread in case the device/adb is not responsive
      withContext(workerThreadDispatcher) {
        try {
          val processes =
            when (device.isTrackAppSupported()) {
              true -> device.appProcessTracker.appProcessFlow.value.mapNotNull { it.jdwpProcess }
              false -> device.jdwpProcessTracker.processesFlow.value
            }
          processes.find { it.pid == process.pid }?.sendDdmsExit(1)
        } catch (e: IOException) {
          thisLogger().warn("`killProcess` failed for pid ${process.pid}", e)
        }
      }
    }
  }

  /**
   * Force stops the [process] on the [ConnectedDevice]
   */
  suspend fun forceStopProcess(process: ProcessInfo, device: ConnectedDevice) {
    if (process.deviceSerialNumber == device.serialNumber) {
      // Run this in a worker thread in case the device/adb is not responsive
      withContext(workerThreadDispatcher) {
        if (process.packageName != null) {
          try {
            device.activityManager.forceStop(process.packageName)
          }
          catch (e: IOException) {
            thisLogger().warn("`forceStop` failed for packageName ${process.packageName}", e)
          }
        } else {
          thisLogger().debug("Force stop invoked on a null package name")
          withContext(uiThreadDispatcher) {
            reportError("force stop", "Couldn't find package name for process.")
          }
        }
      }
    }
  }

  suspend fun debugProcess(project: Project, process: ProcessInfo, device: ConnectedDevice) {
    ThreadingAssertions.assertEventDispatchThread()

    if (process.deviceSerialNumber == device.serialNumber) {
      withContext(workerThreadDispatcher) {
        val iDevice = device.associatedIDevice()
        val client = iDevice?.getClient(process.processName)
        val config = RunManager.getInstance(project).selectedConfiguration?.configuration as? RunConfigurationWithDebugger
        val debugger = config?.androidDebuggerContext?.androidDebugger

        if (client != null && config != null && debugger != null) {
          connectDebuggerAction.invoke(debugger, client, config)
        } else {
          thisLogger().debug("Attach Debugger invoke on a null device, client, config, or debugger")
          withContext(uiThreadDispatcher) {
            reportError("attach debugger", "Couldn't find process to attach or debugger to use.")
          }
        }
      }
    }
  }

  @UiThread
  suspend fun backupApplication(
    project: Project,
    process: ProcessInfo,
    device: ConnectedDevice,
  ) {
    if (process.deviceSerialNumber == device.serialNumber) {
      val packageName = process.packageName
      if (packageName == null) {
        thisLogger().debug("Backup Application invoked without application id")
        withContext(uiThreadDispatcher) {
          reportError("backup application", "Couldn't find application id.")
        }
        return
      }
      val backupManager = BackupManager.getInstance(project)
      backupManager.showBackupDialog(device.serialNumber, packageName, BackupManager.Source.DEVICE_EXPLORER)
    }
  }

  @UiThread
  fun restoreApplication(project: Project, device: ConnectedDevice, path: Path) {
    val backupManager = BackupManager.getInstance(project)
    backupManager.restoreModal(device.serialNumber, path, BackupManager.Source.DEVICE_EXPLORER)
  }

  private fun reportError(title: String, messageToReport: String) {
    val notification = Notification("Device Explorer", "Unable to $title", messageToReport, NotificationType.WARNING)
    ApplicationManager.getApplication().invokeLater {
      Notifications.Bus.notify(notification)
    }
  }

  companion object {
    fun getInstance(project: Project): DeviceProcessService = project.service()
  }
}