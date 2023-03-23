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

import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.device.explorer.monitor.adbimpl.AdbDevice
import com.android.tools.idea.execution.common.debug.AndroidDebugger
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.intellij.execution.RunManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.android.actions.AndroidConnectDebuggerAction

@UiThread
class DeviceProcessService @NonInjectable constructor(private val connectDebuggerAction: (debugger: AndroidDebugger<AndroidDebuggerState>, client: Client, config: AndroidRunConfigurationBase) -> Unit) {

  @Suppress("unused")
  constructor(project: Project) : this({ debugger, client, config ->
    AppExecutorUtil.getAppExecutorService().execute {
      AndroidConnectDebuggerAction.closeOldSessionAndRun(project, debugger, client, config)
    }
  })
  /**
   * The [CoroutineDispatcher] used for asynchronous work that **cannot** happen on the EDT thread.
   */
  private val workerThreadDispatcher: CoroutineDispatcher = AndroidDispatchers.workerThread

  suspend fun fetchProcessList(device: AdbDevice): List<ProcessInfo> {
    ApplicationManager.getApplication().assertIsDispatchThread()

    // Run this in a worker thread in case the device/adb is not responsive
    val clients = device.device.clients ?: emptyArray()
    return withContext(workerThreadDispatcher) {
      clients
        .asSequence()
        .mapNotNull { client -> createProcessInfo(device, client) }
        .toList()
    }
  }

  @WorkerThread
  private fun createProcessInfo(device: Device, client: Client): ProcessInfo? {
    try {
      val processName = client.clientData.clientDescription
      return if (processName == null) {
        thisLogger().debug(
          "Process ${client.clientData.pid} was skipped because the process name is not initialized. Is another instance of Studio running?")
        ProcessInfo(device,
                    pid = client.clientData.pid)
      }
      else {
        val userId = if (client.clientData.userId == -1) null else client.clientData.userId
        ProcessInfo(device,
                    pid = client.clientData.pid,
                    processName = processName,
                    userId = userId,
                    vmIdentifier = client.clientData.vmIdentifier,
                    abi = client.clientData.abi,
                    debuggerStatus = client.clientData.debuggerConnectionStatus,
                    killAction = { client.kill() } )
      }
    }
    catch (e: Throwable) {
      thisLogger().warn("Error retrieving process info from `Client`", e)
      return null
    }
  }

  /**
   * Kills the [process] on the [device][ProcessInfo.device]
   */
  suspend fun killProcess(process: ProcessInfo, device: IDevice) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    if (process.device.serialNumber == device.serialNumber) {
      // Run this in a worker thread in case the device/adb is not responsive
      withContext(workerThreadDispatcher) {
        device.kill(process.processName)
        process.killAction?.invoke()
      }
    }

  }

  /**
   * Force stops the [process] on the [device][ProcessInfo.device]
   */
  suspend fun forceStopProcess(process: ProcessInfo, device: IDevice) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    if (process.device.serialNumber == device.serialNumber) {
      // Run this in a worker thread in case the device/adb is not responsive
      withContext(workerThreadDispatcher) {
        device.forceStop(process.processName)
      }
    }
  }

  suspend fun debugProcess(project: Project, process: ProcessInfo, device: IDevice) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    if (process.device.serialNumber == device.serialNumber) {
      withContext(workerThreadDispatcher) {
        val client = device.getClient(process.safeProcessName)
        val config = RunManager.getInstance(project).selectedConfiguration?.configuration as? AndroidRunConfigurationBase
        val debugger = config?.androidDebuggerContext?.androidDebugger

        if (client != null && config != null && debugger != null) {
          connectDebuggerAction.invoke(debugger, client, config)
        }
      }
    }
  }

  companion object {
    fun getInstance(project: Project): DeviceProcessService = project.service()
  }
}