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
import java.nio.file.Path

/** Keeps track of running emulator processes. */
@Service
class RunningAvdTracker {

  /** A flow of all currently running emulator processes keyed by AVD data folders. */
  val runningAvdsFlow: StateFlow<Map<Path, RunningAvd>>
    get() = mutableRunningAvdsFlow
  /** A snapshot of all currently running emulator processes keyed by AVD data folders. */
  var runningAvds: Map<Path, RunningAvd>
    get() = mutableRunningAvdsFlow.value
    private set(value) {
      mutableRunningAvdsFlow.value = value
    }

  private val mutableRunningAvdsFlow: MutableStateFlow<Map<Path, RunningAvd>> = MutableStateFlow(mapOf())
  private val lock = Any()

  /** Called when an emulator starts. */
  fun started(avdDataFolder: Path, processHandle: ProcessHandle, runType: RunType, isLaunchedByThisProcess: Boolean? = null) {
    synchronized(lock) {
      if (isLaunchedByThisProcess == null) {
        if (!runningAvds.containsKey(avdDataFolder)) {
          runningAvds = runningAvds.plus(avdDataFolder to RunningAvd(avdDataFolder, processHandle, runType, isLaunchedByThisProcess = false))
          removeOnExit(avdDataFolder, processHandle)
        }
      }
      else {
        runningAvds = runningAvds.plus(avdDataFolder to RunningAvd(avdDataFolder, processHandle, runType, isLaunchedByThisProcess))
        removeOnExit(avdDataFolder, processHandle)
      }
    }
  }

  /** Called when an emulator starts shutting down. */
  fun shuttingDown(avdDataFolder: Path) {
    synchronized(lock) {
      val runningAvd = runningAvds[avdDataFolder]
      if (runningAvd != null && !runningAvd.isShuttingDown) {
        runningAvds = runningAvds.plus(avdDataFolder to runningAvd.shuttingDown())
      }
    }
  }

  private fun removeOnExit(avdDataFolder: Path, processHandle: ProcessHandle) {
    processHandle.onExit().thenRun {
      synchronized(lock) {
        if (runningAvds[avdDataFolder]?.processHandle == processHandle) {
          runningAvds = runningAvds.filter { it.key != avdDataFolder }
        }
      }
    }
  }
}

/** Represents a running AVD. */
data class RunningAvd(
  val avdDataFolder: Path,
  val processHandle: ProcessHandle,
  val runType: RunType,
  val isLaunchedByThisProcess: Boolean,
  val isShuttingDown: Boolean = false,
) {

  /** Returns a copy of this [RunningAvd] with [isShuttingDown] set to true. */
  fun shuttingDown() = copy(isShuttingDown = true)

  enum class RunType { EMBEDDED, STANDALONE }
}