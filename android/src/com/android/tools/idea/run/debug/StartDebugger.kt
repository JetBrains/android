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
import com.android.tools.idea.run.AndroidSessionInfo
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import icons.StudioIcons
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.catchError

/**
 * Starts a new Debugging session with [getDebugProcessStarter].
 * Use this method only if debugging is started by using standard 'Debug' action i.e. this methods is called from
 * [ProgramRunner.execute] method. Otherwise, use [attachDebuggerAndShowTab] method.
 **/
@AnyThread
fun attachDebugger(
  project: Project,
  client: Client,
  executionEnvironment: ExecutionEnvironment,
  getDebugProcessStarter: () -> Promise<XDebugProcessStarter>,
): Promise<XDebugSessionImpl> {
  return getDebugProcessStarter()
    .thenAsync { starter ->
      val promise = AsyncPromise<XDebugSessionImpl>()
      runInEdt {
        promise.catchError {
          val session = XDebuggerManager.getInstance(project).startSession(executionEnvironment, starter)
          val debugProcessHandler = session.debugProcess.processHandler
          debugProcessHandler.startNotify()
          captureLogcatOutputToProcessHandler(client, debugProcessHandler)
          val executor = executionEnvironment.executor
          AndroidSessionInfo.create(debugProcessHandler,
                                    executionEnvironment.runProfile as? RunConfiguration,
                                    executor.id,
                                    executionEnvironment.executionTarget)
          promise.setResult(session as XDebugSessionImpl)
        }
      }
      promise
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
 * Starts a new Debugging session with [getDebugProcessStarter]  and opens tab with [tabName] in Debug tool window.
 */
@AnyThread
fun attachDebuggerAndShowTab(
  project: Project,
  tabName: String,
  getDebugProcessStarter: () -> Promise<XDebugProcessStarter>,
): AsyncPromise<XDebugSession> {

  return getDebugProcessStarter()
    .thenAsync { starter ->
      val promise = AsyncPromise<XDebugSession>()
      runInEdt {
        promise.catchError {
          val session = XDebuggerManager.getInstance(project).startSessionAndShowTab(tabName, StudioIcons.Common.ANDROID_HEAD, null, false,
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
    }
    .onError {
      if (it is ExecutionException) {
        showError(project, it, tabName)
      }
      else {
        Logger.getInstance("attachJavaDebuggerToClientAndShowTab").error(it)
      }
    } as AsyncPromise
}