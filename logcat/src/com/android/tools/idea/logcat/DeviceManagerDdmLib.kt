/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

/**
 * Starts a background thread that reads logcat messages and sends them back to the caller.
 */
internal class DeviceManagerDdmLib(device: IDevice, logcatPresenter: LogcatPresenter) : LogcatDeviceManager(device, logcatPresenter) {

  private val executor = Executors.newSingleThreadExecutor(
    ThreadFactoryBuilder()
      .setNameFormat("Android Logcat Service Thread %s for Device Serial Number $device")
      .build()
  )

  private val logcatReceiver = LogcatReceiver(
    device,
    this,
    object : LogcatReceiver.LogcatListener {
      override fun onLogMessagesReceived(messages: List<LogCatMessage>) {
        // Since we are eventually bound by the UI thread, we need to block in order to throttle the caller.
        // When the logcat reading code is converted properly to coroutines, we will already be in the
        // proper scope here and will suspend on a full channel or flow.
        runBlocking(AndroidDispatchers.workerThread) {
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
        executeDebugLogcatFromFile(filename, logcatReceiver)
      } else {
        val command = buildLogcatCommand(device)
        device.executeShellCommand(command, logcatReceiver)
      }
    }
  }

  // Clear the Logcat buffer on the device. This is a blocking call.
  @WorkerThread
  override fun clearLogcat() {
    // This is a blocking call, don't call from EDT
    @Suppress("UnstableApiUsage")
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    device.executeShellCommand("logcat -c", LoggingReceiver(thisLogger()))
  }

  @AnyThread
  override fun dispose() {
  }
}