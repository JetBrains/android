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
package com.android.tools.profilers.leakcanary

import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.leakcanarylib.LeakCanaryParser
import com.android.tools.leakcanarylib.data.Analysis
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Commands.SendLeakCanaryAnalysisData
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.tasks.analytics.LeakCanaryProcessingErrorCode
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import shark.HeapAnalysisFailure
import shark.HeapAnalysisSuccess

/** Exception thrown to propagate specific telemetry error codes up to the main try/catch block. */
class LeakCanaryProcessingException(val errorCode: LeakCanaryProcessingErrorCode, message: String) : RuntimeException(message)

/**
 * Handles the orchestration of triggering a heap dump on the device, downloading the resulting .hprof file, and passing it to the
 * [SharkHostAnalyzer] for analysis.
 */
class LeakCanaryHeapDumper(private val profilers: StudioProfilers) {
  private val logger = Logger.getInstance(LeakCanaryHeapDumper::class.java)
  private val isHeapDumpInProgress = AtomicBoolean(false)

  fun isHeapDumpInProgress(): Boolean = isHeapDumpInProgress.get()

  lateinit var onHostAnalysisFinished: (Analysis, Long, Long, Long) -> Unit
  lateinit var onAnalysisProgress: (Int) -> Unit
  lateinit var onFatalError: (LeakCanaryProcessingErrorCode, String) -> Unit
  lateinit var onResetRetainedObjectCount: () -> Unit

  /**
   * This function orchestrates the entire host-side analysis workflow.
   *
   * @return true if the heap dump process was successfully started, false if it was ignored because a dump is already running.
   */
  fun triggerAndAnalyze(): Boolean {
    if (!isHeapDumpInProgress.compareAndSet(false, true)) {
      logger.info("Host analysis is already in progress. Ignoring trigger.")
      return false
    }

    try {
      val commandId = sendHeapDumpCommand()
      val heapDumpInfo = waitForHeapDumpStatus(commandId)
      val heapDumpEndTime = waitForHeapDumpCompletion(heapDumpInfo)
      val downloadStartTime = System.currentTimeMillis()
      val hprofFile = downloadHeapDump(heapDumpInfo)
      val downloadDurationMs = System.currentTimeMillis() - downloadStartTime
      val hprofFileSizeBytes = hprofFile.length()
      analyzeAndHandleResult(hprofFile, hprofFileSizeBytes, downloadDurationMs)

      // Temporarily set to 0 to prevent the UI from flickering back to the old, pre-dump count
      // during the 2-3 seconds it takes the device to process the completion command and broadcast its true count.
      onResetRetainedObjectCount()

      sendHeapDumpCompleteCommand(heapDumpEndTime)
      logger.info("Host analysis process completed.")
    } catch (e: OutOfMemoryError) {
      // Android Studio OOMs before Shark runs (e.g., during download or byte allocation)
      logger.error("Host analysis process failed due to OOM.", e)
      onFatalError(LeakCanaryProcessingErrorCode.SHARK_ANALYSIS_OOM, "Android Studio ran out of memory while analyzing the heap dump.")
    } catch (e: Exception) {
      val cause = if (e is ExecutionException) e.cause else e

      if (cause is LeakCanaryProcessingException) {
        logger.error("Host analysis process failed: ${cause.message}", cause)
        onFatalError(cause.errorCode, cause.message ?: "Unknown error")
      } else {
        logger.error("Host analysis process failed unexpectedly.", cause)
        onFatalError(LeakCanaryProcessingErrorCode.UNKNOWN_ERROR, "Unexpected error: ${cause?.message}")
      }
    } finally {
      isHeapDumpInProgress.set(false)
    }
    return true
  }

  /** Sends a HEAP_DUMP command and returns its ID. */
  private fun sendHeapDumpCommand(): Int {
    logger.info("Host analysis triggered. Initiating heap dump.")
    val dumpCommand =
      Commands.Command.newBuilder()
        .setStreamId(profilers.session.streamId)
        .setPid(profilers.session.pid)
        .setSessionId(profilers.session.sessionId)
        .setType(Commands.Command.CommandType.HEAP_DUMP)
        .setShouldEndSession(false)
        .build()
    try {
      val response = profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(dumpCommand).build())
      logger.info(
        "Sent HEAP_DUMP command to transport with ID: ${response.commandId}. streamId: ${dumpCommand.streamId}, pid: ${dumpCommand.pid}, sessionId: ${dumpCommand.sessionId}"
      )
      return response.commandId
    } catch (e: Exception) {
      logger.warn(
        "Failed to send HEAP_DUMP command to transport. streamId: ${dumpCommand.streamId}, pid: ${dumpCommand.pid}, sessionId: ${dumpCommand.sessionId}",
        e,
      )
      throw e
    }
  }

  /** Waits for the heap dump status event and returns the HeapDumpInfo. */
  private fun waitForHeapDumpStatus(commandId: Int): Memory.HeapDumpInfo {
    val future = CompletableFuture<Memory.HeapDumpInfo>()
    val statusListener =
      TransportEventListener(
        eventKind = Common.Event.Kind.MEMORY_HEAP_DUMP_STATUS,
        executor = profilers.ideServices.poolExecutor,
        filter = { event -> event.commandId == commandId },
        streamId = { profilers.session.streamId },
        processId = { profilers.session.pid },
        callback = { event ->
          val status = event.memoryHeapdumpStatus.status
          logger.info("Received MEMORY_HEAP_DUMP_STATUS event: ${status.status}")
          if (status.status == Memory.HeapDumpStatus.Status.SUCCESS) {
            logger.info("Heap dump process started on device for id ${status.startTime}. Waiting for completion signal.")
            future.complete(Memory.HeapDumpInfo.newBuilder().setStartTime(status.startTime).build())
          } else {
            future.completeExceptionally(
              LeakCanaryProcessingException(
                LeakCanaryProcessingErrorCode.HEAP_DUMP_GENERATION_FAILED,
                "Heap dump failed to start on device. Status: ${status.status}",
              )
            )
          }
          true // Unregister this listener.
        },
      )
    profilers.transportPoller.registerListener(statusListener)
    return future.get() // Block the background thread until the event arrives.
  }

  /** Waits for the heap dump completion event. */
  private fun waitForHeapDumpCompletion(heapDumpInfo: Memory.HeapDumpInfo): Long {
    val future = CompletableFuture<Long>()
    val completionListener =
      TransportEventListener(
        eventKind = Common.Event.Kind.MEMORY_HEAP_DUMP,
        executor = profilers.ideServices.poolExecutor,
        streamId = { profilers.session.streamId },
        processId = { profilers.session.pid },
        startTime = { heapDumpInfo.startTime },
        callback = { event ->
          if (!event.isEnded) return@TransportEventListener false // Not the end event.

          if (event.memoryHeapdump.info.startTime != heapDumpInfo.startTime)
            return@TransportEventListener false // Belongs to a different heap dump.

          logger.info("Received MEMORY_HEAP_DUMP event. Success: ${event.memoryHeapdump.info.success}")

          if (event.memoryHeapdump.info.success) {
            val endTime = event.memoryHeapdump.info.endTime
            logger.info("Detected heap dump completion event for id ${heapDumpInfo.startTime} with end time $endTime (ns).")
            future.complete(endTime)
          } else {
            future.completeExceptionally(
              LeakCanaryProcessingException(
                LeakCanaryProcessingErrorCode.HEAP_DUMP_GENERATION_FAILED,
                "Heap dump failed on device for id ${heapDumpInfo.startTime}.",
              )
            )
          }
          true // Success. Unregister the listener.
        },
      )
    profilers.transportPoller.registerListener(completionListener)
    return future.get() // Block the background thread until the event arrives.
  }

  /** Downloads the hprof file from the device. */
  private fun downloadHeapDump(heapDumpInfo: Memory.HeapDumpInfo): File {
    val bytesRequest =
      Transport.BytesRequest.newBuilder().setStreamId(profilers.session.streamId).setId(heapDumpInfo.startTime.toString()).build()
    val fileResponse = profilers.client.transportClient.getFile(bytesRequest)
    logger.info("Downloaded heap dump file from transport to: ${fileResponse.filePath}")
    val hprofFile = if (fileResponse.filePath.isEmpty()) null else File(fileResponse.filePath)
    if (hprofFile == null || !hprofFile.exists()) {
      throw LeakCanaryProcessingException(
        LeakCanaryProcessingErrorCode.HPROF_DOWNLOAD_FAILED,
        "Hprof file not found on device despite completion signal. Expected path: ${fileResponse.filePath}",
      )
    }
    return hprofFile
  }

  /** Starts the Shark analysis and handles the result on the UI thread. */
  private fun analyzeAndHandleResult(hprofFile: File, hprofFileSizeBytes: Long, downloadDurationMs: Long) {
    logger.info("Hprof file downloaded to: ${hprofFile.path}. Starting analysis.")
    val analysisStartTime = System.currentTimeMillis()
    val analysisResult =
      SharkHostAnalyzer().analyze(hprofFile) { progress -> profilers.ideServices.mainExecutor.execute { onAnalysisProgress(progress) } }
    val analysisDurationMs = System.currentTimeMillis() - analysisStartTime

    if (analysisResult is HeapAnalysisSuccess) {
      val analysis = LeakCanaryParser().parseLogcatMessage(analysisResult.toString())
      if (analysis != null) {
        logger.info(
          "Shark analysis finished successfully. File Size: $hprofFileSizeBytes, Download time: $downloadDurationMs ms, Analysis time: $analysisDurationMs ms"
        )
        sendAnalysisResultCommand(analysis)
        profilers.ideServices.mainExecutor.execute {
          onHostAnalysisFinished(analysis, hprofFileSizeBytes, downloadDurationMs, analysisDurationMs)
        }
      } else {
        onFatalError(LeakCanaryProcessingErrorCode.PARSING_FAILURE, "Failed to parse Shark analysis output")
        return
      }
    } else if (analysisResult is HeapAnalysisFailure) {
      val cause = analysisResult.exception.cause
      if (cause is OutOfMemoryError) {
        throw LeakCanaryProcessingException(
          LeakCanaryProcessingErrorCode.SHARK_ANALYSIS_OOM,
          "Android Studio ran out of memory while analyzing the heap dump.",
        )
      } else {
        throw LeakCanaryProcessingException(
          LeakCanaryProcessingErrorCode.SHARK_ANALYSIS_EXCEPTION,
          "Heap analysis failed for ${hprofFile.path}: ${cause?.message}",
        )
      }
    } else {
      throw LeakCanaryProcessingException(
        LeakCanaryProcessingErrorCode.SHARK_ANALYSIS_EXCEPTION,
        "Heap analysis failed for ${hprofFile.path}",
      )
    }
  }

  /** Sends the LeakCanary analysis result to the transport pipeline. */
  private fun sendAnalysisResultCommand(analysis: Analysis) {
    val analysisData = SendLeakCanaryAnalysisData.newBuilder().setData(analysis.toString()).build()

    val command =
      Commands.Command.newBuilder()
        .setStreamId(profilers.session.streamId)
        .setPid(profilers.session.pid)
        .setSessionId(profilers.session.sessionId)
        .setType(Commands.Command.CommandType.SEND_LEAKCANARY_ANALYSIS)
        .setSendLeakcanaryAnalysis(analysisData)
        .build()

    try {
      profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(command).build())
      logger.info(
        "Sent SEND_LEAKCANARY_ANALYSIS command to transport. streamId: ${command.streamId}, pid: ${command.pid}, sessionId: ${command.sessionId}"
      )
    } catch (e: Exception) {
      logger.warn(
        "Failed to send SEND_LEAKCANARY_ANALYSIS command to transport. streamId: ${command.streamId}, pid: ${command.pid}, sessionId: ${command.sessionId}",
        e,
      )
    }
  }

  /** Sends a command to perfa to indicate that the host-side heap dump download and analysis is complete. */
  private fun sendHeapDumpCompleteCommand(heapDumpTime: Long) {
    logger.info("Sending heap dump complete signal with timestamp: $heapDumpTime (ns).")

    if (!LeakCanaryTaskHandler.attachAgentAndWait(profilers, profilers.session.streamId, profilers.process)) {
      throw IllegalStateException("PROFILER: Agent attachment failed. Skipping SIGNAL_HEAP_DUMP_COMPLETE command.")
    }

    val data = Commands.SignalHeapDumpCompleteData.newBuilder().setHeapDumpTimestamp(heapDumpTime).build()
    val command =
      Commands.Command.newBuilder()
        .setStreamId(profilers.session.streamId)
        .setPid(profilers.session.pid)
        .setSessionId(profilers.session.sessionId)
        .setType(Commands.Command.CommandType.SIGNAL_HEAP_DUMP_COMPLETE)
        .setSignalHeapDumpComplete(data)
        .build()
    try {
      profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(command).build())
      logger.info(
        "Sent SIGNAL_HEAP_DUMP_COMPLETE command to transport. streamId: ${command.streamId}, pid: ${command.pid}, sessionId: ${command.sessionId}"
      )
    } catch (e: Exception) {
      logger.warn(
        "Failed to send SIGNAL_HEAP_DUMP_COMPLETE command to transport. streamId: ${command.streamId}, pid: ${command.pid}, sessionId: ${command.sessionId}",
        e,
      )
    }
  }
}
