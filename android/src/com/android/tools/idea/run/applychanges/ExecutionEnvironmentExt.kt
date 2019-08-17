/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.applychanges

import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.AndroidSessionInfo
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.util.SwapInfo
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment

/**
 * Finds the existing process handler that can be reused for apply-changes execution.
 */
fun ExecutionEnvironment.findExistingProcessHandler(devices: DeviceFutures): ProcessHandler? {
  // 1) We have an existing session to reuse.
  val existingSessionInfo = AndroidSessionInfo.findOldSession(project, null, runProfile as AndroidRunConfigurationBase, executionTarget)
  if (existingSessionInfo != null) {
    return existingSessionInfo.processHandler
  }

  // 2) We're swapping to an existing regular Android Run/Debug tab, then use what was provided.
  val swapInfo = getUserData(SwapInfo.SWAP_INFO_KEY)
  if (swapInfo != null) {
    return swapInfo.handler
  }

  // 3) Look for an existing remote debugger.
  val liveDevices = devices.ifReady ?: return null
  val debuggerPortsInUse = liveDevices.asSequence()
    .flatMap { device -> device.clients.asSequence() }
    .map { client -> client.debuggerListenPort.toString() }
    .toSet()

  // Find a Client that uses the same port as the debugging session.
  return DebuggerManagerEx.getInstanceEx(project).sessions.asSequence()
    .find { session -> debuggerPortsInUse.contains(session.process.connection.address.trim()) }
    ?.process?.processHandler
}
