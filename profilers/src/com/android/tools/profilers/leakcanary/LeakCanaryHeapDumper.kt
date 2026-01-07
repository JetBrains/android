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
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Commands.SendLeakCanaryAnalysisData
import java.util.concurrent.TimeUnit
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.StudioProfilers
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import shark.HeapAnalysisSuccess

/**
 * Handles the orchestration of triggering a heap dump on the device, downloading the resulting
 * .hprof file, and passing it to the [SharkHostAnalyzer] for analysis.
 */
class LeakCanaryHeapDumper(private val profilers: StudioProfilers) {
  private val logger = Logger.getInstance(LeakCanaryHeapDumper::class.java)
  private val isHeapDumpInProgress = AtomicBoolean(false)
  lateinit var onHostAnalysisFinished: (Analysis) -> Unit
  lateinit var onAnalysisProgress: (Int) -> Unit

  /**
   * This function orchestrates the entire host-side analysis workflow.
   */
  fun triggerAndAnalyze() {
    if (!isHeapDumpInProgress.compareAndSet(false, true)) {
      logger.info("Host analysis is already in progress. Ignoring trigger.")
      return
    }

    try {
      val commandId = sendHeapDumpCommand()
      val heapDumpInfo = waitForHeapDumpStatus(commandId)
      val heapDumpEndTime = waitForHeapDumpCompletion(heapDumpInfo)
      val hprofFile = downloadHeapDump(heapDumpInfo)
      analyzeAndHandleResult(hprofFile)
      sendHeapDumpCompleteCommand(heapDumpEndTime)
    }
    catch (e: Exception) {
      logger.error("Host analysis process failed.", e)
    }
    finally {
      isHeapDumpInProgress.set(false)
    }
  }

  /**
   * Sends a HEAP_DUMP command and returns its ID.
   */
  private fun sendHeapDumpCommand(): Int {
    logger.info("Host analysis triggered. Initiating heap dump.")
    val dumpCommand = Commands.Command.newBuilder()
      .setStreamId(profilers.session.streamId)
      .setPid(profilers.session.pid)
      .setSessionId(profilers.session.sessionId)
      .setType(Commands.Command.CommandType.HEAP_DUMP)
      .setShouldEndSession(false)
      .build()
    val response = profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(dumpCommand).build())
    return response.commandId
  }

  /**
   * Waits for the heap dump status event and returns the HeapDumpInfo.
   */
  private fun waitForHeapDumpStatus(commandId: Int): Memory.HeapDumpInfo {
    val future = CompletableFuture<Memory.HeapDumpInfo>()
    val statusListener = TransportEventListener(
      eventKind = Common.Event.Kind.MEMORY_HEAP_DUMP_STATUS,
      executor = profilers.ideServices.poolExecutor,
      filter = { event -> event.commandId == commandId },
      streamId = { profilers.session.streamId },
      processId = { profilers.session.pid },
      callback = { event ->
        val status = event.memoryHeapdumpStatus.status
        if (status.status == Memory.HeapDumpStatus.Status.SUCCESS) {
          logger.info("Heap dump process started on device for id ${status.startTime}. Waiting for completion signal.")
          future.complete(Memory.HeapDumpInfo.newBuilder().setStartTime(status.startTime).build())
        }
        else {
          future.completeExceptionally(RuntimeException("Heap dump failed to start on device. Status: ${status.status}"))
        }
        true // Unregister this listener.
      })
    profilers.transportPoller.registerListener(statusListener)
    return future.get() // Block the background thread until the event arrives.
  }

  /**
   * Waits for the heap dump completion event.
   */
  private fun waitForHeapDumpCompletion(heapDumpInfo: Memory.HeapDumpInfo): Long {
    val future = CompletableFuture<Long>()
    val completionListener = TransportEventListener(
      eventKind = Common.Event.Kind.MEMORY_HEAP_DUMP,
      executor = profilers.ideServices.poolExecutor,
      streamId = { profilers.session.streamId },
      processId = { profilers.session.pid },
      startTime = { heapDumpInfo.startTime },
      callback = { event ->
        if (!event.isEnded)
          return@TransportEventListener false  // Not the end event.

        if (event.memoryHeapdump.info.startTime != heapDumpInfo.startTime)
          return@TransportEventListener false  // Belongs to a different heap dump.

        if (event.memoryHeapdump.info.success) {
          val endTime = event.memoryHeapdump.info.endTime
          logger.info("Detected heap dump completion event for id ${heapDumpInfo.startTime} with end time $endTime (ns).")
          future.complete(endTime)
        }
        else {
          future.completeExceptionally(RuntimeException("Heap dump failed on device for id ${heapDumpInfo.startTime}."))
        }
        true // Success. Unregister the listener.
      })
    profilers.transportPoller.registerListener(completionListener)
    return future.get() // Block the background thread until the event arrives.
  }

  /**
   * Downloads the hprof file from the device.
   */
  private fun downloadHeapDump(heapDumpInfo: Memory.HeapDumpInfo): File {
    val bytesRequest = Transport.BytesRequest.newBuilder()
      .setStreamId(profilers.session.streamId)
      .setId(heapDumpInfo.startTime.toString())
      .build()
    val fileResponse = profilers.client.transportClient.getFile(bytesRequest)
    val hprofFile = if (fileResponse.filePath.isEmpty()) null else File(fileResponse.filePath)
    if (hprofFile == null || !hprofFile.exists()) {
      throw RuntimeException("Hprof file not found on device despite completion signal. Expected path: ${fileResponse.filePath}")
    }
    return hprofFile
  }

  /**
   * Starts the Shark analysis and handles the result on the UI thread.
   */
  private fun analyzeAndHandleResult(hprofFile: File) {
    logger.info("Hprof file downloaded to: ${hprofFile.path}. Starting analysis.")
    val analysisResult = SharkHostAnalyzer().analyze(hprofFile) { progress ->
      profilers.ideServices.mainExecutor.execute {
        onAnalysisProgress(progress)
      }
    }
    if (analysisResult is HeapAnalysisSuccess) {
      val analysis = LeakCanaryParser().parseLogcatMessage(analysisResult.toString())
      if (analysis != null) {
        sendAnalysisResultCommand(analysis)
        profilers.ideServices.mainExecutor.execute {
          onHostAnalysisFinished(analysis)
        }
      }
    }
    else {
      throw RuntimeException("Heap analysis failed for ${hprofFile.path}")
    }
  }

  /**
   * Sends the LeakCanary analysis result to the transport pipeline.
   */
  private fun sendAnalysisResultCommand(analysis: Analysis) {
    val analysisData = SendLeakCanaryAnalysisData.newBuilder()
      .setData(analysis.toString())
      .build()

    val command = Commands.Command.newBuilder()
      .setStreamId(profilers.session.streamId)
      .setPid(profilers.session.pid)
      .setType(Commands.Command.CommandType.SEND_LEAKCANARY_ANALYSIS)
      .setSendLeakcanaryAnalysis(analysisData)
      .build()

    profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(command).build())
    logger.info("Sent SEND_LEAKCANARY_ANALYSIS command to transport.")
  }

  /**
   * Sends a command to perfa to indicate that the host-side heap dump download and analysis is complete.
   */
  private fun sendHeapDumpCompleteCommand(heapDumpTime: Long) {
    logger.info("Sending heap dump complete signal with timestamp: $heapDumpTime (ns).")
    val data = Commands.SignalHeapDumpCompleteData.newBuilder()
      .setHeapDumpTimestamp(heapDumpTime)
      .build()
    val command = Commands.Command.newBuilder()
      .setStreamId(profilers.session.streamId)
      .setPid(profilers.session.pid)
      .setSessionId(profilers.session.sessionId)
      .setType(Commands.Command.CommandType.SIGNAL_HEAP_DUMP_COMPLETE)
      .setSignalHeapDumpComplete(data)
      .build()
    profilers.client.transportClient.execute(Transport.ExecuteRequest.newBuilder().setCommand(command).build())
  }
}