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
package com.android.tools.idea.debuggers.coroutine

import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import org.jetbrains.android.AndroidStartupManager.ProjectDisposableScope
import org.jetbrains.kotlin.idea.debugger.coroutine.DebuggerConnection

/**
 * Class responsible for setting up the coroutine debugger panel
 */
class CoroutineDebuggerProjectActivity : ProjectActivity{
  override suspend fun execute(project: Project) {
    if (!FlagController.isCoroutineDebuggerEnabled) {
      return
    }
    val connection = project.messageBus.connect(project.service<ProjectDisposableScope>())
    connection.subscribe(XDebuggerManager.TOPIC, CoroutineDebuggerListener(project))
  }
}

private class CoroutineDebuggerListener(private val project: Project) : XDebuggerManagerListener {
  override fun processStarted(debugProcess: XDebugProcess) {
    // don't show coroutine debugger panel if disabled in settings
    if (!CoroutineDebuggerSettings.isCoroutineDebuggerEnabled()) {
      return

    }
    // we check the process handler to differentiate between regular JVM processes and Android processes.
    // we don't want to create the panel if the process is regular JVM.
    if (AndroidSessionInfo.from(debugProcess.processHandler) == null) {
      return
    }

    val debuggerConnection = DebuggerConnection(project, null, null, shouldAttachCoroutineAgent = false, alwaysShowPanel = true)

    val sessionId = (debugProcess.session as XDebugSessionImpl).id
    val sessionProxy = XDebugManagerProxy.getInstance().findSessionProxy(project, sessionId) ?: return

    // creating the [DebuggerConnection] object does nothing on its own. In order for the panel to be created
    // we need to forward the "processStarted" call to the Kotlin plugin DebuggerConnection component,
    // which is responsible for creating the Coroutines Debugger panel
    debuggerConnection.sessionStarted(sessionProxy)
  }
}