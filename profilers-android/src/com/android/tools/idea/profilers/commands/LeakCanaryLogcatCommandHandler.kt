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
import com.android.tools.leakcanarylib.LeakCanaryParser
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.EndSession
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryAnalysisData
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryAnalysisEnded
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryAnalysisEnded.Status
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryAnalysisEnded.Status.STATUS_UNSPECIFIED
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryAnalysisEnded.Status.SUCCESS
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryAnalysisStarted
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryAnalysisStatus
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import java.security.MessageDigest
import java.util.concurrent.BlockingDeque
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob

/**
 * Handles LeakCanary logcat tracking commands, capturing and processing LeakCanary logs from a connected Android device.
 */
class LeakCanaryLogcatCommandHandler(
  private val device: IDevice,
  private val transportStub: TransportServiceGrpc.TransportServiceBlockingStub,
  private val eventQueue: BlockingDeque<Common.Event>
) : TransportProxy.ProxyCommandHandler {

  private val scopeJob = SupervisorJob()
  private val scope = CoroutineScope(Dispatchers.IO + scopeJob)
  private var logCollectionJob: Job? = null
  private var pid = 0
  private var sessionId = 0L
  private val bytesRetainedText = "bytes retained by leaking objects"
  private val logger: Logger = Logger.getInstance(LeakCanaryLogcatCommandHandler::class.java)
  private var startTimeNs: Long = 0
  private val TWO_SECONDS = TimeUnit.SECONDS.toSeconds(2)
  private var prevLogTimeStampOfPartialTrace = 0L
  private var inLastFrameOfPartialTrace = false
  private var capturedLogsForPartialTrace = StringBuilder()

  private var inMetaSectionOfCompleteTrace = false
  private var isCapturingCompleteTrace = false
  private var capturedLogsForCompleteTrace = StringBuilder()

  companion object {
    private const val LEAKCANARY_TAG = "LeakCanary"
  }

  override fun shouldHandle(command: Commands.Command): Boolean {
    return command.type == Commands.Command.CommandType.START_LOGCAT_TRACKING ||
           command.type == Commands.Command.CommandType.STOP_LOGCAT_TRACKING
  }

  private fun resetTrackingState() {
    logCollectionJob?.cancel()
    logCollectionJob = null
    prevLogTimeStampOfPartialTrace = 0L
    inLastFrameOfPartialTrace = false
    capturedLogsForPartialTrace = StringBuilder()
    inMetaSectionOfCompleteTrace = false
    isCapturingCompleteTrace = false
    capturedLogsForCompleteTrace = StringBuilder()
  }

  /**
   * Starts listening and detecting LeakCanary logs from Logcat and sends a started status info event.
   */
  private fun startTrace(command: Commands.Command) {
    startTimeNs = getCurrentTimestampInNs()
    pid = command.pid
    sessionId = command.sessionId
    resetTrackingState()
    readLeakLog()
    sendLeakCanaryAnalysisInfoEvent(timestampNs = startTimeNs, isStarted = true)
  }

  /**
   * Stops listening and detecting LeakCanary logs from Logcat and sends a Logcat info event and a session ended event effectively
   * terminating the task.
   */
  private fun stopTrace(command: Commands.Command) {
    val endTime = getCurrentTimestampInNs()
    resetTrackingState()
    sendLeakCanaryAnalysisInfoEvent(timestampNs = endTime, isStarted = false, stopStatus = SUCCESS)
    val endSessionCommand = Commands.Command.newBuilder()
      .setStreamId(command.streamId)
      .setPid(pid)
      .setSessionId(command.sessionId)
      .setType(Commands.Command.CommandType.END_SESSION)
      .setEndSession(EndSession.newBuilder().setSessionId(command.sessionId))
      .build()
    transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(endSessionCommand).build())
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
  private fun sendLeakCanaryAnalysisInfoEvent(timestampNs: Long, isStarted: Boolean,
                                              stopStatus: Status = STATUS_UNSPECIFIED) {
    val infoEvent: LeakCanaryAnalysisStatus = if (isStarted) {
      // Start event
      LeakCanaryAnalysisStatus.newBuilder()
        .setAnalysisStarted(LeakCanaryAnalysisStarted.newBuilder()
                              .setTimestamp(timestampNs)
                              .build())
        .build()
    }
    else {
      // Stop event
      LeakCanaryAnalysisStatus.newBuilder()
        .setAnalysisEnded(LeakCanaryAnalysisEnded.newBuilder()
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
                       .setKind(Common.Event.Kind.LEAKCANARY_ANALYSIS_STATUS)
                       .setLeakCanaryAnalysisStatus(infoEvent)
                       .setTimestamp(timestampNs)
                       .build())
  }


  /**
   * Sends a LeakCanary log message event to the event queue.
   *
   * @param analysisData The LeakCanary analysis data to be sent.
   */
  private fun sendLeakCanaryAnalysisEvent(analysisData: String) {
    try {
      val analysis = LeakCanaryParser().parseLogcatMessage(analysisData)
      val leakCanaryEvent = LeakCanaryAnalysisData.newBuilder().setData(analysis.toString()).build()
      eventQueue.offer(Common.Event.newBuilder()
                         .setGroupId(pid.toLong())
                         .setPid(pid)
                         .setKind(Common.Event.Kind.LEAKCANARY_ANALYSIS)
                         .setLeakcanaryAnalysis(leakCanaryEvent)
                         .setTimestamp(getCurrentTimestampInNs())
                         .build()
      )
    }
    catch (e: Exception) {
      logger.info("Failed to parse LeakCanary report. Skipping event.", e)
    }
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
    val logcatService: LogcatService = LogcatService.getInstance(ProjectManager.getInstance().defaultProject)
    logCollectionJob = scope.launch {
      logger.info("Coroutine Started")
      logcatService.readLogcat(
        serialNumber = device.serialNumber,
        sdk = device.version.androidApiLevel,
        maxHistoryEntries = 0,
      ).collect { logcatMessages ->
        logcatMessages.forEach { logcatMessage ->
          // Handlers are called sequentially. If a handler returns true, it means it processed the event
          // and subsequent handlers are skipped for this logcatMessage.
          var handled = detectAndHandleObjectRetainedAndAnalysis(logcatMessage)

          if (!handled) {
            handled = detectAndHandleCompleteLeakTraces(logcatMessage)
          }

          if (!handled) {
            handled = detectAndHandleHostAnalysisTrigger(logcatMessage)
          }

          // Partial traces should only run if the logcat message was not handled by an explicit LeakCanary event (complete trace, trigger, etc.)
          if (!handled) {
            detectAndHandlePartialLeakTraces(logcatMessage)
          }
          // Note: detectAndHandlePartialLeakTraces doesn't return a boolean because it often spans multiple logcat entries.
          // The logic for partial trace completion is handled inside the function itself, including the TWO_SECONDS check
          // against the previous log entry.
        }
      }
    }
  }

  // Returns true if any event was sent, false otherwise.
  private fun detectAndHandleObjectRetainedAndAnalysis(logcatMessage: LogcatMessage): Boolean {
    val retainedObjectsRegex = """Found (\d+) objects retained""".toRegex()
    val analysisProgressRegex = """Analysis in progress, (\d+)% done""".toRegex()
    var handled = false

    if (logcatMessage.header.tag != LEAKCANARY_TAG)
      return false

    logcatMessage.message.split("\n").forEach { line ->
      if (retainedObjectsRegex.containsMatchIn(line) || analysisProgressRegex.containsMatchIn(line)) {
        sendLeakCanaryAnalysisEvent(line)
        handled = true
      }
    }
    return handled
  }

  // Returns true if a complete trace was captured and sent, false otherwise.
  private fun detectAndHandleCompleteLeakTraces(logcatMessage: LogcatMessage): Boolean {
    val startPatternSuccess = "HEAP ANALYSIS RESULT"
    val startPatternFailure = "HEAP ANALYSIS FAILED"
    val separatingLine = "===================================="
    val metaSectionPattern = "METADATA"
    var handled = false

    if (LEAKCANARY_TAG != logcatMessage.header.tag) return false

    // The following logic reads LeakCanary's logs line by line, but LeakCanary may print multiple lines as one logcat entry
    // (with one header). Therefore, we need to break the message into lines before processing.
    logcatMessage.message.split("\n").forEach { line ->
      if (startPatternSuccess in line || startPatternFailure in line) {
        isCapturingCompleteTrace = true
        capturedLogsForCompleteTrace.clear()
        // Add === since it's skipped before the startPattern check
        capturedLogsForCompleteTrace.appendLine(separatingLine)
      }
      if (isCapturingCompleteTrace) {
        capturedLogsForCompleteTrace.appendLine(line)
      }
      if (isCapturingCompleteTrace && metaSectionPattern in line) {
        inMetaSectionOfCompleteTrace = true
      }
      if (inMetaSectionOfCompleteTrace && separatingLine == line) {
        isCapturingCompleteTrace = false
        inMetaSectionOfCompleteTrace = false
        sendLeakCanaryAnalysisEvent(capturedLogsForCompleteTrace.toString())
        capturedLogsForCompleteTrace.clear()
        handled = true

        // Clear the partial trace state to prevent double-handling of the same content
        // by the partial trace handler logic.
        capturedLogsForPartialTrace.clear()
        inLastFrameOfPartialTrace = false
      }
    }
    return handled
  }

  private fun convertPartialToCompleteTrace(leaktrace: StringBuilder): String {
    val isBytesAvailable = leaktrace.contains(bytesRetainedText)
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(leaktrace.toString().toByteArray(Charsets.UTF_8))
    val hashedSignature = hashBytes.joinToString("") { "%02x".format(it) }

    return """====================================
HEAP ANALYSIS RESULT
====================================
1 APPLICATION LEAKS

References underlined with "~~~" are likely causes.
Learn more at https://squ.re/leaks.
${
      if (!isBytesAvailable)
        """
0 bytes retained by leaking objects
Signature: $hashedSignature
┬───"""
      else ""
    }
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
    val gcRootText = "GC Root"
    val lastFramePattern = "╰→"
    val initialTabSpace = "  "

    if (logcatMessage.header.tag == LEAKCANARY_TAG) {
      prevLogTimeStampOfPartialTrace = logcatMessage.header.timestamp.epochSecond
      // The following logic reads LeakCanary's logs line by line, but LeakCanary may print multiple lines as one logcat entry
      // (with one header). Therefore, we need to break the message into lines before processing.
      logcatMessage.message.split("\n").forEach { line ->
        if (inLastFrameOfPartialTrace && initialTabSpace !in line) {
          sendLeakCanaryAnalysisEvent(convertPartialToCompleteTrace(capturedLogsForPartialTrace))
          capturedLogsForPartialTrace.clear()
          inLastFrameOfPartialTrace = false
        }
        if (capturedLogsForPartialTrace.isNotEmpty()) {
          capturedLogsForPartialTrace.appendLine(line)
          if (lastFramePattern in line) {
            inLastFrameOfPartialTrace = true
          }
        }
        if (bytesRetainedText in line || (gcRootText in line && capturedLogsForPartialTrace.isEmpty())) {
          capturedLogsForPartialTrace.clear()
          inLastFrameOfPartialTrace = false
          capturedLogsForPartialTrace.appendLine(line)
        }
      }
    }
    else {
      // Logic for partial trace timeout when a non-LeakCanary log message is received
      if (inLastFrameOfPartialTrace && logcatMessage.header.timestamp.epochSecond - prevLogTimeStampOfPartialTrace >= TWO_SECONDS) {
        sendLeakCanaryAnalysisEvent(convertPartialToCompleteTrace(capturedLogsForPartialTrace))
        capturedLogsForPartialTrace.clear()
        inLastFrameOfPartialTrace = false
      }
    }
  }

  // Returns true if the host analysis trigger event was sent, false otherwise.
  private fun detectAndHandleHostAnalysisTrigger(logcatMessage: LogcatMessage): Boolean {
    val HOST_ANALYSIS_TRIGGER_STRING = "The heap dump will be collected and analyzed by the Android Studio"
    if (LEAKCANARY_TAG == logcatMessage.header.tag && HOST_ANALYSIS_TRIGGER_STRING in logcatMessage.message) {
      logger.info("Host analysis trigger detected.")
      eventQueue.offer(Common.Event.newBuilder()
                         .setGroupId(pid.toLong())
                         .setPid(pid)
                         .setKind(Common.Event.Kind.LEAKCANARY_HOST_ANALYSIS_TRIGGER)
                         .setTimestamp(getCurrentTimestampInNs())
                         .build())
      return true
    }
    return false
  }
}