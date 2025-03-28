/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.avdmanager

import com.android.tools.idea.avdmanager.RunningAvd.RunType
import com.intellij.openapi.components.Service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Keeps track of running emulator processes. */
@Service
class RunningAvdTracker {

  /** A flow of all currently running emulator processes keyed by [AVD IDs][com.android.sdklib.internal.avd.AvdInfo.id]. */
  val runningAvdsFlow: StateFlow<Map<String, RunningAvd>>
    get() = mutableRunningAvdsFlow
  /** A snapshot of all currently running emulator processes keyed by [AVD IDs][com.android.sdklib.internal.avd.AvdInfo.id]. */
  var runningAvds: Map<String, RunningAvd>
    get() = mutableRunningAvdsFlow.value
    private set(value) {
      mutableRunningAvdsFlow.value = value
    }

  private val mutableRunningAvdsFlow: MutableStateFlow<Map<String, RunningAvd>> = MutableStateFlow(mapOf())
  private val lock = Any()

  /**
   * Called when an emulator starts. The [avdId] parameter has the same semantics as
   * [AvdInfo.id][com.android.sdklib.internal.avd.AvdInfo.id].
   */
  fun started(avdId: String, processHandle: ProcessHandle, runType: RunType, isLaunchedByThisProcess: Boolean? = null) {
    synchronized(lock) {
      if (isLaunchedByThisProcess == null) {
        if (!runningAvds.containsKey(avdId)) {
          runningAvds = runningAvds.plus(avdId to RunningAvd(avdId, processHandle, runType, isLaunchedByThisProcess = false))
          removeOnExit(avdId, processHandle)
        }
      }
      else {
        runningAvds = runningAvds.plus(avdId to RunningAvd(avdId, processHandle, runType, isLaunchedByThisProcess))
        removeOnExit(avdId, processHandle)
      }
    }
  }

  /**
   * Called when an emulator starts shutting down. The [avdId] parameter has the same semantics as
   * [AvdInfo.id][com.android.sdklib.internal.avd.AvdInfo.id].
   */
  fun shuttingDown(avdId: String) {
    synchronized(lock) {
      val runningAvd = runningAvds[avdId]
      if (runningAvd != null && !runningAvd.isShuttingDown) {
        runningAvds = runningAvds.plus(avdId to runningAvd.shuttingDown())
      }
    }
  }

  private fun removeOnExit(avdId: String, processHandle: ProcessHandle) {
    processHandle.onExit().thenRun {
      synchronized(lock) {
        if (runningAvds[avdId]?.processHandle == processHandle) {
          runningAvds = runningAvds.minus(avdId)
        }
      }
    }
  }
}

/** The [avdId] property has the same semantics as [AvdInfo.id][com.android.sdklib.internal.avd.AvdInfo.id]. */
data class RunningAvd(
  val avdId: String,
  val processHandle: ProcessHandle,
  val runType: RunType,
  val isLaunchedByThisProcess: Boolean,
  val isShuttingDown: Boolean = false,
) {

  /** Returns a copy of this [RunningAvd] with [isShuttingDown] set to true. */
  fun shuttingDown() = copy(isShuttingDown = true)

  enum class RunType { EMBEDDED, STANDALONE }
}