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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.annotations.concurrency.GuardedBy
import com.android.ddmlib.Client
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorSessionMetrics
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.StatusNotificationAction
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.pipeline.adb.findClient
import com.android.tools.idea.layoutinspector.pipeline.debugger.isPausedInDebugger
import com.android.tools.idea.layoutinspector.pipeline.debugger.resumeDebugger
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.util.ListenerCollection
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotificationPanel.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import kotlin.time.Duration.Companion.seconds

@VisibleForTesting val CONNECTED_STATE = AttachErrorState.MODEL_UPDATED
@VisibleForTesting const val CONNECT_TIMEOUT_SECONDS: Long = 30L
@VisibleForTesting const val CONNECT_TIMEOUT_MESSAGE_KEY = "connect.timeout"
@VisibleForTesting const val DEBUGGER_CHECK_SECONDS: Long = 2L
@VisibleForTesting const val DEBUGGER_CHECK_MESSAGE_KEY = "debugger.paused"

class InspectorClientLaunchMonitor(
  private val project: Project,
  private val notificationModel: NotificationModel,
  private val attachErrorStateListeners: ListenerCollection<(AttachErrorState) -> Unit>,
  private val stats: SessionStatistics,
  coroutineScope: CoroutineScope,
  private val timeoutScope: CoroutineScope = coroutineScope,
  private val debuggerCheckScope: CoroutineScope = coroutineScope
) {
  private var lastUpdate: Long = 0L
  private var timeoutJob: Job? = null
  private var debuggerJob: Job? = null
  private val clientLock = Any()

  var currentProgress = AttachErrorState.UNKNOWN_ATTACH_ERROR_STATE
    private set

  // This is to make sure we never schedule a timeout check after the monitor is stopped.
  // Note: a stop() call could happen while updateProgress is being executed (on different threads).
  @GuardedBy("clientLock") private var client: InspectorClient? = null

  fun start(client: InspectorClient) {
    assert(this.client == null)
    synchronized(clientLock) { this.client = client }
    updateProgress(AttachErrorState.NOT_STARTED)
    debuggerJob = debuggerCheckScope.launch { debuggerCheck() }
    timeoutJob = timeoutScope.launch { timeoutCheck() }
  }

  val timeoutHandlerScheduled: Boolean
    @TestOnly get() = timeoutJob != null

  fun updateProgress(progress: AttachErrorState) {
    attachErrorStateListeners.forEach { it.invoke(progress) }

    if (progress <= currentProgress) {
      return
    }
    currentProgress = progress
    stats.currentProgress = progress
    if (currentProgress < CONNECTED_STATE) {
      lastUpdate = System.currentTimeMillis()
    } else {
      stopAsyncJobs()
    }
    notificationModel.removeNotification(CONNECT_TIMEOUT_MESSAGE_KEY)
    notificationModel.removeNotification(DEBUGGER_CHECK_MESSAGE_KEY)
  }

  private suspend fun debuggerCheck() {
    while (true) {
      delay(DEBUGGER_CHECK_SECONDS.seconds)

      val adb = adbClient
      val currentClient = synchronized(clientLock) { client } ?: return
      if (adb == null || !isPausedInDebugger(adb)) {
        currentClient.stats.debuggerInUse(isPaused = false)
        notificationModel.removeNotification(DEBUGGER_CHECK_MESSAGE_KEY)
      } else {
        currentClient.stats.debuggerInUse(isPaused = true)
        val resumeDebuggerAction = createResumeDebuggerAction()
        val disconnectAction = createDisconnectAction(attemptDumpViews = false)
        notificationModel.addNotification(
          DEBUGGER_CHECK_MESSAGE_KEY,
          LayoutInspectorBundle.message(DEBUGGER_CHECK_MESSAGE_KEY),
          Status.Error,
          listOf(resumeDebuggerAction, disconnectAction)
        )
        notificationModel.removeNotification(CONNECT_TIMEOUT_MESSAGE_KEY)
      }
    }
  }

  private suspend fun timeoutCheck() {
    delay(CONNECT_TIMEOUT_SECONDS.seconds)

    synchronized(clientLock) {
      if (client == null || currentProgress == CONNECTED_STATE) {
        // We are disconnected or connected: stop the timeout check
        return
      }
    }
    val adb = adbClient
    if (adb?.let { isPausedInDebugger(it) } == true) {
      // Ignore the timeout if we know we are stuck in the debugger
      return
    }
    val continueWaiting = createContinueWaitingAction()
    // Only offer option to dump views in standalone Layout Inspector
    // The embedded LI is meant to work only with live app inspection client.
    val attemptDumpViews = !LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled
    val disconnect = createDisconnectAction(attemptDumpViews = attemptDumpViews)
    notificationModel.addNotification(
      CONNECT_TIMEOUT_MESSAGE_KEY,
      LayoutInspectorBundle.message(CONNECT_TIMEOUT_MESSAGE_KEY),
      Status.Warning,
      listOf(continueWaiting, disconnect)
    )
  }

  private fun createContinueWaitingAction() =
    StatusNotificationAction("Continue Waiting") {
      notificationModel.removeNotification(CONNECT_TIMEOUT_MESSAGE_KEY)
      synchronized(clientLock) {
        if (client != null) {
          timeoutJob = timeoutScope.launch { timeoutCheck() }
        }
      }
    }

  private fun createDisconnectAction(attemptDumpViews: Boolean): StatusNotificationAction {
    val disconnectText =
      if (attemptDumpViews && client?.clientType == ClientType.APP_INSPECTION_CLIENT) "Dump Views"
      else "Disconnect"
    return StatusNotificationAction(disconnectText) {
      notificationModel.removeNotification(CONNECT_TIMEOUT_MESSAGE_KEY)
      Logger.getInstance(InspectorClientLaunchMonitor::class.java)
        .warn(
          "Client $client timed out during attach at step $currentProgress on the users request"
        )
      logAttachErrorToMetrics(AttachErrorCode.CONNECT_TIMEOUT)
      client?.disconnect()
    }
  }

  private fun createResumeDebuggerAction() =
    StatusNotificationAction("Resume Debugger") {
      notificationModel.removeNotification(DEBUGGER_CHECK_MESSAGE_KEY)
      synchronized(clientLock) {
        if (client != null) {
          adbClient?.let { resumeDebugger(it) }
        }
      }
    }

  private val adbClient: Client?
    get() = client?.process?.let { AdbUtils.getAdbFuture(project).get()?.findClient(it) }

  /** Log an attach error from the Dynamic Layout Inspector to metrics. */
  fun logAttachErrorToMetrics(errorCode: AttachErrorCode) {
    val stats = client?.stats ?: DisconnectedClient.stats
    LayoutInspectorSessionMetrics(null, client?.process, null)
      .logEvent(
        DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_ERROR,
        stats,
        currentProgress,
        errorCode
      )
  }

  fun stop() {
    synchronized(clientLock) { client = null }
    stopAsyncJobs()
  }

  private fun stopAsyncJobs() {
    timeoutJob?.cancel()
    timeoutJob = null
    debuggerJob?.cancel()
    debuggerJob = null
  }
}
