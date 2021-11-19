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
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.ddmlib.NullOutputReceiver
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.configuration.AndroidWearConfiguration
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.DefaultDebugUIEnvironment
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
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DebugSessionStarter(private val environment: ExecutionEnvironment) {

  private val configuration = environment.runProfile as AndroidWearConfiguration
  private val project = configuration.project
  private val appId = project.getProjectSystem().getApplicationIdProvider(configuration)?.packageName
                      ?: throw RuntimeException("Cannot get ApplicationIdProvider")

  @WorkerThread
  fun attachDebuggerToClient(device: IDevice, processHandler: AndroidProcessHandlerForDevices, consoleView: ConsoleView): RunContentDescriptor {
    waitForClient(device)
    val client = device.getClient(appId)
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

      val debugEnvironment = DefaultDebugUIEnvironment(environment, debugState, remoteConnection, false)
      val debuggerSession = DebuggerManagerEx.getInstanceEx(project).attachVirtualMachine(debugEnvironment.environment)
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
  private fun waitForClient(device: IDevice): Client {
    ProgressIndicatorProvider.getGlobalProgressIndicator()?.text = "Waiting for a process to start"
    val appProcessCountDownLatch = CountDownLatch(1)
    val listener = object : AndroidDebugBridge.IDeviceChangeListener {
      override fun deviceConnected(device: IDevice) {}
      override fun deviceDisconnected(device: IDevice) {}

      override fun deviceChanged(changedDevice: IDevice, changeMask: Int) {
        if (changedDevice == device && changeMask and IDevice.CHANGE_CLIENT_LIST != 0) {
          val clients = changedDevice.clients
          if (clients.find { it.clientData.packageName == appId } != null) {
            appProcessCountDownLatch.countDown()
            AndroidDebugBridge.removeDeviceChangeListener(this)
          }
        }
      }
    }
    AndroidDebugBridge.addDeviceChangeListener(listener)

    if (device.getClient(appId) != null) {
      appProcessCountDownLatch.countDown()
      AndroidDebugBridge.removeDeviceChangeListener(listener)
    }

    if (!appProcessCountDownLatch.await(15, TimeUnit.SECONDS)) {
      device.executeShellCommand(AndroidRemoteDebugProcessHandler.CLEAR_DEBUG_APP_COMMAND, NullOutputReceiver(), 5, TimeUnit.SECONDS)
      throw ExecutionException("Process $appId is not found. Aborting session.")
    }

    return device.getClient(appId)
  }
}

class AndroidRemoteDebugProcessHandler(
  project : Project,
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
      it.executeShellCommand(CLEAR_DEBUG_APP_COMMAND, AndroidWearConfigurationExecutorBase.AndroidLaunchReceiver({false}, console), 5, TimeUnit.SECONDS)
    }
  }
}