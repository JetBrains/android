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
package com.android.tools.idea.profilers.commands

import com.android.ddmlib.IDevice
import com.android.tools.idea.logcat.service.LogcatService
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.LeakCanary
import com.android.tools.profiler.proto.Transport
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.BlockingDeque

class LeakCanaryLogcatCommandHandler(
  private val device: IDevice,
  private val eventQueue: BlockingDeque<Common.Event>
) : TransportProxy.ProxyCommandHandler {

  private var logCollectionJob: Job? = null
  private var pid = 0
  private val logcatService: LogcatService = LogcatService.getInstance(ProjectManager.getInstance().defaultProject)
  private val startPatternSuccess: String = "HEAP ANALYSIS RESULT"
  private val startPatternFailure: String = "HEAP ANALYSIS FAILED"
  private val separatingLine: String = "===================================="
  private val metaSectionPattern: String = "METADATA"
  private val logger: Logger = Logger.getInstance(LeakCanaryLogcatCommandHandler::class.java)

  override fun shouldHandle(command: Commands.Command): Boolean {
    return command.type == Commands.Command.CommandType.START_LOGCAT_TRACKING ||
           command.type == Commands.Command.CommandType.STOP_LOGCAT_TRACKING
  }

  private fun sendToQueue(logcatMessage: String) {
    eventQueue.offer(
      Common.Event.newBuilder()
        .setGroupId(pid.toLong())
        .setPid(pid)
        .setKind(Common.Event.Kind.LEAKCANARY_LOGCAT)
        .setLeakcanaryLogcat(LeakCanary.LeakCanaryLogcatData.newBuilder().setLogcatMessage(logcatMessage).build())
        .setTimestamp(System.nanoTime())
        .build()
    )
  }

  /**
   * Starts capturing LeakCanary logs from the device's logcat.
   */
  private fun startTrace(command: Commands.Command) {
    pid = command.pid
    readLeakLog()
  }

  /**
   * Stops capturing LeakCanary logs.
   */
  private fun stopTrace() {
    logCollectionJob?.cancel()
  }

  override fun execute(command: Commands.Command): Transport.ExecuteResponse {
    when (command.type) {
      Commands.Command.CommandType.START_LOGCAT_TRACKING -> startTrace(command)
      Commands.Command.CommandType.STOP_LOGCAT_TRACKING -> stopTrace()
      else -> {}
    }
    return Transport.ExecuteResponse.newBuilder().build()
  }

  /**
   * Identifies and reads leakCanary logs from logcat and sends them to the event queue.
   */
  private fun readLeakLog() {
    logCollectionJob = CoroutineScope(Dispatchers.Default + Job()).launch {
      try {
        val capturedLogs = StringBuilder()
        var capturing = false
        var isInMetaSection = false

        logcatService.readLogcat(
          serialNumber = device.serialNumber,
          sdk = device.version.apiLevel,
          newMessagesOnly = true
        ).collect { logcatMessages ->
          if (logCollectionJob?.isCancelled == true) return@collect

          logcatMessages.forEach { logcatMessage ->
            if ("LeakCanary" != logcatMessage.header.tag) return@forEach

            if (startPatternSuccess in logcatMessage.message || startPatternFailure in logcatMessage.message) {
              capturing = true
              capturedLogs.clear()
              // Add === since it's skipped before the startPattern check
              capturedLogs.appendLine(separatingLine)
            }
            if (capturing) {
              capturedLogs.appendLine(logcatMessage.message)
            }
            if (capturing && metaSectionPattern in logcatMessage.message) {
              isInMetaSection = true
            }
            if (isInMetaSection && separatingLine == logcatMessage.message) {
              capturing = false
              isInMetaSection = false
              sendToQueue(capturedLogs.toString())
              capturedLogs.clear()
            }
          }
        }
      } catch (e: Exception) {
        logger.error("Error reading logcat: ${e.message}", e)
      }
    }
  }
}