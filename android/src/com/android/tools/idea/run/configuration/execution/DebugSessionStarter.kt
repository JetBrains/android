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
package com.android.tools.idea.run.configuration.execution

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData.DebuggerStatus
import com.android.ddmlib.IDevice
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.DeploymentApplicationService
import com.android.tools.idea.run.configuration.ComponentSpecificConfiguration
import com.google.common.util.concurrent.Uninterruptibles
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.DefaultDebugEnvironment
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.RemoteDebugProcessHandler
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.configurations.RemoteState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import java.util.Locale
import java.util.concurrent.TimeUnit

class DebugSessionStarter(private val environment: ExecutionEnvironment) {

  private val configuration = environment.runProfile as ComponentSpecificConfiguration
  private val project = configuration.project
  private val appId = project.getProjectSystem().getApplicationIdProvider(configuration)?.packageName
                      ?: throw RuntimeException("Cannot get ApplicationIdProvider")

  @WorkerThread
  fun attachDebuggerToClient(device: IDevice,
                             processHandler: AndroidProcessHandlerForDevices,
                             consoleView: ConsoleView): RunContentDescriptor {
    val client = waitForClient(device, consoleView)
    val debugPort = client.debuggerListenPort.toString()
    val remoteConnection = RemoteConnection(true, "localhost", debugPort, false)
    ProgressIndicatorProvider.getGlobalProgressIndicator()?.text = "Attaching debugger"
    return invokeAndWaitIfNeeded {
      val debugState = object : RemoteState {
        override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult {
          val process = AndroidRemoteDebugProcessHandler(project, consoleView, processHandler)
          consoleView.attachToProcess(process)
          return DefaultExecutionResult(consoleView, process)
        }

        override fun getRemoteConnection() = remoteConnection
      }

      val debugEnvironment = DefaultDebugEnvironment(environment, debugState, remoteConnection, false)
      val debuggerSession = DebuggerManagerEx.getInstanceEx(project).attachVirtualMachine(debugEnvironment)
                            ?: throw ExecutionException("Could not attach the virtual machine")

      val debugSession = XDebuggerManager.getInstance(project).startSession(environment, object : XDebugProcessStarter() {
        override fun start(session: XDebugSession): XDebugProcess {
          return JavaDebugProcess.create(session, debuggerSession)
        }
      })

      debugSession.runContentDescriptor
    }
  }

  @WorkerThread
  private fun waitForClient(device: IDevice, console: ConsoleView): Client {
    val pollTimeoutSeconds = 15
    val timeUnit = TimeUnit.SECONDS

    for (i in 0 until pollTimeoutSeconds) {
      ProgressManager.checkCanceled()
      if (!device.isOnline) {
        throw ExecutionException("Device is offline")
      }
      val clients = DeploymentApplicationService.getInstance().findClient(device, appId)
      if (clients.isEmpty()) {
        console.print("Waiting for application to come online: $appId")
      }
      else {
        console.print("Connecting to $appId")
        if (clients.size > 1) {
          Logger.getInstance(DebugSessionStarter::class.java).info("Multiple clients with same application ID: $appId")
        }
        val client = clients[0]
        when (client.clientData.debuggerConnectionStatus) {
          DebuggerStatus.ERROR -> {
            val message = String.format(Locale.US,
                                        "Debug port (%1\$d) is busy, make sure there is no other active debug connection to the same application",
                                        client.debuggerListenPort)
            console.printError(message)
            throw ExecutionException(message)
          }
          DebuggerStatus.ATTACHED -> {
            val message = "A debugger is already attached"
            console.printError(message)
            throw ExecutionException(message)
          }
          else -> {
            console.print("Waiting for application to start debug server")
            return client
          }
        }

      }
      sleep(1, timeUnit)
    }
    throw ExecutionException("Process $appId is not found. Aborting session.")
  }

  protected fun sleep(sleepFor: Long, unit: TimeUnit) {
    Uninterruptibles.sleepUninterruptibly(sleepFor, unit)
  }
}

/**
 * [processHandler] is handler responsible for monitoring app process on devices. For example [WatchFaceProcessHandler].
 * [AndroidRemoteDebugProcessHandler] is responsible for monitoring debugger process.
 */
class AndroidRemoteDebugProcessHandler(
  project: Project,
  private val console: ConsoleView,
  private val processHandler: AndroidProcessHandlerForDevices
) : RemoteDebugProcessHandler(project, false) {

  companion object {
    const val CLEAR_DEBUG_APP_COMMAND = "am clear-debug-app"
  }

  override fun detachIsDefault() = false

  override fun destroyProcess() {
    super.destroyProcess()
    processHandler.destroyProcess()
    processHandler.devices.forEach {
      console.printShellCommand(CLEAR_DEBUG_APP_COMMAND)
      it.executeShellCommand(CLEAR_DEBUG_APP_COMMAND, ConsoleOutputReceiver({ false }, console), 5, TimeUnit.SECONDS)
    }
  }
}