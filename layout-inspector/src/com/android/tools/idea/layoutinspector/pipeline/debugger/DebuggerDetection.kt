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
package com.android.tools.idea.layoutinspector.pipeline.debugger

import com.android.ddmlib.Client
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.openapi.project.ProjectManager
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager

fun isPausedInDebugger(client: Client): Boolean = runInAttachedDebugger(client) { debugSession -> debugSession.isPaused }

fun resumeDebugger(client: Client) = runInAttachedDebugger(client) { debugSession ->
  val paused = debugSession.isPaused
  if (paused) {
    debugSession.resume()
  }
  // Do not stop after resuming a debugger since the app may be paused in 2 debuggers: native & Java
  false
}

/**
 * Run the [operation] on the [XDebugSession]s that are attached to the current [client].
 * If the app on the client has both JVM and native code, there may be a [XDebugSession] for each i.e. a total of 2 sessions.
 * The run will stop first time [operation] returns true in which case this function returns true.
 */
private fun runInAttachedDebugger(client: Client, operation: (XDebugSession) -> Boolean): Boolean {
  return client.isDebuggerAttached && ProjectManager.getInstance().openProjects.any { anyProject ->
    // This will find both native and Java debugger sessions:
    XDebuggerManager.getInstance(anyProject).debugSessions.any {
      it.debugProcess.isDebuggingClient(client) && operation(it)
    }
  }
}

/**
 * Return true if this [XDebugProcess] is attached to this [client].
 */
private fun XDebugProcess.isDebuggingClient(client: Client): Boolean {
  if (this is JavaDebugProcess) {
    return debuggerSession.process.isDebuggingClient(client)
  }

  // TODO(b/264545884): Remove these reflection calls when we have the ability to detect if a client is attached to a XDebugProcess.
  var processClass: Class<*>? = javaClass
  while (processClass != null && processClass.name != Object::class.java.name) {
    try {
      val method = processClass.getDeclaredMethod("getClient")
      val attachedClient = method.invoke(this) as? Client
      return attachedClient?.clientData?.pid == client.clientData.pid
    }
    catch (ignore: Exception) {
    }
    processClass = processClass.superclass
  }
  return false
}

/**
 * Return true if this [DebugProcessImpl] is attached to this [client].
 */
private fun DebugProcessImpl.isDebuggingClient(client: Client): Boolean =
  connection.debuggerAddress.trim() == client.debuggerListenPort.toString()
