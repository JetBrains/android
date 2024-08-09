/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.streaming.device

import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.shellAsLines
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.EOFException
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/**
 * Obtains logcat output from the device and writes messages related to the screen sharing agent
 * to `device_mirroring.logcat`.
 */
internal object AgentLogSaver {

  val logFile: Path
    get() = PathManager.getLogDir().resolve("device_mirroring.logcat")

  private val logger: Logger
    get() = thisLogger()

  @Suppress("BlockingMethodInNonBlockingContext")
  fun saveLog(adbSession: AdbSession, deviceSelector: DeviceSelector) {
    val command = "logcat -d studio.screen.sharing:V app_process:I *:F"
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      var logWriter: Writer? = null
      try {
        adbSession.deviceServices.shellAsLines(deviceSelector, command).collect { output ->
          if (output is ShellCommandOutputElement.StdoutLine) {
            val writer = logWriter ?: Files.newOutputStream(logFile, WRITE, CREATE, TRUNCATE_EXISTING).writer().also { logWriter = it }
            writer.write(output.contents)
            writer.write("\n")
          }
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: EOFException) {
        // Expected.
      }
      catch (e: Throwable) {
        logger.warn("Unable to save logcat output", e)
      }
      finally {
        logWriter?.close()
      }
    }
  }
}