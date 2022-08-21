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
import com.android.tools.idea.run.AndroidSessionInfo
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.catchError

/**
 * Starts a new Java debugging session for given [Client].
 * Use this method only if debugging is started by using standard 'Debug' action i.e. this method is called from
 * [ProgramRunner.execute](ExecutionEnvironment) method. Otherwise, use [attachDebuggerAndShowTab] method.
 * It's a replacement for ConnectJavaDebuggerTask.
 *
 * This method will be moved inside AndroidJavaDebugger, when all debuggers detached from RunConfigurations.
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
  return attachDebugger(project, client, executionEnvironment) {
    getDebugProcessStarter(project, client, consoleViewToReuse, onDebugProcessStarted, onDebugProcessDestroyed, false)
  }
}

internal fun getDebugProcessStarter(
  project: Project,
  client: Client,
  consoleViewToReuse: ConsoleView?,
  onDebugProcessStarted: (() -> Unit)?,
  onDebugProcessDestroyed: (IDevice) -> Unit,
  detachIsDefault: Boolean,
): Promise<XDebugProcessStarter> {
  return startAndroidJavaDebuggerSession(project, client, consoleViewToReuse, onDebugProcessStarted,
                                         onDebugProcessDestroyed, detachIsDefault)
    .then { debuggerSession ->
      return@then object : XDebugProcessStarter() {
        override fun start(session: XDebugSession): XDebugProcess {
          return JavaDebugProcess.create(session, debuggerSession)
        }
      }
    }
}

fun startAndroidJavaDebuggerSession(
  project: Project,
  client: Client,
  consoleViewToReuse: ConsoleView?,
  onDebugProcessStarted: (() -> Unit)?,
  onDebugProcessDestroyed: (IDevice) -> Unit,
  detachIsDefault: Boolean
): AsyncPromise<DebuggerSession> {
  val promise = AsyncPromise<DebuggerSession>()

  val sessionName = "Android Java Debugger (pid: ${client.clientData.pid}, debug port: ${client.debuggerListenPort})"

  runInEdt {
    promise.catchError {
      val debugEnvironment = AndroidJavaDebugEnvironment(
        project,
        client,
        sessionName,
        consoleViewToReuse,
        onDebugProcessStarted,
        onDebugProcessDestroyed,
        detachIsDefault
      )

      val debuggerSession = DebuggerManagerEx.getInstanceEx(project).attachVirtualMachine(debugEnvironment)
                            ?: throw ExecutionException("Unable to start debugger session")

      debuggerSession.process.processHandler.putUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL, client.device.version)
      promise.setResult(debuggerSession)
    }
  }
  return promise
}
