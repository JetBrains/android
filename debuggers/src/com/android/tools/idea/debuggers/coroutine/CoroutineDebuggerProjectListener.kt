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
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import org.jetbrains.kotlin.idea.debugger.coroutine.DebuggerConnection

/**
 * Class responsible for setting up the coroutine debugger panel
 */
class CoroutineDebuggerProjectListener : ProjectManagerListener {
  private var associatedProject: Project? = null

  override fun projectOpened(project: Project) {
    if (!FlagController.isCoroutineDebuggerEnabled) {
      return
    }

    // multiple projects can be opened at the same time, which causes multiple ProjectManagerListeners to be created.
    // ProjectManagerListeners#projectOpened is called on every listener every time a project is opened.
    // by checking this flag we prevent the same listener to register the execution listener multiple times.
    if (associatedProject != null) {
      return
    }
    associatedProject = project

    val connection = project.messageBus.connect(project)

    val executionListener = CoroutineDebuggerListener(project)
    connection.subscribe(XDebuggerManager.TOPIC, executionListener)
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

    val debuggerConnection = DebuggerConnection(project, null, null, false, alwaysShowPanel = true)

    // creating the [DebuggerConnection] object does nothing on its own. In order for the panel to be created
    // we need to forward the "processStarted" call to the Kotlin plugin DebuggerConnection component,
    // which is responsible for creating the Coroutines Debugger panel
    debuggerConnection.processStarted(debugProcess)
  }
}