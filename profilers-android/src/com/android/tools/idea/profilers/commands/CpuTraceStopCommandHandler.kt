/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Trace
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.android.tools.profilers.ProfilerFormat.PerfettoTrace
import com.android.tools.profilers.cpu.TraceMerger
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class CpuTraceStopCommandHandler(val device: IDevice, private val transportStub: TransportServiceGrpc.TransportServiceBlockingStub) :
  TransportProxy.ProxyCommandHandler {

  private val log = Logger.getInstance(CpuTraceStopCommandHandler::class.java)

  override fun shouldHandle(command: Commands.Command): Boolean {
    return command.type == Commands.Command.CommandType.STOP_TRACE &&
      command.stopTrace.profilerType == Trace.ProfilerType.CPU &&
      (TraceType.from(command.stopTrace.configuration) == TraceType.PERFETTO ||
        TraceMerger.activeV2Sessions.containsKey(command.pid.toLong()) ||
        TraceMerger.activeV2Sessions.containsKey(0L))
  }

  override fun execute(command: Commands.Command): Transport.ExecuteResponse {
    assert(command.type == Commands.Command.CommandType.STOP_TRACE)

    /*
     * 1. Check if this session is an active Tracing 2.0 session (and migrate Startup Traces from PID 0 to the real PID).
     * 2. Forward STOP_TRACE to daemon to halt OS tracing synchronously FIRST, so the Perfetto daemon
     *    stops recording and flushes its OS buffers.
     * 3. If it is a Tracing 2.0 session, we launch a background thread to issue a FLUSH broadcast, pull the
     *    Compose app traces to a host temp directory, and resolve a CompletableFuture for the TraceMerger.
     */
    var appName = TraceMerger.getAppName(command.pid.toLong())
    if (appName == null) {
      // Fallback for Startup Tracing where PID was initially recorded as 0 at START_TRACE.
      val nameFromClient = device.getClientName(command.pid)
      val startupAppName = TraceMerger.activeV2Sessions[0L]
      if (
        startupAppName != null &&
          (nameFromClient == null || nameFromClient.startsWith(startupAppName) || startupAppName.contains(nameFromClient))
      ) {
        appName = startupAppName
        // Migrate the mapping to the real PID so TraceMerger can clean it up later.
        TraceMerger.activeV2Sessions.remove(0L)
        TraceMerger.activeV2Sessions[command.pid.toLong()] = appName
      } else {
        log.warn(
          "Tracing 2.0 STOP: Could not resolve app name for PID ${command.pid}. startupAppName: $startupAppName, nameFromClient: $nameFromClient"
        )
      }
    }

    val response =
      try {
        transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(command).build())
      } finally {
        // If appName is non-null, this PID was marked as a Tracing 2.0 session during START_TRACE.
        if (appName != null) {
          val future = CompletableFuture<File?>()
          TraceMerger.appTracePathCache[command.pid.toLong()] = future

          ApplicationManager.getApplication().executeOnPooledThread {
            var finalHostTempDir: File? = null
            try {
              val flushCmd =
                "am broadcast -a androidx.tracing.profiler.action.FLUSH_TRACES_GET_PATH -n $appName/androidx.tracing.profiler.ConnectedProfilerTracingReceiver"
              val flushOutput = executeShellWithTimeout(flushCmd, 5)

              val pathRegex = Regex("data=\"([^\"]+)\"")
              val pathMatch = pathRegex.find(flushOutput)
              val deviceTraceDir = pathMatch?.groupValues?.get(1)

              if (deviceTraceDir == null) {
                log.warn("Failed to parse trace directory from FLUSH broadcast: $flushOutput")
                return@executeOnPooledThread
              }

              /*
               * Try direct shell ls first (works for profileable apps writing to externalMediaDirs).
               */
              var lsCmd = "sh -c 'ls -1tp \"$deviceTraceDir\" | grep -v /'"
              var lsOutput = executeShellWithTimeout(lsCmd, 5)

              /*
               * If direct ls doesn't return any expected trace files (e.g. due to Permission denied,
               * Not a directory, or I/O error), fallback to run-as.
               */
              if (!VALID_EXTENSIONS.any { lsOutput.contains(it) }) {
                lsCmd = "sh -c 'run-as $appName ls -1tp \"$deviceTraceDir\" | grep -v /'"
                lsOutput = executeShellWithTimeout(lsCmd, 5)
              }

              val fileLines =
                lsOutput
                  .trim()
                  .split("\n")
                  .map { it.trim() }
                  .filter { line -> line.isNotBlank() && VALID_EXTENSIONS.any { line.endsWith(it) } }

              if (fileLines.isEmpty()) {
                log.warn("No valid trace files (.pb, .perfetto-trace, .trace, .pftrace) found in $deviceTraceDir")
                return@executeOnPooledThread
              }

              val hostTempDir = FileUtil.createTempDirectory("compose_traces_${command.pid}_", null)
              hostTempDir.deleteOnExit()
              log.info("Pulling ${fileLines.size} Compose trace files to ${hostTempDir.absolutePath}")

              val pulledAppTraces = mutableListOf<File>()
              for (fileName in fileLines) {
                val remoteFilePath = "$deviceTraceDir/$fileName"
                val tempRemoteFilePath = "/data/local/tmp/${command.pid}_$fileName"
                val localFile = File(hostTempDir, fileName)

                try {
                  var pulledSuccessfully = false

                  /*
                   * Try direct pull (works for Profileable apps writing to externalMediaDirs).
                   */
                  try {
                    device.pullFile(remoteFilePath, localFile.absolutePath)
                    if (localFile.exists() && localFile.length() > 0) {
                      pulledSuccessfully = true
                    }
                  } catch (e: Exception) {}

                  /*
                   * If direct pull fails, try run-as trick (works for Debuggable apps in cacheDir).
                   */
                  if (!pulledSuccessfully) {
                    val cpCmd = "sh -c 'run-as $appName cat \"$remoteFilePath\" > \"$tempRemoteFilePath\"'"
                    try {
                      executeShellWithTimeout(cpCmd, 5)

                      device.pullFile(tempRemoteFilePath, localFile.absolutePath)
                      if (localFile.exists() && localFile.length() > 0) {
                        pulledSuccessfully = true
                      }
                    } finally {
                      executeShellWithTimeout("rm \"$tempRemoteFilePath\"", 2)
                    }
                  }

                  if (pulledSuccessfully) {
                    pulledAppTraces.add(localFile)
                  } else {
                    log.warn("Failed to pull Compose trace file via both strategies: $remoteFilePath")
                    if (localFile.exists()) {
                      localFile.delete()
                    }
                  }
                } catch (e: Exception) {
                  log.warn("Exception while pulling Compose trace file: $remoteFilePath", e)
                }
              }

              if (pulledAppTraces.isNotEmpty()) {
                finalHostTempDir = hostTempDir
                log.info("Successfully pulled Tracing 2.0 app traces directory to ${hostTempDir.absolutePath} for app $appName")
              } else {
                FileUtil.delete(hostTempDir)
              }
            } catch (e: Exception) {
              log.warn("Graceful Failure: Exception during Tracing 2.0 FLUSH/PULL logic. OS trace will continue normally.", e)
            } finally {
              // Send STOP broadcast (always stop, even if flush/pull failed)
              try {
                val stopCmd =
                  "am broadcast -a androidx.tracing.profiler.action.STOP -n $appName/androidx.tracing.profiler.ConnectedProfilerTracingReceiver"
                executeShellWithTimeout(stopCmd, 5)
              } catch (e: Exception) {
                log.warn("Failed to send STOP broadcast", e)
              }
              future.complete(finalHostTempDir)
            }
          }
        }
      }

    return response
  }

  private fun executeShellWithTimeout(command: String, timeoutSeconds: Long): String {
    val receiver = CollectingOutputReceiver()
    device.executeShellCommand(command, receiver, timeoutSeconds, TimeUnit.SECONDS)
    return receiver.output
  }

  companion object {
    private val VALID_EXTENSIONS = PerfettoTrace.extensions.map { ".$it" } + ".pb"
  }
}
