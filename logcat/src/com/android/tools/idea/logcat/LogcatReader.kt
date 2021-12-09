/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.IDevice
import com.android.ddmlib.logcat.LogCatMessage
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.flags.StudioFlags
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors

/**
 * Abstraction over the execution of the `logcat` command, forwarding its output to [logcatPresenter].
 */
internal interface LogcatReader : Disposable {
  /**
   * The [LogcatPresenter] that will be invoked, via [LogcatPresenter.processMessages], when `logcat` messages
   * are received from the device.
   */
  val logcatPresenter: LogcatPresenter

  /**
   * Clears the `logcat` buffer on the device. This is a blocking call that succeeds when the underlying `logcat -c` command
   * successfully finishes.
   */
  @WorkerThread
  fun clearLogcat()

  companion object {
    fun create(project: Project, device: IDevice, logcatPresenter: LogcatPresenter): LogcatReader {
      return if (StudioFlags.ADBLIB_MIGRATION_LOGCAT_V2.get()) {
        LogcatReaderAdbLibImpl(project, device, logcatPresenter)
      }
      else {
        LogcatReaderDdmLib(device, logcatPresenter)
      }
    }


    // A blocking call that runs a tail command reading logcat data from a file. Only to be used for debugging and profiling, this method
    // mimics the behavior of AdbHelper#executeRemoteCommand() including a busy wait loop with a 25ms delay.
    internal fun executeDebugLogcatFromFile(filename: String, logcatReceiver: LogcatReceiver) {
      if (!File(filename).exists()) {
        Logger.getInstance(LogcatReader::class.java).warn("Failed to load logcat from $filename. File does not exist")
      }
      val process = ProcessBuilder("tail", "-n", "+1", "-f", filename).start()
      val inputStream = process.inputStream
      val buf = ByteArray(16384)
      while (!logcatReceiver.isCancelled) {
        if (inputStream.available() > 0) {
          val n = inputStream.read(buf)
          if (n < 0) {
            break
          }
          logcatReceiver.addOutput(buf, 0, n)
        }
        else {
          Thread.sleep(25)
        }
      }
      logcatReceiver.flush()
    }

    internal fun buildLogcatCommand(device: IDevice): String {
      val command = StringBuilder("logcat -v long")
      if (device.version.apiLevel >= AndroidVersion.VersionCodes.N) {
        command.append(" -v epoch")
      }
      return command.toString()
    }
  }
}

/**
 * Starts a background thread that reads logcat messages and sends them back to the caller.
 */
internal class LogcatReaderDdmLib(private val device: IDevice, override val logcatPresenter: LogcatPresenter) : LogcatReader {

  private val executor = Executors.newSingleThreadExecutor(
    ThreadFactoryBuilder()
      .setNameFormat("Android Logcat Service Thread %s for Device Serial Number $device")
      .build())

  private val logcatReceiver = LogcatReceiver(
    device,
    this,
    object : LogcatReceiver.LogcatListener {
      override fun onLogMessagesReceived(messages: List<LogCatMessage>) {
        // Since we are eventually bound by the UI thread, we need to block in order to throttle the caller.
        // When the logcat reading code is converted properly to coroutines, we will already be in the
        // proper scope here and will suspend on a full channel or flow.
        runBlocking(workerThread) {
          logcatPresenter.processMessages(messages)
        }
      }
    })

  init {
    Disposer.register(logcatPresenter, this)
    start()
  }

  // Start a Logcat command on the device. This is a blocking call and does not return until the receiver is canceled.
  private fun start() {
    // The thread is released on dispose() when logcatReceiver.isCanceled() returns true and executeShellCommand() aborts.
    executor.execute {
      val filename = System.getProperty("studio.logcat.debug.readFromFile")
      if (filename != null && SystemInfo.isUnix) {
        LogcatReader.executeDebugLogcatFromFile(filename, logcatReceiver)
      }
      else {
        val command = LogcatReader.buildLogcatCommand(device)
        device.executeShellCommand(command, logcatReceiver)
      }
    }
  }

  // Clear the Logcat buffer on the device. This is a blocking call.
  @WorkerThread
  override fun clearLogcat() {
    // This is a blocking call, don't call from EDT
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    device.executeShellCommand("logcat -c", LoggingReceiver(thisLogger()))
  }

  @AnyThread
  override fun dispose() {
  }
}
