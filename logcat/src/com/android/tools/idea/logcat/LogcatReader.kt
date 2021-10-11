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

import com.android.ddmlib.DdmPreferences
import com.android.ddmlib.IDevice
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.run.LoggingReceiver
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Starts a background thread that reads logcat messages and sends them back to the caller.
 */
internal class LogcatReader(private val device: IDevice, logcatPresenter: LogcatPresenter) : Disposable {

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
  }

  // Start a Logcat command on the device. This is a blocking call and does not return until the receiver is canceled.
  fun start() {
    logcatReceiver.resume()
    // The thread is released on dispose() when logcatReceiver.isCanceled() returns true and executeShellCommand() aborts.
    executor.execute {
      val filename = System.getProperty("studio.logcat.debug.readFromFile")
      if (filename != null && SystemInfo.isUnix) {
        executeDebugLogcatFromFile(filename)
      }
      else {
        device.executeShellCommand("logcat -v long -v epoch", logcatReceiver)
      }
    }
  }

  // Stop the logcat command by canceling the receiver. This is a blocking call.
  // The underlying thread will exit and free up the executor, so it can trigger the
  fun stop() {
    val latch = CountDownLatch(1)
    logcatReceiver.stop()
    executor.execute(latch::countDown)
    latch.await(DdmPreferences.getTimeOut().toLong(), TimeUnit.MILLISECONDS)
  }

  // Clear the Logcat buffer on the device. This is a blocking call.
  fun clearLogcat() {
    device.executeShellCommand("logcat -c", LoggingReceiver(thisLogger()))
  }

  override fun dispose() {}

  // A blocking call that runs a tail command reading logcat data from a file. Only to be used for debugging and profiling, this method
  // mimics the behavior of AdbHelper#executeRemoteCommand() including a busy wait loop with a 25ms delay.
  private fun executeDebugLogcatFromFile(filename: String) {
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
}
