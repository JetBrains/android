/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.profilers.perfetto.traceprocessor

import com.android.tools.idea.transport.DeployableFile
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * This is responsible to manage the lifetime of an instance of the TraceProcessorDaemon,
 * spawning a new one if necessary and properly shutting it down at the end of Studio execution.
 */
class TraceProcessorDaemonManager: Disposable {
  // All access paths to process should be synchronized.
  private var process: Process? = null

  private companion object {
    private val LOGGER = Logger.getInstance(TraceProcessorDaemonManager::class.java)

    private val TPD_DEV_PATH: String by lazy {
      when {
        SystemInfo.isWindows -> {
          LOGGER.warn("TPD Backend not supported on Windows.")
          "prebuilts/tools/common/trace-processor-daemon/windows"
        }
        SystemInfo.isMac -> {
          "prebuilts/tools/common/trace-processor-daemon/darwin"
        }
        SystemInfo.isLinux -> {
          "prebuilts/tools/common/trace-processor-daemon/linux"
        }
        else -> {
          LOGGER.warn("Unsupported platform for TPD. Using linux binary.")
          "prebuilts/tools/common/trace-processor-daemon/linux"
        }
      }
    }
    private val TPD_RELEASE_PATH = "plugins/android/resources/trace_processor_daemon"

    private val TPD_BINARY = DeployableFile.Builder("trace_processor_daemon")
      .setReleaseDir(TPD_RELEASE_PATH)
      .setDevDir(DeployableFile.getDevDir(TPD_DEV_PATH))
      .setExecutable(true)
      .build()

    private fun getExecutablePath(): String {
      return File(TPD_BINARY.dir, TPD_BINARY.fileName).absolutePath
    }
  }

  @VisibleForTesting
  fun processIsRunning(): Boolean {
    return process?.isAlive ?: false
  }

  @Synchronized
  fun makeSureDaemonIsRunning() {
    // Spawn a new one if either we don't have one running already or if the current one is not alive anymore.
    if (!processIsRunning()) {
      process = ProcessBuilder(getExecutablePath()).start()
    }
  }

  @Synchronized
  override fun dispose() {
    // We waitFor after destroying the process in order to not leave a Zombie process in the system.
    process?.destroyForcibly()?.waitFor()
  }
}