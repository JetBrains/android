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
package com.android.tools.idea.execution.common.debug.utils

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.tools.idea.run.DeploymentApplicationService
import com.google.common.util.concurrent.Uninterruptibles
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.function.Function

const val SECONDS_BETWEEN_LOGGING_WAIT_STATUS: Long = 1

/**
 * Returns client with appId in [appIds] with [waitingProcessState], otherwise throws [ExecutionException].
 */
@WorkerThread
@Throws(ExecutionException::class)
internal fun waitForClientReadyForDebug(device: IDevice,
                                        appIds: Collection<String>,
                                        pollTimeoutSeconds: Long = 15,
                                        indicator: ProgressIndicator,
                                        waitingProcessState: ClientData.DebuggerStatus = ClientData.DebuggerStatus.WAITING): Client {
  indicator.text = "Waiting for processes ${appIds.joinToString()}"
  val lastLogTimes: MutableMap<String, Long> = mutableMapOf()
  val logger = Logger.getInstance("waitForClientReadyForDebug")
  logger.info("Waiting for clients $appIds for $pollTimeoutSeconds seconds")

  val startTimeMillis = System.currentTimeMillis()
  while ((System.currentTimeMillis() - startTimeMillis) <= TimeUnit.SECONDS.toMillis(pollTimeoutSeconds)) {
    indicator.checkCanceled()
    if (!device.isOnline) {
      throw ExecutionException("Device is offline")
    }
    // Multiple ids can be in the case of instrumented test with orchestrator.
    // [TODO] pass only one appId.
    for (appId in appIds) {
      val client = getClientWithAppId(device, appId, lastLogTimes, waitingProcessState)
      if (client != null) {
        logger.info("$appId is now debuggable.")
        return client
      }
    }
    Uninterruptibles.sleepUninterruptibly(20, TimeUnit.MILLISECONDS)
  }
  if (appIds.size > 1) {
    throw ExecutionException("Process IDs ${appIds.joinToString()} were not found. Aborting session.")
  }
  else {
    throw ExecutionException("Process ID ${appIds.joinToString()} was not found. Aborting session.")
  }
}

private fun getClientWithAppId(device: IDevice,
                               appId: String,
                               lastLogTimes: MutableMap<String, Long>,
                               waitingProcessState: ClientData.DebuggerStatus): Client? {
  val clients = DeploymentApplicationService.instance.findClient(device, appId)
  if (clients.isNotEmpty()) {
    val logger = Logger.getInstance("waitForClientReadyForDebug")
    val lastLogTime = lastLogTimes[appId]
    if (lastLogTime == null) {
      logger.info("Found process $appId. Waiting for it to be debuggable.")
      if (clients.size > 1) {
        logger.info("Multiple clients with same application ID: $appId")
      }
      lastLogTimes[appId] = System.currentTimeMillis()
    } else if ((System.currentTimeMillis() - lastLogTime) >= TimeUnit.SECONDS.toMillis(SECONDS_BETWEEN_LOGGING_WAIT_STATUS)) {
      logger.info("Still waiting for process $appId to be debuggable.")

      lastLogTimes[appId] = System.currentTimeMillis()
    }
    // Even though multiple processes may be related to a particular application ID, we'll only connect to the first one
    // in the list since the debugger is set up to only connect to at most one process.
    // TODO b/122613825: improve support for connecting to multiple processes with the same application ID.
    // This requires this task to wait for potentially multiple Clients before returning.
    val client = clients[0]
    when (client.clientData.debuggerConnectionStatus) {
      waitingProcessState -> {
        return client
      }

      ClientData.DebuggerStatus.ERROR -> {
        val message = String.format(Locale.US,
                                    "Debug port (%1\$d) is busy, make sure there is no other active debug connection to the same application",
                                    client.debuggerListenPort)
        throw ExecutionException(message)
      }

      ClientData.DebuggerStatus.ATTACHED -> {
        throw ExecutionException("A debugger is already attached")
      }

      else -> {
        return null
      }
    }
  }
  return null
}

/**
 * Shows [ExecutionException] in Debug Tool Window.
 */
fun showError(project: Project, e: ExecutionException, sessionName: String) {
  ExecutionUtil.handleExecutionError(project, ToolWindowId.DEBUG, e,
                                     ExecutionBundle.message("error.running.configuration.message", sessionName),
                                     e.message, Function.identity(), null)
}
