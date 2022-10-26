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
package com.android.tools.idea.run.debug

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.AndroidLogcatFormatter
import com.android.tools.idea.logcat.AndroidLogcatPreferences
import com.android.tools.idea.logcat.AndroidLogcatService
import com.android.tools.idea.logcat.LogcatHeaderFormat
import com.android.tools.idea.logcat.output.LogcatOutputConfigurableProvider
import com.android.tools.idea.logcat.output.LogcatOutputSettings
import com.android.tools.idea.run.ApplicationLogListener
import com.android.tools.idea.run.DeploymentApplicationService
import com.android.tools.idea.run.ShowLogcatListener
import com.android.tools.idea.run.tasks.ConnectJavaDebuggerTask
import com.google.common.util.concurrent.Uninterruptibles
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowId
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

/**
 * Returns client with appId in [appIds] and [ClientData.DebuggerStatus.WAITING], otherwise throws [ExecutionException].
 */
@WorkerThread
@Throws(ExecutionException::class)
internal fun waitForClientReadyForDebug(device: IDevice,
                                        appIds: Collection<String>,
                                        pollTimeoutSeconds: Long = 15,
                                        indicator: ProgressIndicator?): Client {
  indicator?.text = "Waiting for processes ${appIds.joinToString()}"
  Logger.getInstance("waitForClientReadyForDebug").info("Waiting for clients $appIds for $pollTimeoutSeconds seconds")

  val startTimeMillis = System.currentTimeMillis()
  while ((System.currentTimeMillis() - startTimeMillis) <= TimeUnit.SECONDS.toMillis(pollTimeoutSeconds)) {
    indicator?.checkCanceled()
    if (!device.isOnline) {
      throw ExecutionException("Device is offline")
    }
    // Multiple ids can be in the case of instrumented test with orchestrator.
    // [TODO] pass only one appId.
    for (appId in appIds) {
      val client = getClientWithAppId(device, appId)
      if (client != null) {
        return client
      }
    }
    Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS)
  }
  throw ExecutionException("Processes ${appIds.joinToString()} are not found. Aborting session.")
}

private fun getClientWithAppId(device: IDevice, appId: String): Client? {
  val clients = DeploymentApplicationService.instance.findClient(device, appId)
  if (clients.isNotEmpty()) {
    Logger.getInstance("waitForClientReadyForDebug").info("Found process $appId")
    if (clients.size > 1) {
      Logger.getInstance("waitForClientReadyForDebug").info("Multiple clients with same application ID: $appId")
    }
    // Even though multiple processes may be related to a particular application ID, we'll only connect to the first one
    // in the list since the debugger is set up to only connect to at most one process.
    // TODO b/122613825: improve support for connecting to multiple processes with the same application ID.
    // This requires this task to wait for potentially multiple Clients before returning.
    val client = clients[0]
    when (client.clientData.debuggerConnectionStatus) {
      ClientData.DebuggerStatus.WAITING -> {
        return client
      }

      ClientData.DebuggerStatus.ERROR -> {
        val message = String.format(Locale.US,
                                    "Debug port (%1\$d) is busy, make sure there is no other active debug connection to the same application",
                                    client.debuggerListenPort)
        throw ExecutionException(message)
      }

      ClientData.DebuggerStatus.ATTACHED -> {
        throw ExecutionException("A debugger is already attached")
      }

      ClientData.DebuggerStatus.DEFAULT -> {
        return null
      }
    }
  }
  return null
}

/**
 * Shows [ExecutionException] in Debug Tool Window.
 */
internal fun showError(project: Project, e: ExecutionException, sessionName: String) {
  ExecutionUtil.handleExecutionError(project, ToolWindowId.DEBUG, e,
                                     ExecutionBundle.message("error.running.configuration.message", sessionName),
                                     e.message, Function.identity(), null)
}

internal fun captureLogcatOutputToProcessHandler(client: Client, consoleView: ConsoleView, debugProcessHandler: ProcessHandler) {
  if (!StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.get()) {
    val device = client.device
    consoleView.printHyperlink(ShowLogcatListener.getShowLogcatLinkText(device)) {
      it.messageBus.syncPublisher(ShowLogcatListener.TOPIC).showLogcat(device, client.clientData.clientDescription)
    }
    return
  }
  if (!LogcatOutputSettings.getInstance().isDebugOutputEnabled) {
    return
  }
  val device = client.device
  val logListener: AndroidLogcatService.LogcatListener = MyLogcatListener(client, debugProcessHandler)
  Logger.getInstance(
    ConnectJavaDebuggerTask::class.java).info(String.format("captureLogcatOutput(\"%s\")", device.name))
  AndroidLogcatService.getInstance().addListener(device, logListener, true)

  // Remove listener when process is terminated
  debugProcessHandler.addProcessListener(object : ProcessAdapter() {
    override fun processTerminated(event: ProcessEvent) {
      Logger.getInstance(ConnectJavaDebuggerTask::class.java)
        .info(String.format("captureLogcatOutput(\"%s\"): remove listener", device.name))
      AndroidLogcatService.getInstance().removeListener(device, logListener)
    }
  })
}

private class MyLogcatListener(client: Client, debugProcessHandler: ProcessHandler) : ApplicationLogListener(
  client.clientData.clientDescription!!, client.clientData.pid) {
  private val myFormatter: AndroidLogcatFormatter
  private val myIsFirstMessage: AtomicBoolean
  private val myDebugProcessHandler: ProcessHandler

  init {
    // noinspection ConstantConditions
    myFormatter = AndroidLogcatFormatter(ZoneId.systemDefault(), AndroidLogcatPreferences())
    myIsFirstMessage = AtomicBoolean(true)
    myDebugProcessHandler = debugProcessHandler
  }

  override fun formatLogLine(line: LogCatMessage): String {
    return myFormatter.formatMessage(SIMPLE_FORMAT, line.header, line.message)
  }

  override fun notifyTextAvailable(message: String, key: Key<*>) {
    if (myIsFirstMessage.compareAndSet(true, false)) {
      myDebugProcessHandler.notifyTextAvailable(LogcatOutputConfigurableProvider.BANNER_MESSAGE, ProcessOutputTypes.STDOUT)
    }
    myDebugProcessHandler.notifyTextAvailable(message, key)
  }

  companion object {
    private val SIMPLE_FORMAT = LogcatHeaderFormat(LogcatHeaderFormat.TimestampFormat.NONE, false, false, true)
  }
}