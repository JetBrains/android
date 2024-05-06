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
package com.android.tools.idea.execution.common.debug

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.debug.utils.waitForClientReadyForDebug
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.execution.common.stats.track
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider.Companion.getApplicationProjectContext
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import icons.StudioIcons
import kotlinx.coroutines.withContext

object DebugSessionStarter {

  private val LOG = Logger.getInstance(DebugSessionStarter::class.java)

  private val START_DEBUGGER_SESSION = "startDebuggerSession"
  private val START_REATTACHING_DEBUGGER_SESSION = "startReattachingDebuggerSession"

  /**
   * Starts a new debugging session for given [Client].
   * Use this method only if debugging is started by using 'Debug' on configuration, otherwise use [AndroidDebugger.attachToClient]
   */
  suspend fun <S : AndroidDebuggerState> attachDebuggerToStartedProcess(
    device: IDevice,
    applicationContext: ApplicationProjectContext,
    environment: ExecutionEnvironment,
    androidDebugger: AndroidDebugger<S>,
    androidDebuggerState: S,
    destroyRunningProcess: (IDevice) -> Unit,
    indicator: ProgressIndicator,
    consoleView: ConsoleView? = null,
    timeout: Long = 15
  ): XDebugSessionImpl = RunStats.from(environment).track(START_DEBUGGER_SESSION) {
    val client = waitForClientReadyForDebug(device, listOf(applicationContext.applicationId), timeout, indicator)

    val debugProcessStarter = androidDebugger.getDebugProcessStarterForNewProcess(
      environment.project,
      client,
      applicationContext,
      androidDebuggerState,
      consoleView
    )
    val session = withContext(uiThread) {
      indicator.text = "Attaching debugger"
      XDebuggerManager.getInstance(environment.project).startSession(environment, debugProcessStarter) as XDebugSessionImpl
    }

    val debugProcessHandler = session.debugProcess.processHandler
    debugProcessHandler.startNotify()
    debugProcessHandler.addProcessListener(object : ProcessAdapter() {
      override fun processTerminated(event: ProcessEvent) {
        executeOnPooledThread { destroyRunningProcess(device) }
        super.processTerminated(event)
      }
    })
    AndroidSessionInfo.create(debugProcessHandler, listOf(device), applicationContext.applicationId)
    session
  }

  suspend fun <S : AndroidDebuggerState> attachReattachingDebuggerToStartedProcess(
    device: IDevice,
    applicationContext: ApplicationProjectContext,
    masterProcessName: String,
    environment: ExecutionEnvironment,
    androidDebugger: AndroidDebugger<S>,
    androidDebuggerState: S,
    destroyRunningProcess: (IDevice) -> Unit,
    indicator: ProgressIndicator,
    consoleView: ConsoleView? = null,
    timeout: Long = 300
  ): XDebugSessionImpl {
    val masterProcessHandler = AndroidProcessHandler(
      masterProcessName,
      finishAndroidProcessCallback = destroyRunningProcess
    )
    masterProcessHandler.addTargetDevice(device)
    return attachReattachingDebuggerToStartedProcess(
      device, applicationContext, masterProcessHandler, environment, androidDebugger,
      androidDebuggerState, indicator, consoleView, timeout
    )
  }

  /**
   * Wires up adb listeners to automatically reconnect the debugger for each test. This is necessary when
   * using instrumentation runners that kill the instrumentation process between each test, disconnecting
   * the debugger. We listen for the start of a new test, waiting for a debugger, and reconnect.
   */
  suspend fun <S : AndroidDebuggerState> attachReattachingDebuggerToStartedProcess(
    device: IDevice,
    applicationContext: ApplicationProjectContext,
    masterProcessHandler: ProcessHandler,
    environment: ExecutionEnvironment,
    androidDebugger: AndroidDebugger<S>,
    androidDebuggerState: S,
    indicator: ProgressIndicator,
    consoleView: ConsoleView? = null,
    timeout: Long = 300
  ): XDebugSessionImpl = RunStats.from(environment).track(START_REATTACHING_DEBUGGER_SESSION) {
    val client = waitForClientReadyForDebug(device, listOf(applicationContext.applicationId), timeout, indicator)
    val debugProcessStarter = androidDebugger.getDebugProcessStarterForNewProcess(
      environment.project,
      client,
      applicationContext,
      androidDebuggerState,
      consoleView
    )
    indicator.text = "Attaching debugger"
    val reattachingProcessHandler = ReattachingProcessHandler(masterProcessHandler)

    val reattachingListener = ReattachingDebuggerListener(
      environment.project, masterProcessHandler, applicationContext,
      androidDebugger, androidDebuggerState, consoleView, environment,
      reattachingProcessHandler
    )
    reattachingListener.addProcessedClientPid(client.clientData.pid)

    LOG.info("Add reattaching listener")
    AndroidDebugBridge.addClientChangeListener(reattachingListener)

    masterProcessHandler.addProcessListener(object : ProcessAdapter() {
      override fun processTerminated(event: ProcessEvent) {
        // Stop the reattaching debug connector task as soon as the master process is terminated.
        LOG.info("Delete reattaching listener")
        AndroidDebugBridge.removeClientChangeListener(reattachingListener)
      }
    })
    masterProcessHandler.startNotify()

    LOG.info("Start first session")

    withContext(uiThread) {
      val session = XDebuggerManager.getInstance(environment.project).startSession(environment, debugProcessStarter)

      val debugProcessHandler = session.debugProcess.processHandler
      debugProcessHandler.startNotify()
      reattachingProcessHandler.subscribeOnDebugProcess(debugProcessHandler)
      session.runContentDescriptor.processHandler = reattachingProcessHandler

      AndroidSessionInfo.create(debugProcessHandler, listOf(device), applicationContext.applicationId)
      session as XDebugSessionImpl
    }
  }


  /**
   * Starts a new Debugging session for [client] and opens a tab with in Debug tool window.
   */
  @WorkerThread
  @Throws(ExecutionException::class)
  suspend fun <S : AndroidDebuggerState> attachDebuggerToClientAndShowTab(
    project: Project,
    client: Client,
    androidDebugger: AndroidDebugger<S>,
    androidDebuggerState: S
  ): XDebugSession {
    val sessionName = "${androidDebugger.displayName} (${client.clientData.pid})"
    val applicationContext = project.getProjectSystem().getApplicationProjectContext(client)
      ?: throw ExecutionException("Cannot obtain RunningApplicationContext for client: $client")
    val starter = androidDebugger.getDebugProcessStarterForExistingProcess(project, client, applicationContext, androidDebuggerState)

    val session = withContext(uiThread) {
      XDebuggerManager.getInstance(project).startSessionAndShowTab(sessionName, StudioIcons.Common.ANDROID_HEAD, null, false, starter)
    }
    val debugProcessHandler = session.debugProcess.processHandler
    AndroidSessionInfo.create(debugProcessHandler, listOf(client.device), applicationContext.applicationId)
    return session
  }
}