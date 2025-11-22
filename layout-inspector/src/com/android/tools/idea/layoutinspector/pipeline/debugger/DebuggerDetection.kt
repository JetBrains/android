/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.adblib.ConnectedDevice
import com.android.adblib.serialNumber
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.getOrNull
import com.android.adblib.tools.debugging.jdwpProcessTracker
import com.android.adblib.tools.debugging.jdwpProxySocketServer
import com.android.adblib.utils.createChildScope
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.layoutinspector.pipeline.AbstractInspectorClient
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val DEBUGGER_CHECK_DELAY = 200.milliseconds

/** Detect if a debugger is attached to the [client] process during LI start up. */
class DebuggerDetection(private val client: AbstractInspectorClient, parentScope: CoroutineScope) {
  private val project = client.project
  private val process = client.process
  private val deviceProvisioner =
    project.getService(DeviceProvisionerService::class.java).deviceProvisioner
  private val debuggerManager = XDebuggerManager.getInstance(project)
  private val scope = parentScope.createChildScope()
  private var debuggingPort = 0

  data class DebuggerState(val attached: Boolean = false, val isPaused: Boolean = false)

  private val _state = MutableStateFlow<DebuggerState>(DebuggerState())
  val state: StateFlow<DebuggerState> = _state.asStateFlow()

  init {
    scope.launch { debuggingPort().collect { port -> debuggingPort = port } }
    scope.launch {
      while (isActive) {
        val sessions = findDebugSessions(debuggingPort)
        _state.emit(DebuggerState(sessions.isNotEmpty(), sessions.any { it.isPaused }))
        delay(DEBUGGER_CHECK_DELAY)
      }
    }
  }

  /** After a successful start of the LayoutInspector, stop debugger detection. */
  fun attachCompleted() {
    scope.cancel()
  }

  /** Resume the current paused debugger. */
  fun resumeDebugger() {
    findDebugSessions(debuggingPort).filter { it.isPaused }.forEach { it.resume() }
  }

  /** Provide the ConnectedDevice of the current process */
  private fun connectedDevice(): Flow<ConnectedDevice?> = flow {
    deviceProvisioner.devices.collect { handles ->
      emit(
        handles
          .mapNotNull { it.state.connectedDevice }
          .firstOrNull { it.serialNumber == process.device.serial }
      )
    }
  }

  /** Provide the JdwpProcess of the current process */
  private fun jdwpProcess(): Flow<JdwpProcess?> = flow {
    connectedDevice().collect { device ->
      device?.jdwpProcessTracker?.processesFlow?.collect { list ->
        emit(list.find { it.pid == process.pid })
      }
    }
  }

  /** Provide the debugging port of the current process or 0. */
  private fun debuggingPort(): Flow<Int> = flow {
    jdwpProcess().collect { process ->
      if (process == null) {
        emit(0)
      } else {
        process.jdwpProxySocketServer.proxyStatusFlow.collect {
          emit(it.socketAddress.getOrNull()?.port ?: 0)
        }
      }
    }
  }

  /**
   * Return the debug sessions on the specified debugging port. Both Java and Hybrid debug sessions
   * are returned.
   */
  private fun findDebugSessions(debuggingPort: Int): List<XDebugSession> {
    return debuggerManager.debugSessions.filter { session ->
      val javaProcess = session.debugProcess.javaProcess
      val port = javaProcess?.debuggerSession?.process?.connection?.debuggerAddress?.toIntOrNull()
      port == debuggingPort
    }
  }

  /**
   * The XDebugProcess can be a JavaDebugProcess or a hybrid process with an associated Java
   * session. Return the JavaDebugProcess of either.
   */
  private val XDebugProcess.javaProcess: JavaDebugProcess?
    get() {
      (this as? JavaDebugProcess)?.let {
        return it
      }
      return hybridJavaSession?.debugProcess as? JavaDebugProcess
    }

  /**
   * Return the JavaDebugProcess if present. The Layout Inspector can only attach to JVM processes.
   * As such we are only interested in JavaDebugProcess or a hybrid process that is associated with
   * a JavaDebugProcess.
   */
  private val XDebugProcess.hybridJavaSession: XDebugSession?
    get() =
      try {
        val method = javaClass.getMethod("getJavaSession").apply { isAccessible = true }
        method.invoke(this) as? XDebugSession
      } catch (_: Throwable) {
        null
      }
}
