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

import com.android.ddmlib.ClientData

/**
 * Snapshot of data related to a process of a [Device].
 */
data class ProcessInfo(
  /**
   * The [Device] this entry belongs to.
   */
  val device: Device,

  /**
   * The process ID on the device
   */
  val pid: Int,

  /**
   * The name of this entry in its parent directory.
   */
  val processName: String? = null,

  /**
   * The user ID for this process, or `null` if this property is not supported (i.e. older APIs).
   */
  val userId: Int? = null,

  val vmIdentifier: String? = null,

  val abi: String? = null,

  val debuggerStatus: ClientData.DebuggerStatus = ClientData.DebuggerStatus.DEFAULT,

  val supportsNativeDebugging: Boolean = false,
)

/**
 * Return `true` if the only valid field is [ProcessInfo.pid], everything else is unknown about the process.
 */
val ProcessInfo.isPidOnly: Boolean
  get() = processName == null


val ProcessInfo.safeProcessName: String
  get() = processName ?: "<unknown-$pid>"
