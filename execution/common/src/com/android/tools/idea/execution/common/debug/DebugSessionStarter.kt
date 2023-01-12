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
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.debug.utils.showError
import com.android.tools.idea.execution.common.debug.utils.waitForClientReadyForDebug
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import icons.StudioIcons
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object DebugSessionStarter {

  private val LOG = Logger.getInstance(DebugSessionStarter::class.java)

  /**
   * Starts a new debugging session for given [Client].
   * Use this method only if debugging is started by using 'Debug' on configuration, otherwise use [AndroidDebugger.attachToClient]
   */
  @WorkerThread
  fun <S : AndroidDebuggerState> attachDebuggerToStartedProcess(
    device: IDevice,
    appId: String,
    environment: ExecutionEnvironment,
    androidDebugger: AndroidDebugger<S>,
    androidDebuggerState: S,
    destroyRunningProcess: (IDevice) -> Unit,
    indicator: ProgressIndicator,
    consoleView: ConsoleView? = null,
    timeout: Long = 15
  ): XDebugSessionImpl = runBlockingCancellable(indicator) {
    val client = waitForClientReadyForDebug(device, listOf(appId), timeout, indicator)

    val debugProcessStarter = androidDebugger.getDebugProcessStarterForNewProcess(environment.project, client,
                                                                                  androidDebuggerState,
                                                                                  consoleView, destroyRunningProcess).blockingGet(1,
                                                                                                                                  TimeUnit.MINUTES)!!
    val session = withContext(uiThread) {
      indicator.text = "Attaching debugger"
      XDebuggerManager.getInstance(environment.project).startSession(environment, debugProcessStarter) as XDebugSessionImpl
    }

    val debugProcessHandler = session.debugProcess.processHandler
    debugProcessHandler.startNotify()
    val executor = environment.executor
    AndroidSessionInfo.create(debugProcessHandler,
                              environment.runProfile as? RunConfiguration,
                              executor.id,
                              environment.executionTarget)
    session
  }

  @WorkerThread
  fun <S : AndroidDebuggerState> attachReattachingDebuggerToStartedProcess(
    device: IDevice,
    appId: String,
    masterProcessName: String,
    environment: ExecutionEnvironment,
    androidDebugger: AndroidDebugger<S>,
    androidDebuggerState: S,
    destroyRunningProcess: (IDevice) -> Unit,
    indicator: ProgressIndicator,
    consoleView: ConsoleView? = null,
    timeout: Long = 300
  ): XDebugSessionImpl {
    val masterProcessHandler = AndroidProcessHandler(environment.project, masterProcessName)
    masterProcessHandler.addTargetDevice(device)
    return attachReattachingDebuggerToStartedProcess(device, appId, masterProcessHandler, environment, androidDebugger,
                                                     androidDebuggerState, destroyRunningProcess, indicator, consoleView, timeout)
  }

  /**
   * Wires up adb listeners to automatically reconnect the debugger for each test. This is necessary when
   * using instrumentation runners that kill the instrumentation process between each test, disconnecting
   * the debugger. We listen for the start of a new test, waiting for a debugger, and reconnect.
   */
  @WorkerThread
  fun <S : AndroidDebuggerState> attachReattachingDebuggerToStartedProcess(
    device: IDevice,
    appId: String,
    masterProcessHandler: ProcessHandler,
    environment: ExecutionEnvironment,
    androidDebugger: AndroidDebugger<S>,
    androidDebuggerState: S,
    destroyRunningProcess: (IDevice) -> Unit,
    indicator: ProgressIndicator,
    consoleView: ConsoleView? = null,
    timeout: Long = 300
  ): XDebugSessionImpl = runBlockingCancellable(indicator) {
    val client = waitForClientReadyForDebug(device, listOf(appId), timeout, indicator)
    val debugProcessStarter = androidDebugger.getDebugProcessStarterForNewProcess(environment.project, client,
                                                                                  androidDebuggerState,
                                                                                  consoleView, destroyRunningProcess).blockingGet(1,
                                                                                                                                  TimeUnit.MINUTES)!!
    indicator.text = "Attaching debugger"
    val reattachingProcessHandler = ReattachingProcessHandler(masterProcessHandler)

    val reattachingListener = ReattachingDebuggerListener(environment.project, masterProcessHandler, appId,
                                                          androidDebugger, androidDebuggerState, consoleView, environment,
                                                          reattachingProcessHandler)
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

      val executor = environment.executor
      AndroidSessionInfo.create(masterProcessHandler,
                                environment.runProfile as? RunConfiguration,
                                executor.id,
                                environment.executionTarget)
      session as XDebugSessionImpl
    }
  }


  /**
   * Starts a new Debugging session for [client] and opens a tab with in Debug tool window.
   */
  @WorkerThread
  @JvmOverloads
  fun <S : AndroidDebuggerState> attachDebuggerToClientAndShowTab(
    project: Project,
    client: Client,
    androidDebugger: AndroidDebugger<S>,
    androidDebuggerState: S,
    indicator: ProgressIndicator = EmptyProgressIndicator()
  ): XDebugSession = runBlockingCancellable(indicator) {
    val sessionName = "${androidDebugger.displayName} (${client.clientData.pid})"
    try {
      val starter = androidDebugger.getDebugProcessStarterForExistingProcess(project, client, androidDebuggerState).blockingGet(1,
                                                                                                                                TimeUnit.MINUTES)!!

      val session = withContext(uiThread) {
        XDebuggerManager.getInstance(project).startSessionAndShowTab(sessionName, StudioIcons.Common.ANDROID_HEAD, null, false, starter)
      }
      val debugProcessHandler = session.debugProcess.processHandler
      AndroidSessionInfo.create(debugProcessHandler,
                                null,
                                DefaultDebugExecutor.getDebugExecutorInstance().id,
                                ExecutionTargetManager.getActiveTarget(project))
      session
    }
    catch (e: Exception) {
      if (e is ExecutionException) {
        showError(project, e, sessionName)
      }
      else {
        LOG.error(e)
      }
      throw e
    }
  }
}