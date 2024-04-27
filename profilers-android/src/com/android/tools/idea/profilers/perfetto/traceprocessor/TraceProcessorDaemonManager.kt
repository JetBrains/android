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
import com.android.tools.nativeSymbolizer.getLlvmSymbolizerPath
import com.android.tools.profilers.analytics.FeatureTracker
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


/**
 * This is responsible to manage the lifetime of an instance of the TraceProcessorDaemon,
 * spawning a new one if necessary and properly shutting it down at the end of Studio execution.
 */
class TraceProcessorDaemonManager(
    private val ticker: Ticker,
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()): Disposable {

  // All access paths to process should be synchronized.
  private var process: Process? = null
  // Controls if we started the dispose process for this manager, to prevent new instances of daemon to be spawned.
  private var disposed = false
  // The port in which the daemon is listening to, available to the Client to build the channel.
  var daemonPort = 0
    private set

  private companion object {
    private val LOGGER = Logger.getInstance(TraceProcessorDaemonManager::class.java)

    // Timeout in milliseconds to wait for the TPD to start: 1 minute
    private val TPD_SPAWN_TIMEOUT = TimeUnit.MINUTES.toMillis(1)
    // TPD is hardcoded to output these strings on stdout:
    private val SERVER_STARTED = Pattern.compile("^Server listening on (?:127.0.0.1|localhost):(?<port>\\d+)\n*$")
    // TPD write the following message to stdout when it cannot find a port to bind.
    private const val SERVER_PORT_BIND_FAILED = "Server failed to start. A port number wasn't bound."

    private val TPD_DEV_PATH: String by lazy {
      when {
        SystemInfo.isWindows -> {
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
    private val TPD_EXECUTABLE: String by lazy {
      when {
        SystemInfo.isWindows -> {
          "trace_processor_daemon.exe"
        }
        else -> {
          "trace_processor_daemon"
        }
      }
    }

    private val TPD_BINARY = DeployableFile.Builder(TPD_EXECUTABLE)
      .setReleaseDir(TPD_RELEASE_PATH)
      .setDevDir(TPD_DEV_PATH)
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
  fun makeSureDaemonIsRunning(tracker: FeatureTracker) {
    // Spawn a new one if either we don't have one running already or if the current one is not alive anymore.
    if (!processIsRunning() && !disposed) {
      val spawnStopwatch = Stopwatch.createStarted(ticker)
      LOGGER.info("TPD Manager: Starting new instance of TPD")
      val newProcess = ProcessBuilder(getExecutablePath(), "--llvm_symbolizer_path", getLlvmSymbolizerPath())
        .redirectErrorStream(true)
        .start()
      val stdoutListener = TPDStdoutListener(BufferedReader(InputStreamReader(newProcess.inputStream)))
      executorService.execute(stdoutListener)

      // wait until we receive the message that the daemon is listening and get the port
      stdoutListener.waitForStatusChangeOrTerminated(TPD_SPAWN_TIMEOUT)

      spawnStopwatch.stop()
      val timeToSpawnMs = spawnStopwatch.elapsed(TimeUnit.MILLISECONDS)

      if (stdoutListener.status == DaemonStatus.RUNNING) {
        tracker.trackTraceProcessorDaemonSpawnAttempt(true, timeToSpawnMs)
        daemonPort = stdoutListener.selectedPort
        process = newProcess
        LOGGER.info("TPD Manager: TPD instance ready on port $daemonPort.")
      } else {
        tracker.trackTraceProcessorDaemonSpawnAttempt(false, timeToSpawnMs)
        LOGGER.info("TPD Manager: Unable to start TPD instance.")
        // Make sure we clean up our instance to not leave a zombie process
        newProcess?.destroyForcibly()?.waitFor()
        throw RuntimeException("Unable to start TPD instance.")
      }
    }
  }

  /**
   * Represents the status of the daemon, that we can extract from its output/logging.
   */
  @VisibleForTesting
  enum class DaemonStatus { STARTING, RUNNING, FAILED, END_OF_STREAM }

  /**
   * This runnable will keep consuming the output (stdout and stderr) from the daemon and will pipe it to our own logs.
   * Besides the obvious utility of being able to track down what the daemon is doing duing debugging, it seems this
   * is also important to not lock the daemon if it produces too much output (see b/158124339 for full context).
   */
  @VisibleForTesting
  class TPDStdoutListener(private val outputReader: BufferedReader) : Runnable {
    private val statusLock = Object()
    var status = DaemonStatus.STARTING
      private set(newStatus) {
        synchronized(statusLock) {
          LOGGER.debug("TPD Manager: Daemon status: $newStatus")
          field = newStatus
          statusLock.notifyAll()
        }
      }
    var selectedPort = 0
      private set

    override fun run() {
      while (true) {
        val line = outputReader.readLine()
        if (line == null) {
          LOGGER.debug("TPD Manager: [TPD Log] EOF")
          status = DaemonStatus.END_OF_STREAM
          break
        }
        LOGGER.debug("TPD Manager: [TPD Log] $line")

        val serverOkMatcher = SERVER_STARTED.matcher(line)
        if (serverOkMatcher.matches()) {
          selectedPort = serverOkMatcher.group("port").toInt()
          status = DaemonStatus.RUNNING
        } else if (line.startsWith(SERVER_PORT_BIND_FAILED)) {
          status = DaemonStatus.FAILED
          break
        }
      }
    }

    @VisibleForTesting
    fun waitUntilTerminated(timeout: Long) {
      while(!terminated()) {
        waitForStatusChangeOrTerminated(timeout)
      }
    }

    fun waitForStatusChangeOrTerminated(timeout:Long) {
      synchronized(statusLock) {
        // We do a check to avoid the timeout wait unnecessarily if the status isn't expected status already.
        if ( !terminated() ) statusLock.wait(timeout)
      }
    }

    private fun terminated() = status == DaemonStatus.END_OF_STREAM || status == DaemonStatus.FAILED
  }

  @Synchronized
  override fun dispose() {
    disposed = true
    // We waitFor after destroying the process in order to not leave a Zombie process in the system.
    process?.destroyForcibly()?.waitFor()
  }
}