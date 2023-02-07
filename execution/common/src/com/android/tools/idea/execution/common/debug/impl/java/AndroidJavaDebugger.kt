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

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.Client
import com.android.tools.idea.execution.common.debug.AndroidDebuggerConfigurable
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState
import com.android.tools.idea.execution.common.debug.DebugSessionStarter.attachDebuggerToClientAndShowTab
import com.android.tools.idea.execution.common.debug.impl.AndroidDebuggerImplBase
import com.android.tools.idea.execution.common.debug.startAndroidJavaDebuggerSession
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import java.util.concurrent.TimeUnit

class AndroidJavaDebugger : AndroidDebuggerImplBase<AndroidDebuggerState>() {
  override fun getId(): String {
    return ID
  }

  override fun getDisplayName(): String {
    return "Java Only"
  }

  override fun createState(): AndroidDebuggerState {
    return AndroidDebuggerState()
  }

  override fun createConfigurable(runConfiguration: RunConfiguration): AndroidDebuggerConfigurable<AndroidDebuggerState?> {
    return AndroidDebuggerConfigurable()
  }

  override fun supportsProject(project: Project): Boolean {
    return true
  }

  @WorkerThread
  override fun attachToClient(project: Project, client: Client, debugState: AndroidDebuggerState?): XDebugSession {
    val debugPort = getClientDebugPort(client)

    // Try to find existing debug session
    val existingDebugSession = getExistingDebugSession(debugPort)
    if (existingDebugSession != null) {
      activateDebugSessionWindow(project, existingDebugSession.runContentDescriptor)
      return existingDebugSession
    }

    return attachDebuggerToClientAndShowTab(project, client, this, createState());
  }


  override fun getDebugProcessStarterForNewProcess(
    project: Project,
    client: Client,
    debugState: AndroidDebuggerState,
    consoleViewToReuse: ConsoleView?
  ): XDebugProcessStarter {
    try {
      val debuggerSession = startAndroidJavaDebuggerSession(project, client, consoleViewToReuse, {}, detachIsDefault = false)
                              .blockingGet(10, TimeUnit.SECONDS) ?: throw ExecutionException("Java Debug Session is not started")
      return object : XDebugProcessStarter() {
        override fun start(session: XDebugSession): XDebugProcess {
          return JavaDebugProcess.create(session, debuggerSession)
        }
      }
    }
    catch (e: java.util.concurrent.ExecutionException) {
      throw e.cause ?: e
    }
  }

  override fun getDebugProcessStarterForExistingProcess(
    project: Project,
    client: Client,
    state: AndroidDebuggerState?
  ): XDebugProcessStarter {
    try {
      val debuggerSession = startAndroidJavaDebuggerSession(project, client, null, {}, detachIsDefault = true)
                              .blockingGet(10, TimeUnit.SECONDS) ?: throw ExecutionException("Java Debug Session is not started")
      return object : XDebugProcessStarter() {
        override fun start(session: XDebugSession): XDebugProcess {
          return JavaDebugProcess.create(session, debuggerSession)
        }
      }
    }
    catch (e: java.util.concurrent.ExecutionException) {
      throw e.cause ?: e
    }
  }

  companion object {
    const val ID = "Java"

    private fun getExistingDebugSession(debugPort: String): XDebugSession? {
      val sessions: MutableList<XDebugSession> = ArrayList()
      val openProjects = ProjectManager.getInstance().openProjects

      // Scan through open project to find if this port has been opened in any session.
      for (openProject in openProjects) {
        val debuggerSession = findJdwpDebuggerSession(openProject, debugPort)
        if (debuggerSession != null) {
          debuggerSession.xDebugSession?.let { sessions.add(it) }
        }
      }
      return sessions.firstOrNull()
    }
  }
}