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
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.pipeline.adb.findClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.errorCode
import com.android.tools.idea.layoutinspector.pipeline.debugger.isPausedInDebugger
import com.android.tools.idea.layoutinspector.pipeline.debugger.resumeDebugger
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.util.ListenerCollection
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@VisibleForTesting val CONNECTED_STATE = AttachErrorState.MODEL_UPDATED
@VisibleForTesting const val CONNECT_TIMEOUT_SECONDS: Long = 30L
@VisibleForTesting const val CONNECT_TIMEOUT_MESSAGE_KEY = "connect.timeout"
@VisibleForTesting const val DEBUGGER_CHECK_SECONDS: Long = 2L
@VisibleForTesting const val DEBUGGER_CHECK_MESSAGE_KEY = "debugger.paused"

class InspectorClientLaunchMonitor(
  private val project: Project,
  private val attachErrorStateListeners: ListenerCollection<(AttachErrorState) -> Unit>,
  @TestOnly private val executorService: ScheduledExecutorService = AppExecutorUtil.getAppScheduledExecutorService()
) {
  private var lastUpdate: Long = 0L
  private var currentProgress = AttachErrorState.UNKNOWN_ATTACH_ERROR_STATE
  private var timeoutFuture: ScheduledFuture<*>? = null
  private var debuggerFuture:  ScheduledFuture<*>? = null
  private val clientLock = Any()

  // This is to make sure we never schedule a timeout check after the monitor is stopped.
  // Note: a stop() call could happen while updateProgress is being executed (on different threads).
  @GuardedBy("clientLock")
  private var client: InspectorClient? = null

  fun start(client: InspectorClient) {
    assert(this.client == null)
    synchronized(clientLock) {
      this.client = client
    }
    updateProgress(AttachErrorState.NOT_STARTED)
  }

  val timeoutHandlerScheduled: Boolean
    @TestOnly
    get() = timeoutFuture != null

  fun updateProgress(progress: AttachErrorState) {
    attachErrorStateListeners.forEach { it.invoke(progress) }

    if (progress <= currentProgress) {
      return
    }
    timeoutFuture?.cancel(true)
    debuggerFuture?.cancel(true)
    currentProgress = progress
    if (currentProgress < CONNECTED_STATE) {
      lastUpdate = System.currentTimeMillis()
      synchronized(clientLock) {
        if (client != null) {
          timeoutFuture = executorService.schedule(::handleTimeout, CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
          debuggerFuture = executorService.schedule(::handleDebuggerCheck, DEBUGGER_CHECK_SECONDS, TimeUnit.SECONDS)
        }
      }
    }
    val banner = InspectorBannerService.getInstance(project)
    banner?.removeNotification(LayoutInspectorBundle.message(CONNECT_TIMEOUT_MESSAGE_KEY))
    banner?.removeNotification(LayoutInspectorBundle.message(DEBUGGER_CHECK_MESSAGE_KEY))
  }

  fun onFailure(t: Throwable) {
    // CancellationExceptions will be forwarded to LayoutInspector.logError no need to handle it here.
    if (t !is CancellationException) {
      logAttachError(t.errorCode.code)
    }
    stop()
  }

  private fun handleDebuggerCheck() {
    val banner = InspectorBannerService.getInstance(project)
    val currentClient = adbClient ?: return
    if (!isPausedInDebugger(currentClient)) {
      banner?.removeNotification(LayoutInspectorBundle.message(DEBUGGER_CHECK_MESSAGE_KEY))
      debuggerFuture = executorService.schedule(::handleDebuggerCheck, DEBUGGER_CHECK_SECONDS, TimeUnit.SECONDS)
      return
    }
    // Cancel the timeout check since we now know that the attach delay is caused by a debugging session:
    timeoutFuture?.cancel(true)
    val resumeDebugger = object : AnAction("Resume Debugger") {
      override fun actionPerformed(event: AnActionEvent) {
        banner?.removeNotification(LayoutInspectorBundle.message(DEBUGGER_CHECK_MESSAGE_KEY))
        synchronized(clientLock) {
          if (client != null) {
            adbClient?.let { resumeDebugger(it) }
            debuggerFuture = executorService.schedule(::handleDebuggerCheck, DEBUGGER_CHECK_SECONDS, TimeUnit.SECONDS)
          }
        }
      }
    }
    val disconnect = createDisconnectAction(attemptDumpViews = false) // The legacy inspector cannot get information either...
    banner?.addNotification(LayoutInspectorBundle.message(DEBUGGER_CHECK_MESSAGE_KEY), listOf(resumeDebugger, disconnect))
    banner?.removeNotification(LayoutInspectorBundle.message(CONNECT_TIMEOUT_MESSAGE_KEY))
  }

  private fun handleTimeout() {
    if (adbClient?.let { isPausedInDebugger(it) } == true) {
      // Ignore the timeout if we know we are stuck in the debugger
      return
    }
    // Allow the user to wait as long as they want in case it takes a long time to connect.
    // This action simply removes the banner and schedules another check after CONNECT_TIMEOUT_SECONDS.
    val banner = InspectorBannerService.getInstance(project)
    val continueWaiting = object : AnAction("Continue Waiting") {
      override fun actionPerformed(event: AnActionEvent) {
        banner?.removeNotification(LayoutInspectorBundle.message(CONNECT_TIMEOUT_MESSAGE_KEY))
        synchronized(clientLock) {
          if (client != null) {
            timeoutFuture = executorService.schedule(::handleTimeout, CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
          }
        }
      }
    }
    val disconnect = createDisconnectAction(attemptDumpViews = true)
    banner?.addNotification(LayoutInspectorBundle.message(CONNECT_TIMEOUT_MESSAGE_KEY), listOf(continueWaiting, disconnect))
  }

  private fun createDisconnectAction(attemptDumpViews: Boolean): AnAction {
    val disconnectText = if (attemptDumpViews && client?.clientType == ClientType.APP_INSPECTION_CLIENT) "Dump Views" else "Disconnect"
    return object : AnAction(disconnectText) {
      override fun actionPerformed(event: AnActionEvent) {
        InspectorBannerService.getInstance(project)?.removeNotification(LayoutInspectorBundle.message(CONNECT_TIMEOUT_MESSAGE_KEY))
        Logger.getInstance(InspectorClientLaunchMonitor::class.java).warn(
          "Client $client timed out during attach at step $currentProgress on the users request")
        logAttachError(AttachErrorCode.CONNECT_TIMEOUT)
        client?.disconnect()
      }
    }
  }

  private val adbClient: Client?
    get() = client?.process?.let { AdbUtils.getAdbFuture(project).get()?.findClient(it) }

  private fun logAttachError(errorCode: AttachErrorCode) {
    val stats = client?.stats ?: DisconnectedClient.stats
    LayoutInspectorSessionMetrics(null, client?.process, null).logEvent(
      DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_ERROR, stats, currentProgress, errorCode)
  }

  fun stop() {
    synchronized(clientLock) {
      client = null
    }
    timeoutFuture?.cancel(true)
    timeoutFuture = null
    debuggerFuture?.cancel(true)
    debuggerFuture = null
  }
}