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
package com.android.tools.idea.run.debug

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.tools.idea.run.DeploymentApplicationService
import com.google.common.util.concurrent.Uninterruptibles
import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Returns client with appId in [appIds] and DebuggerStatus.WAITING, otherwise throws [ExecutionException].
 */
@WorkerThread
fun waitForClientReadyForDebug(device: IDevice, appIds: Collection<String>, pollTimeoutSeconds: Int = 15): Client {
  val timeUnit = TimeUnit.SECONDS

  for (i in 0 until pollTimeoutSeconds) {
    ProgressManager.checkCanceled()
    if (!device.isOnline) {
      throw ExecutionException("Device is offline")
    }
    for (appId in appIds) {
      val clients = DeploymentApplicationService.getInstance().findClient(device, appId)
      if (clients.isNotEmpty()) {
        Logger.getInstance("waitForClient").info("Connecting to $appId")
        if (clients.size > 1) {
          Logger.getInstance("waitForClient").info("Multiple clients with same application ID: $appId")
        }
        val client = clients[0]
        when (client.clientData.debuggerConnectionStatus) {
          ClientData.DebuggerStatus.WAITING -> {
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
          ClientData.DebuggerStatus.DEFAULT -> {
            continue
          }
        }

      }
      Uninterruptibles.sleepUninterruptibly(1, timeUnit)
    }
  }
  throw ExecutionException("Processes ${appIds.joinToString()} are not found. Aborting session.")
}