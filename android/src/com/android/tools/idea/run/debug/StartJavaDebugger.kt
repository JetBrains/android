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

import com.android.annotations.concurrency.AnyThread
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.tools.idea.flags.StudioFlags
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import icons.StudioIcons.Common.ANDROID_HEAD
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.catchError

/**
 * Starts a new Java debugging session for given [Client].
 * Use this method only if debugging is started by using standard 'Debug' action i.e. this methods is called from
 * [ProgramRunner.execute](ExecutionEnvironment) method. Otherwise, use [attachJavaDebuggerToClientAndShowTab] method.
 * It's a replacement for ConnectJavaDebuggerTask.
 *
 * This method will be moved inside AndroidJavaDebugger, when [StudioFlags.NEW_EXECUTION_FLOW_FOR_JAVA_DEBUGGER] is enabled by default in stable.
 */
@AnyThread
fun attachJavaDebuggerToClient(
  project: Project,
  client: Client,
  executionEnvironment: ExecutionEnvironment,
  consoleViewToReuse: ConsoleView? = null,
  onDebugProcessStarted: (() -> Unit)? = null,
  onDebugProcessDestroyed: (IDevice) -> Unit,
): Promise<XDebugSessionImpl> {
  return getDebugProcessStarter(project, client, consoleViewToReuse, onDebugProcessStarted, onDebugProcessDestroyed, false)
    .then { starter ->
      val session = XDebuggerManager.getInstance(project).startSession(executionEnvironment, starter)
      session.debugProcess.processHandler.startNotify()
      session as XDebugSessionImpl
    }
    // TODO: delete error handling when [StudioFlags.NEW_EXECUTION_FLOW_ENABLED] is enabled
    .onError {
      if (it is ExecutionException) {
        showError(project, it, executionEnvironment.runProfile.name)
      }
      else {
        Logger.getInstance("attachJavaDebuggerToClient").error(it)
      }
    }
}

/**
 * Starts a new Java debugging session for given [Client] and opens 'Debug' tool window.
 * If debugging is started via standard 'Debug' action i.e. called from [ProgramRunner.execute] method use
 * [attachJavaDebuggerToClient].
 *
 * It's a replacement for AndroidJavaDebugger.attachToClient.
 *
 * This method will be moved inside AndroidJavaDebugger, when [StudioFlags.NEW_EXECUTION_FLOW_FOR_JAVA_DEBUGGER] is enabled by default in stable.
 */
@AnyThread
fun attachJavaDebuggerToClientAndShowTab(
  project: Project,
  client: Client
): AsyncPromise<XDebugSession> {
  val sessionName = "Android Debugger (pid: ${client.clientData.pid}, debug port: ${client.debuggerListenPort})"

  return getDebugProcessStarter(project, client, null, null,
                                { device -> device.forceStop(client.clientData.clientDescription) }, true)
    .thenAsync { starter ->
      val promise = AsyncPromise<XDebugSession>()
      runInEdt {
        promise.catchError {
          promise.setResult(XDebuggerManager.getInstance(project).startSessionAndShowTab(sessionName, ANDROID_HEAD, null, false, starter))
        }
      }
      promise
    }
    .onError {
      if (it is ExecutionException) {
        showError(project, it, sessionName)
      }
      else {
        Logger.getInstance("attachJavaDebuggerToClientAndShowTab").error(it)
      }
    } as AsyncPromise
}

private fun getDebugProcessStarter(
  project: Project,
  client: Client,
  consoleViewToReuse: ConsoleView?,
  onDebugProcessStarted: (() -> Unit)?,
  onDebugProcessDestroyed: (IDevice) -> Unit,
  detachIsDefault: Boolean,
): AsyncPromise<XDebugProcessStarter> {
  val sessionName = "Android Java Debugger (pid: ${client.clientData.pid}, debug port: ${client.debuggerListenPort})"

  val debugEnvironment = AndroidJavaDebugEnvironment(
    project,
    client,
    sessionName,
    consoleViewToReuse,
    onDebugProcessStarted,
    onDebugProcessDestroyed,
    detachIsDefault
  )

  val promise = AsyncPromise<XDebugProcessStarter>()

  runInEdt {
    promise.catchError {
      val debuggerSession = DebuggerManagerEx.getInstanceEx(project).attachVirtualMachine(debugEnvironment)
                            ?: throw ExecutionException("Unable to start debugger session")

      promise.setResult(object : XDebugProcessStarter() {
        override fun start(session: XDebugSession): XDebugProcess {
          return JavaDebugProcess.create(session, debuggerSession)
        }
      })
    }
  }
  return promise
}
