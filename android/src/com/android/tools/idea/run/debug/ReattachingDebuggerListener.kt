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

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.tools.idea.run.configuration.execution.DebugSessionStarter
import com.android.tools.idea.run.editor.AndroidDebugger
import com.android.tools.idea.run.editor.AndroidDebuggerState
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

/**
 * Process handler that is used for [DebugSessionStarter.attachDebuggerToStartedProcess]
 *
 * This is [ProcessHandler] with which user will interact (stop tests via UI).
 * Thus, it should be set as a process handler for [RunContentDescriptor] for debug session for every new process(new test).
 *
 * It has two important properties:
 * 1. If user presses stop button it terminates [masterProcessHandler].
 * If [masterProcessHandler] terminates [ReattachingProcessHandler] goes to terminated state.
 *
 * 2. When current test process is finished [ReattachingProcessHandler] goes to terminated state,
 * so RunContentDescriptionManager will reuse the current tab for the next test(next debug session) instead of opening a new one.
 */
internal class ReattachingProcessHandler(private val masterProcessHandler: ProcessHandler) : ProcessHandler() {
  init {
    masterProcessHandler.addProcessListener(object : ProcessAdapter() {
      override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
        if (!isProcessTerminating && !isProcessTerminated) {
          detachProcess()
        }
      }
    })
    startNotify()
  }

  fun subscribeOnDebugProcess(debugProcessHandler: ProcessHandler) {
    debugProcessHandler.addProcessListener(object : ProcessAdapter() {
      override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
        if (!isProcessTerminating && !isProcessTerminated) {
          detachProcess()
        }
      }
    })
  }

  override fun destroyProcessImpl() {
    masterProcessHandler.destroyProcess()
    notifyProcessTerminated(0)
  }

  override fun detachProcessImpl() {
    notifyProcessDetached()
  }

  override fun detachIsDefault() = false

  override fun getProcessInput() = null
}

/**
 * While [masterProcessHandler] is not terminated starts [androidDebugger] for clients with a process name [applicationId].
 */
internal class ReattachingDebuggerListener<S : AndroidDebuggerState>(
  private val project: Project,
  private val masterProcessHandler: ProcessHandler,
  private val applicationId: String,
  private val androidDebugger: AndroidDebugger<S>,
  val androidDebuggerState: S,
  private val consoleViewToReuse: ConsoleView?,
  val environment: ExecutionEnvironment,
  private var processHandlerForOpenedTab: ReattachingProcessHandler
) : AndroidDebugBridge.IClientChangeListener {

  init {
    Disposer.register(project) { AndroidDebugBridge.removeClientChangeListener(this) }
  }

  companion object {
    /**
     * Changes to [Client] instances that mean a new debugger should be connected.
     *
     * The target application can either:
     * 1. Match our target name, and become available for debugging.
     * 2. Be available for debugging, and suddenly have its name changed to match.
     */
    const val CHANGE_MASK = Client.CHANGE_DEBUGGER_STATUS or Client.CHANGE_NAME
  }

  private val LOG = Logger.getInstance(ReattachingDebuggerListener::class.java)

  private fun isClientForDebug(client: Client, changeMask: Int): Boolean {
    val clientDescription = client.clientData.clientDescription
    if (applicationId == clientDescription) {
      if (changeMask and CHANGE_MASK != 0 && client.clientData.debuggerConnectionStatus == ClientData.DebuggerStatus.WAITING) {
        LOG.debug("Client waiting for debugger, PORT: ${client.debuggerListenPort}, PID: ${client.clientData.pid}\n")
        return true
      }
    }
    return false
  }

  private fun handleError(e: Throwable) {
    try {
      masterProcessHandler.destroyProcess()
    }
    catch (destroyError: Exception) {
      LOG.warn(destroyError)
    }
    if (e is ExecutionException) {
      showError(project, e, environment.runProfile.name)
    }
    else {
      LOG.error(e)
    }
  }

  override fun clientChanged(client: Client, changeMask: Int) {
    if (isClientForDebug(client, changeMask)) {
      if (!masterProcessHandler.isProcessTerminating && !masterProcessHandler.isProcessTerminated) {
        LOG.info("Attaching debugger to a client, PID: ${client.clientData.pid}")
        DebugSessionStarter.attachDebuggerToStartedProcess(client.device, client.clientData.clientDescription!!, environment,
                                                           androidDebugger, androidDebuggerState,
                                                           { it.forceStop(client.clientData.clientDescription!!) },
                                                           consoleViewToReuse)
          .onSuccess { session ->
            processHandlerForOpenedTab.detachProcess()

            processHandlerForOpenedTab = ReattachingProcessHandler(masterProcessHandler)
            processHandlerForOpenedTab.subscribeOnDebugProcess(session.debugProcess.processHandler)

            session.runContentDescriptor.processHandler = processHandlerForOpenedTab
            runInEdt {
              try {
                session.showSessionTab()
              }
              catch (e: Throwable) {
                handleError(e)
              }
            }
          }
          .onError { handleError(it) }
      }
    }
  }
}