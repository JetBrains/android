/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.execution.common.debug.utils

import com.android.ddmlib.Client
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.execution.common.debug.AndroidDebugger
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState
import com.android.tools.idea.execution.common.debug.DebugSessionStarter.attachDebuggerToClientAndShowTab
import com.android.tools.idea.execution.common.debug.RunConfigurationWithDebugger
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.xdebugger.XDebugSession
import kotlinx.coroutines.withContext

object AndroidConnectDebugger {
  @JvmStatic
  fun <S: AndroidDebuggerState> closeOldSessionAndRun(
    project: Project,
    androidDebugger: AndroidDebugger<S>,
    client: Client,
    configuration: RunConfigurationWithDebugger?
  ) {
    terminateRunSessions(project, client)
    val state: S = configuration?.androidDebuggerContext?.getAndroidDebuggerState() ?: androidDebugger.createState()
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Attaching debugger") {
      override fun run(indicator: ProgressIndicator) {
        runBlockingCancellable {
          try {
            val existingSession = androidDebugger.getExistingDebugSession(project, client)

            if (existingSession != null) {
              if (activateDebugSessionWindow(project, existingSession)) {
                return@runBlockingCancellable
              }
              existingSession.debugProcess.processHandler.detachProcess()
            }
            attachDebuggerToClientAndShowTab(project, client, androidDebugger, state)
          }
          catch (e: ExecutionException) {
            showError(project, e, "Attach debug to process")
          }
        }
      }
    })
  }

  // Disconnect any active run sessions to the same client
  private fun terminateRunSessions(project: Project, selectedClient: Client) {
    val pid = selectedClient.getClientData().pid

    // find if there are any active run sessions to the same client, and terminate them if so
    for (handler in ExecutionManager.getInstance(project).getRunningProcesses()) {
      if (handler is AndroidProcessHandler) {
        val client = handler.getClient(selectedClient.getDevice())
        if (client != null && client.getClientData().pid == pid) {
          handler.notifyTextAvailable("Disconnecting run session: a new debug session will be established.\n", ProcessOutputTypes.STDOUT)
          handler.detachProcess()
          break
        }
      }
    }
  }

  private suspend fun activateDebugSessionWindow(project: Project, session: XDebugSession): Boolean {
    val descriptor = session.runContentDescriptor
    val processHandler = descriptor.processHandler
    val content = descriptor.attachedContent
    if (processHandler == null || content == null || processHandler.isProcessTerminated) {
      return false
    }
    val executor = DefaultDebugExecutor.getDebugExecutorInstance()
    return withContext(AndroidDispatchers.uiThread) {
      // Switch to the debug tab associated with the existing debug session, and open the debug tool window.
      if (content.manager == null) return@withContext false
      content.manager!!.setSelectedContent(content)
      val window = ToolWindowManager.getInstance(project).getToolWindow(executor.toolWindowId) ?: return@withContext false
      window.activate(null, false, true)
      true
    }
  }
}
