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
package com.android.tools.idea.run.configuration.execution

import com.android.annotations.concurrency.AnyThread
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.tools.idea.run.AndroidSessionInfo
import com.android.tools.idea.run.ApplicationTerminator
import com.android.tools.idea.run.debug.captureLogcatOutputToProcessHandler
import com.android.tools.idea.run.debug.showError
import com.android.tools.idea.run.debug.waitForClientReadyForDebug
import com.android.tools.idea.run.editor.AndroidDebugger
import com.android.tools.idea.run.editor.AndroidDebuggerState
import com.android.tools.idea.run.editor.AndroidJavaDebugger
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import icons.StudioIcons
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.catchError
import org.jetbrains.kotlin.idea.util.application.executeOnPooledThread
import com.intellij.openapi.module.Module

object DebugSessionStarter {

  /**
   * Starts a new debugging session for given [Client].
   * Use this method only if debugging is started by using 'Debug' on configuration, otherwise use [AndroidJavaDebugger.attachToClient]
   */
  @AnyThread
  fun attachDebuggerToStartedProcess(
    device: IDevice,
    appId: String,
    environment: ExecutionEnvironment,
    androidDebugger: AndroidJavaDebugger,
    androidDebuggerState: AndroidDebuggerState,
    destroyRunningProcess: (IDevice) -> Unit,
    consoleView: ConsoleView? = null,
    timeout: Long = 15
  ): Promise<XDebugSessionImpl> {
    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()
    ProgressManager.checkCanceled()
    indicator?.text = "Waiting for a client"
    val clientPromise = AsyncPromise<Client>()
    executeOnPooledThread {
      clientPromise.catchError {
        clientPromise.setResult(waitForClientReadyForDebug(device, listOf(appId), timeout))
      }
    }

    ProgressManager.checkCanceled()
    indicator?.text = "Attaching debugger"

    return clientPromise
      .thenAsync { client ->
        androidDebugger.getDebugProcessStarterForNewProcess(environment.project, client,
                                                            androidDebuggerState,
                                                            consoleView, destroyRunningProcess)
      }
      .thenAsync { debugProcessStarter ->
        val promise = AsyncPromise<XDebugSessionImpl>()

        runInEdt {
          promise.catchError {
            val session = XDebuggerManager.getInstance(environment.project).startSession(environment, debugProcessStarter)
            val debugProcessHandler = session.debugProcess.processHandler
            debugProcessHandler.startNotify()
            captureLogcatOutputToProcessHandler(clientPromise.get()!!, session.consoleView, debugProcessHandler)
            val executor = environment.executor
            AndroidSessionInfo.create(debugProcessHandler,
                                      environment.runProfile as? RunConfiguration,
                                      executor.id,
                                      environment.executionTarget)
            promise.setResult(session as XDebugSessionImpl)
          }
        }
        promise
      }
      .onError {
        executeOnPooledThread {
          try {
            destroyRunningProcess(device)
          }
          catch (e: Exception) {
            Logger.getInstance(this::class.java).warn(e)
          }
          try {
            // Terminate the process to make it ready for future debugging.
            ApplicationTerminator(device, appId).killApp()
          }
          catch (e: Exception) {
            Logger.getInstance(this::class.java).warn(e)
          }
        }
      }
  }

  /**
   * Starts a new Debugging session with [starter] and opens tab with [tabName] in Debug tool window.
   */
  @AnyThread
  fun <S : AndroidDebuggerState> attachDebuggerToClientAndShowTab(
    project: Project,
    client: Client,
    androidDebugger: AndroidDebugger<S>,
    androidDebuggerState: S
  ): Promise<XDebugSession> {

    val sessionName = "${androidDebugger.displayName} (${client.clientData.pid})"

    return androidDebugger.getDebugProcessStarterForExistingProcess(project, client, androidDebuggerState)
      .thenAsync { starter ->
        val promise = AsyncPromise<XDebugSession>()
        runInEdt {
          promise.catchError {
            val session = XDebuggerManager.getInstance(project).startSessionAndShowTab(sessionName, StudioIcons.Common.ANDROID_HEAD, null,
                                                                                       false,
                                                                                       starter)
            val debugProcessHandler = session.debugProcess.processHandler
            AndroidSessionInfo.create(debugProcessHandler,
                                      null,
                                      DefaultDebugExecutor.getDebugExecutorInstance().id,
                                      ExecutionTargetManager.getActiveTarget(project))
            promise.setResult(session)
          }
        }
        promise
      }.onError {
        if (it is ExecutionException) {
          showError(project, it, sessionName)
        }
        else {
          Logger.getInstance(this::class.java).error(it)
        }
      }
  }
}