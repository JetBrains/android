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
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.service.LogcatService
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryLogcatData
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryLogcatEnded
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryLogcatEnded.Status
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryLogcatEnded.Status.FAILURE
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryLogcatEnded.Status.STATUS_UNSPECIFIED
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryLogcatEnded.Status.SUCCESS
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryLogcatStarted
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryLogcatStatus
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.BlockingDeque
import java.util.concurrent.TimeUnit

/**
 * Handles LeakCanary logcat tracking commands, capturing and processing LeakCanary logs from a connected Android device.
 */
class LeakCanaryLogcatCommandHandler(
  private val device: IDevice,
  private val transportStub: TransportServiceGrpc.TransportServiceBlockingStub,
  private val eventQueue: BlockingDeque<Common.Event>
) : TransportProxy.ProxyCommandHandler {

  private var logCollectionJob: Job? = null
  private var pid = 0
  private var sessionId = 0L
  private val logcatService: LogcatService = LogcatService.getInstance(ProjectManager.getInstance().defaultProject)
  private val startPatternSuccess: String = "HEAP ANALYSIS RESULT"
  private val startPatternFailure: String = "HEAP ANALYSIS FAILED"
  private val separatingLine: String = "===================================="
  private val metaSectionPattern: String = "METADATA"
  private val logger: Logger = Logger.getInstance(LeakCanaryLogcatCommandHandler::class.java)
  private var startTimeNs: Long = 0
  private var isEnded = false
  private var prevLeakCanaryLogTimeStamp = 0L
  private var inLastFrame = false
  private var capturedLogsForIncompleteTrace = StringBuilder()
  private val leakStartPattern: String = "bytes retained by leaking objects"
  private val lastFramePattern: String = "╰→"
  private val initialTabSpace = "  "
  private val TWO_SECONDS = TimeUnit.SECONDS.toSeconds(2)

  companion object {
    private const val LEAKCANARY_TAG = "LeakCanary"
  }

  override fun shouldHandle(command: Commands.Command): Boolean {
    return command.type == Commands.Command.CommandType.START_LOGCAT_TRACKING ||
           command.type == Commands.Command.CommandType.STOP_LOGCAT_TRACKING
  }

  /**
   * Starts listening and detecting LeakCanary logs from Logcat and sends a started status info event.
   */
  private fun startTrace(command: Commands.Command) {
    startTimeNs = getCurrentTimestampInNs()
    pid = command.pid
    sessionId = command.sessionId
    readLeakLog()
    sendLeakCanaryLogcatInfoEvent(timestampNs = startTimeNs, isStarted = true)
  }

  /**
   * Stops listening and detecting LeakCanary logs from Logcat and sends a Logcat info event and a session ended event effectively
   * terminating the task.
   */
  private fun stopTrace(command: Commands.Command) {
    val endTime = getCurrentTimestampInNs()
    isEnded = true
    logCollectionJob?.cancel()
    sendLeakCanaryLogcatInfoEvent(timestampNs = endTime, isStarted = false, stopStatus = SUCCESS)
    addSessionEndedEvent(eventQueue, endTime, pid, command.sessionId)
  }

  private fun getCurrentTimestampInNs(): Long {
    return transportStub.getCurrentTime(Transport.TimeRequest.getDefaultInstance()).timestampNs
  }

  /**
   * Sends a LeakCanary logcat info event to the event queue, indicating the start or stop of tracking for a session.
   * This helps ensure that a LeakCanary task was started/stopped regardless of leaks will be detected or not.
   *
   * @param timestampNs The timestamp (in nanoseconds) associated with the event.
   * @param isStarted A boolean flag indicating whether this is a start event (true) or a stop event (false).
   * @param stopStatus The status of the LeakCanary logcat tracking stop event (relevant only if `startEvent` is false).
   */
  private fun sendLeakCanaryLogcatInfoEvent(timestampNs: Long, isStarted: Boolean,
                                            stopStatus: Status = STATUS_UNSPECIFIED) {
    val infoEvent: LeakCanaryLogcatStatus = if (isStarted) {
      // Start event
      LeakCanaryLogcatStatus.newBuilder()
        .setLogcatStarted(LeakCanaryLogcatStarted.newBuilder()
                            .setTimestamp(timestampNs)
                            .build())
        .build()
    }
    else {
      // Stop event
      LeakCanaryLogcatStatus.newBuilder()
        .setLogcatEnded(LeakCanaryLogcatEnded.newBuilder()
                          .setStartTimestamp(startTimeNs)
                          .setEndTimestamp(timestampNs)
                          .setStatus(stopStatus)
                          .build())
        .build()
    }

    eventQueue.offer(Common.Event.newBuilder()
                       .setGroupId(pid.toLong())
                       .setPid(pid)
                       .setIsEnded(!isStarted)
                       .setKind(Common.Event.Kind.LEAKCANARY_LOGCAT_STATUS)
                       .setLeakCanaryLogcatStatus(infoEvent)
                       .setTimestamp(timestampNs)
                       .build())
  }


  /**
   * Sends a LeakCanary log message event to the event queue.
   *
   * @param logcatMessage The LeakCanary log message to be sent.
   */
  private fun sendLeakCanaryLogcatEvent(logcatMessage: String) {
    val leakCanaryEvent = LeakCanaryLogcatData.newBuilder().setLogcatMessage(logcatMessage).build()
    eventQueue.offer(Common.Event.newBuilder()
                       .setGroupId(pid.toLong())
                       .setPid(pid)
                       .setKind(Common.Event.Kind.LEAKCANARY_LOGCAT)
                       .setLeakcanaryLogcat(leakCanaryEvent)
                       .setTimestamp(getCurrentTimestampInNs())
                       .build()
    )
  }

  override fun execute(command: Commands.Command): Transport.ExecuteResponse {
    when (command.type) {
      Commands.Command.CommandType.START_LOGCAT_TRACKING -> startTrace(command)
      Commands.Command.CommandType.STOP_LOGCAT_TRACKING -> stopTrace(command)
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
          sdk = device.version.androidApiLevel,
          newMessagesOnly = true
        ).collect { logcatMessages ->
          if (logCollectionJob?.isCancelled == true) return@collect

          logcatMessages.forEach { logcatMessage ->
            detectAndHandlePartialLeakTraces(logcatMessage)
            if (LEAKCANARY_TAG != logcatMessage.header.tag) return@forEach

            // The following logic reads LeakCanary's logs line by line, but LeakCanary may print multiple lines as one logcat entry
            // (with one header). Therefore, we need to break the message into lines before processing.
            logcatMessage.message.split("\n").forEach { line ->
              if (startPatternSuccess in line || startPatternFailure in line) {
                capturing = true
                capturedLogs.clear()
                // Add === since it's skipped before the startPattern check
                capturedLogs.appendLine(separatingLine)
              }
              if (capturing) {
                capturedLogs.appendLine(line)
              }
              if (capturing && metaSectionPattern in line) {
                isInMetaSection = true
              }
              if (isInMetaSection && separatingLine == line) {
                capturing = false
                isInMetaSection = false
                sendLeakCanaryLogcatEvent(capturedLogs.toString())
                capturedLogs.clear()
              }
            }
          }
        }
      }
      catch (e: Exception) {
        // Exception that can occur when isEnded = true is not taken into account because we stop listening and session is ended.
        if (!isEnded) {
          // Send a failed status and end session when there is error reading logcat.
          logger.error("Error reading logcat: ${e.message}", e)
          isEnded = true
          logCollectionJob?.cancel()
          val currentTimeNs = getCurrentTimestampInNs()
          sendLeakCanaryLogcatInfoEvent(timestampNs = currentTimeNs, isStarted = false, stopStatus = FAILURE)
          addSessionEndedEvent(eventQueue, currentTimeNs, pid, sessionId)
        }
      }
    }
  }

  private fun convertIncompleteToCompleteTrace(leaktrace: StringBuilder): String {
    return """====================================
HEAP ANALYSIS RESULT
====================================
1 APPLICATION LEAKS

References underlined with "~~~" are likely causes.
Learn more at https://squ.re/leaks.

$leaktrace
====================================
0 LIBRARY LEAKS

A Library Leak is a leak caused by a known bug in 3rd party code that you do not have control over.
See https://square.github.io/leakcanary/fundamentals-how-leakcanary-works/#4-categorizing-leaks
====================================
0 UNREACHABLE OBJECTS

An unreachable object is still in memory but LeakCanary could not find a strong reference path
from GC roots.
====================================
METADATA

Please include this in bug reports and Stack Overflow questions.
Analysis duration: -1 ms
Heap dump file path: -
Heap dump timestamp: 0
Heap dump duration: Unknown
====================================""".trimIndent()
  }

  private fun detectAndHandlePartialLeakTraces(logcatMessage: LogcatMessage) {
    if (logcatMessage.header.tag == LEAKCANARY_TAG) {
      prevLeakCanaryLogTimeStamp = logcatMessage.header.timestamp.epochSecond
      logcatMessage.message.split("\n").forEach { line ->
        if (inLastFrame && initialTabSpace !in line) {
          sendLeakCanaryLogcatEvent(convertIncompleteToCompleteTrace(capturedLogsForIncompleteTrace))
          capturedLogsForIncompleteTrace.clear()
          inLastFrame = false
        }
        if (capturedLogsForIncompleteTrace.isNotEmpty()) {
          capturedLogsForIncompleteTrace.appendLine(line)
          if (lastFramePattern in line) {
            inLastFrame = true
          }
        }
        if (leakStartPattern in line) {
          capturedLogsForIncompleteTrace.clear()
          inLastFrame = false
          capturedLogsForIncompleteTrace.appendLine(line)
        }
      }
    }
    else {
      if (inLastFrame && logcatMessage.header.timestamp.epochSecond - prevLeakCanaryLogTimeStamp >= TWO_SECONDS) {
        sendLeakCanaryLogcatEvent(convertIncompleteToCompleteTrace(capturedLogsForIncompleteTrace))
        capturedLogsForIncompleteTrace.clear()
        inLastFrame = false
      }
    }
  }
}