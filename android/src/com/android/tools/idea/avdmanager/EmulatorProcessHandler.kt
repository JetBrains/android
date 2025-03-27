/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager

import com.android.sdklib.internal.avd.AvdInfo
import com.intellij.execution.process.BaseOSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.io.BaseOutputReader

private const val NOTIFY_USER = "(?<notifyUser>USER_)?"
private const val SEVERITY = "(?<severity>VERBOSE|DEBUG|INFO|WARNING|ERROR|FATAL)"
private const val MESSAGE = "(?<message>.*)"
private const val TIMESTAMP = "(?<timestamp>\\d+:\\d+:\\d+\\.\\d+)"
private const val THREAD = "(?<thread>\\d+)"
private const val LOCATION = "(?<location>[\\w-]+\\.[A-Za-z]+:\\d+)"

/**
 * A process handler for an emulator process.
 */
class EmulatorProcessHandler(
  process: Process,
  commandLine: String,
  private val avd: AvdInfo
) : BaseOSProcessHandler(process, commandLine, null) {

  private val avdName = avd.displayName
  private val log = Logger.getInstance("Emulator: $avdName")

  /**
   * Matches emulator messages in the default logging format.
   *
   * Example messages:
   * ```
   * INFO         | Advertising in: /Users/janedoe/Library/Caches/TemporaryItems/avd/running/pid_82179.ini
   * INFO         | boot completed
   * INFO         | boot time 32239 ms
   * USER_WARNING | Emulator failed to start. Deleting quickboot snapshot and performing a cold boot.
   * ```
   */
  private val defaultMessagePattern = Regex("""^$NOTIFY_USER$SEVERITY\s+\| $MESSAGE""")

  /**
   * Matches emulator messages in the verbose logging format that is enabled by the `-debug-log` command line flag.
   *
   * Example messages:
   * ```
   * 13:06:17.219774 123145368940544 VERBOSE proxy_setup.cpp:25                 | Not using any http proxy
   * 13:06:17.219868 123145368940544 VERBOSE hw-fingerprint.c:93                | fingerprint qemud listen service initialized
   * 13:06:17.220560 123145368940544 INFO    GrpcServices.cpp:315               | Started GRPC server at 127.0.0.1:8554, security: Local
   * ```
   */
  private val verboseMessagePattern = Regex("""^$TIMESTAMP $THREAD\s+$NOTIFY_USER$SEVERITY\s+$LOCATION\s+\| $MESSAGE""")

  private val isEmbedded = commandLine.contains(" -qt-hide-window ")
  private val messageBus = ApplicationManager.getApplication().messageBus
  private val ownedRunningEmulators = service<LaunchedAvdTracker>()

  init {
    addProcessListener(EmulatorProcessListener())
    ProcessTerminatedListener.attach(this)
  }

  private fun notifyListeners(avd: AvdInfo, severity: EmulatorLogListener.Severity, notifyUser: Boolean, message: String) {
    try {
      messageBus.syncPublisher(EmulatorLogListener.TOPIC).messageLogged(avd, severity, notifyUser, message)
    }
    catch (_: AlreadyDisposedException) {
    }
  }

  override fun readerOptions(): BaseOutputReader.Options =
      BaseOutputReader.Options.forMostlySilentProcess()

  private inner class EmulatorProcessListener : ProcessListener {

    override fun startNotified(event: ProcessEvent) {
      ownedRunningEmulators.started(avd.id, process.toHandle())
    }

    override fun processTerminated(event: ProcessEvent) {
      ownedRunningEmulators.terminated(avd.id, process.toHandle())
    }

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
      val text = event.text?.trim { it <= ' ' }
      if (text != null) {
        parseAndLogMessage(text)
      }

      if (ProcessOutputType.SYSTEM == outputType && isProcessTerminated) {
        val exitCode = exitCode
        if (exitCode != null && exitCode != 0) {
          // Don't use error level because we don't want Studio crash reports for this; the emulator's
          // crash reporter can provide better detail
          log.warn("Emulator terminated with exit code $exitCode")
        }
      }
    }

    private fun parseAndLogMessage(text: String) {
      val notifyUser: Boolean
      val severity: EmulatorLogListener.Severity
      val message: String
      var groups = defaultMessagePattern.matchEntire(text)?.groups as MatchNamedGroupCollection?
      if (groups != null) {
        notifyUser = groups["notifyUser"] != null
        severity = mapSeverity(groups["severity"]!!.value)
        message = groups["message"]!!.value
      }
      else {
        groups = verboseMessagePattern.matchEntire(text)?.groups as MatchNamedGroupCollection?
        if (groups != null) {
          notifyUser = groups["notifyUser"] != null
          severity = mapSeverity(groups["severity"]!!.value)
          message = groups["timestamp"]!!.value + ' ' + groups["thread"]!!.value + ' ' + groups["location"]!!.value + ' ' +
                    groups["message"]!!.value
        }
        else {
          // Legacy unstructured message.
          notifyUser = false
          severity = EmulatorLogListener.Severity.INFO
          message = text
        }
      }
      when (severity) {
        EmulatorLogListener.Severity.VERBOSE -> log.trace(message)
        EmulatorLogListener.Severity.DEBUG -> log.debug(message)
        EmulatorLogListener.Severity.INFO -> log.info(message)
        // Emulator errors are logged as warning to prevent them from appearing in Studio crash reports.
        // Such crash reports would not be actionable due to insufficient information.
        EmulatorLogListener.Severity.WARNING, EmulatorLogListener.Severity.ERROR -> log.warn(message)
        EmulatorLogListener.Severity.FATAL -> {
          log.warn(message)
          notify("Emulator: $avdName", message, NotificationType.ERROR)
        }
      }
      notifyListeners(avd, severity, notifyUser, message)
    }

    private fun notify(title: String, content: String, @Suppress("SameParameterValue") notificationType: NotificationType) {
      val notificationGroup = if (isEmbedded) "Running Devices Messages" else "Device Manager Messages"
      NotificationGroup.findRegisteredGroup(notificationGroup)
        ?.createNotification(title, content, notificationType)
        ?.notify(null)
    }
  }
}

private fun mapSeverity(severity: String?): EmulatorLogListener.Severity {
  return when (severity) {
    "VERBOSE" -> EmulatorLogListener.Severity.VERBOSE
    "DEBUG" -> EmulatorLogListener.Severity.DEBUG
    "INFO" -> EmulatorLogListener.Severity.INFO
    "WARNING" -> EmulatorLogListener.Severity.WARNING
    "ERROR" -> EmulatorLogListener.Severity.ERROR
    "FATAL" -> EmulatorLogListener.Severity.FATAL
    else -> EmulatorLogListener.Severity.INFO
  }
}
