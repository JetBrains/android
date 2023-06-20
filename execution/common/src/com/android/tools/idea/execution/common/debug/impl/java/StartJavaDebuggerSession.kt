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
package com.android.tools.idea.execution.common.debug.impl.java

import com.android.ddmlib.Client
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.execution.ExecutionException
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.catchError


fun startAndroidJavaDebuggerSession(
  project: Project,
  client: Client,
  consoleViewToReuse: ConsoleView?,
  detachIsDefault: Boolean
): AsyncPromise<DebuggerSession> {

  val sessionName = "Android Java Debugger (pid: ${client.clientData.pid}, debug port: ${client.debuggerListenPort})"

  val promise = AsyncPromise<DebuggerSession>()

  runInEdt {
    promise.catchError {
      val debugEnvironment = AndroidJavaDebugEnvironment(
        project,
        client,
        sessionName,
        consoleViewToReuse,
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
