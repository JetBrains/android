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
import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.processhandler.AndroidRemoteDebugProcessHandler
import com.intellij.debugger.DebugEnvironment
import com.intellij.debugger.DebuggerGlobalSearchScope
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope

/**
 * Describes Java debug process that we are about to start for any [Client]. It is used by Java debugger [StartJavaDebuggerSession.kt].
 *
 * This class is needed by the platform to create a debug session. See [DebuggerManagerEx.attachVirtualMachine]
 */
internal class AndroidJavaDebugEnvironment(
  private val project: Project,
  private val client: Client,
  private val mySessionName: String,
  private val consoleViewToReuse: ConsoleView?,
  private val onDebugProcessDestroyed: (IDevice) -> Unit,
  private val detachIsDefault: Boolean
) : DebugEnvironment {

  private val myRemoteConnection = RemoteConnection(true, "localhost", client.debuggerListenPort.toString(), false)

  private val searchScope = DebuggerGlobalSearchScope(GlobalSearchScope.allScope(project), project)
  override fun getSearchScope() = searchScope

  override fun createExecutionResult(): ExecutionResult {
    val console = consoleViewToReuse ?: TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    Disposer.register(project, console)
    val debugProcessHandler = AndroidRemoteDebugProcessHandler(
      project,
      client,
      detachIsDefault,
      onDebugProcessDestroyed
    )
    console.attachToProcess(debugProcessHandler)

    return DefaultExecutionResult(console, debugProcessHandler)
  }

  override fun isRemote() = true

  override fun getRemoteConnection() = myRemoteConnection

  override fun getPollTimeout() = 0L

  override fun getSessionName() = mySessionName

  override fun getRunJre() = ProjectRootManager.getInstance(project).projectSdk
}