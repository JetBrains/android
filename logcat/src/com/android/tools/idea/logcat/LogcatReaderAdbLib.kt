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

import com.android.adblib.AdbDeviceServices
import com.android.adblib.DeviceSelector
import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.adblib.ddmlibcompatibility.ShellCollectorToIShellOutputReceiver
import com.android.tools.idea.adblib.ddmlibcompatibility.toDeviceSelector
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Implementation of [LogcatReceiver] using coroutines and `adblib`
 */
internal class LogcatReaderAdbLibImpl(project: Project, device: IDevice, logcatPresenter: LogcatPresenter)
  : LogcatReader(device, logcatPresenter) {
  /**
   * The [CoroutineScope] used to control the lifetime of the coroutines launched by this implementation
   */
  private val scope = AndroidCoroutineScope(this)

  /**
   * The [AdbDeviceServices] instance used to launch logcat commands
   */
  private val deviceServices = AdbLibService.getInstance(project).session.deviceServices

  /**
   * The [LogcatReceiver] instance used to process incoming `logcat` messages
   */
  private val logcatReceiver = LogcatReceiver(
    device,
    this,
    object : LogcatReceiver.LogcatListener {
      @WorkerThread
      override fun onLogMessagesReceived(messages: List<LogCatMessage>) {
        @Suppress("UnstableApiUsage")
        ApplicationManager.getApplication().assertIsNonDispatchThread()

        // Since we are eventually bound by the UI thread, we need to block in order to throttle the caller.
        // When the logcat reading code is converted properly to coroutines, we will already be in the
        // proper scope here and will suspend on a full channel or flow.
        runBlocking {
          logcatPresenter.processMessages(messages)
        }
      }
    })

  init {
    Disposer.register(logcatPresenter, this)
    start()
  }

  private fun start() {
    scope.launch(workerThread) {
      val filename = System.getProperty("studio.logcat.debug.readFromFile")
      if (filename != null && SystemInfo.isUnix) {
        executeDebugLogcatFromFile(filename, logcatReceiver)
      }
      else {
        val command = buildLogcatCommand(device)
        shellToReceiver(device.toDeviceSelector(), command, logcatReceiver)
      }
    }
  }

  @WorkerThread
  override fun clearLogcat() {
    // This is a blocking call, don't call from EDT
    @Suppress("UnstableApiUsage")
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    // Creates the job as part of the scope, so it is canceled on dispose
    val job = scope.launch {
      val receiver = LoggingReceiver(thisLogger())
      shellToReceiver(device.toDeviceSelector(), "logcat -c", receiver)
    }

    // Waits for the job to finish, since we need to block the calling thread
    @Suppress("ConvertLambdaToReference")
    runBlocking { job.join() }
  }

  @AnyThread
  override fun dispose() {
    // Nothing to do here, but our child [scope] is cancelled because it was registered as our child disposable
  }

  /**
   * Executes the shell [command] and forwards its output to an [IShellOutputReceiver]
   */
  private suspend fun shellToReceiver(device: DeviceSelector, command: String, receiver: IShellOutputReceiver) {
    val collector = ShellCollectorToIShellOutputReceiver(receiver)
    deviceServices.shell(device, command, collector).first()
  }
}
