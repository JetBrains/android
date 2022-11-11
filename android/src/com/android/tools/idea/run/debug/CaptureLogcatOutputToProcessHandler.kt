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

import com.android.ddmlib.Client
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.AndroidLogcatFormatter
import com.android.tools.idea.logcat.AndroidLogcatPreferences
import com.android.tools.idea.logcat.AndroidLogcatService
import com.android.tools.idea.logcat.LogcatHeaderFormat
import com.android.tools.idea.logcat.output.LogcatOutputConfigurableProvider
import com.android.tools.idea.logcat.output.LogcatOutputSettings
import com.android.tools.idea.run.ApplicationLogListener
import com.android.tools.idea.run.ShowLogcatListener
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

internal fun captureLogcatOutputToProcessHandler(client: Client, consoleView: ConsoleView, debugProcessHandler: ProcessHandler) {
  val LOG = Logger.getInstance("captureLogcatOutputToProcessHandler")

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
  LOG.info("captureLogcatOutput(\"${device.name}\")")
  AndroidLogcatService.getInstance().addListener(device, logListener, true)

  // Remove listener when process is terminated
  debugProcessHandler.addProcessListener(object : ProcessAdapter() {
    override fun processTerminated(event: ProcessEvent) {
      LOG.info("captureLogcatOutput(\"${device.name}\"): remove listener")
      AndroidLogcatService.getInstance().removeListener(device, logListener)
    }
  })
}

private val SIMPLE_FORMAT = LogcatHeaderFormat(LogcatHeaderFormat.TimestampFormat.NONE, showProcessId = false, showPackageName = false,
                                               showTag = true)

private class MyLogcatListener(client: Client, private val debugProcessHandler: ProcessHandler) :
  ApplicationLogListener(client.clientData.clientDescription!!, client.clientData.pid) {
  private val formatter: AndroidLogcatFormatter = AndroidLogcatFormatter(ZoneId.systemDefault(), AndroidLogcatPreferences())
  private val isFirstMessage: AtomicBoolean = AtomicBoolean(true)

  override fun formatLogLine(line: LogCatMessage): String {
    return formatter.formatMessage(SIMPLE_FORMAT, line.header, line.message)
  }

  override fun notifyTextAvailable(message: String, key: Key<*>) {
    if (isFirstMessage.compareAndSet(true, false)) {
      debugProcessHandler.notifyTextAvailable(LogcatOutputConfigurableProvider.BANNER_MESSAGE, ProcessOutputTypes.STDOUT)
    }
    debugProcessHandler.notifyTextAvailable(message, key)
  }
}