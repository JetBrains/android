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

import com.android.adblib.serialNumber
import com.android.adblib.tools.debugging.JdwpProcessInfo
import com.android.adblib.tools.debugging.getOrDefault
import com.android.adblib.tools.debugging.getOrNull
import com.android.ddmlib.ClientData

/**
 * Snapshot of data related to a device process.
 */
data class ProcessInfo(
  /**
   * A unique identifier of a device the process belongs to. The identifier is unique as long as
   * the device is connected, i.e. an identifier value can be re-used if a device
   * is disconnected and another device becomes connected.
   */
  val deviceSerialNumber: String,

  /**
   * The process ID on the device
   */
  val pid: Int,

  /**
   * The application ID
   */
  val packageName: String? = null,

  /**
   * The name of this process.
   */
  val processName: String? = null,

  /**
   * The user ID for this process, or `null` if this property is not supported (i.e. older APIs).
   */
  val userId: Int? = null,

  val vmIdentifier: String? = null,

  val abi: String? = null,

  val debuggerStatus: ClientData.DebuggerStatus = ClientData.DebuggerStatus.DEFAULT
)

internal fun JdwpProcessInfo.toProcessInfo() =
  ProcessInfo(
    deviceSerialNumber = device.serialNumber,
    pid = properties.pid,
    // JdwpProcess.packageName is only supported for R+, we need to default to processName for < R.
    packageName = properties.packageName.getOrNull() ?: properties.processName.getOrNull(),
    processName = properties.processName.getOrNull(),
    userId = properties.userId.getOrNull(),
    vmIdentifier = properties.vmIdentifier.getOrNull(),
    abi = properties.instructionSetDescription.getOrNull(),
    debuggerStatus = toDebuggerStatus()
  )

private fun JdwpProcessInfo.toDebuggerStatus(): ClientData.DebuggerStatus =  when  {
  properties.isWaitingForDebugger.getOrDefault(false) -> ClientData.DebuggerStatus.WAITING
  proxyStatus.isExternalDebuggerAttached -> ClientData.DebuggerStatus.ATTACHED
  properties.exception != null -> ClientData.DebuggerStatus.ERROR
  else -> ClientData.DebuggerStatus.DEFAULT
}

/**
 * Return `true` if the only valid field is [ProcessInfo.pid], everything else is unknown about the process.
 */
val ProcessInfo.isPidOnly: Boolean
  get() = processName == null


val ProcessInfo.safeProcessName: String
  get() = processName ?: "<unknown-$pid>"