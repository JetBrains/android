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
package com.android.tools.idea.run

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.debug.AndroidDebugger
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState
import com.android.tools.idea.execution.common.processhandler.AndroidRemoteDebugProcessHandler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.ThreeState
import org.jetbrains.android.actions.AndroidConnectDebuggerAction

@Service(Service.Level.PROJECT)
class AttachOnWaitForDebuggerMonitor(val host: DebuggerHost) : Disposable {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): AttachOnWaitForDebuggerMonitor? {
      return project.getService(AttachOnWaitForDebuggerMonitor::class.java)
    }
  }

  open class DebuggerHost(val project: Project) {
    open val runConfig: AndroidRunConfigurationBase?
      get() {
        if (!project.isInitialized) {
          return null
        }
        return (RunManager.getInstance(project).selectedConfiguration)?.configuration as? AndroidRunConfigurationBase
      }

    open fun debugger(config: AndroidRunConfigurationBase): AndroidDebugger<out AndroidDebuggerState>? {
      return config.androidDebuggerContext.androidDebugger
    }

    open fun enabled(config: AndroidRunConfigurationBase, debugger: AndroidDebugger<out AndroidDebuggerState>): Boolean {
      return config.androidDebuggerContext.getAndroidDebuggerState<AndroidDebuggerState>(debugger.id)?.ATTACH_ON_WAIT_FOR_DEBUGGER == true &&
             GradleSyncState.getInstance(project).isSyncNeeded() == ThreeState.NO
    }

    open fun canDebugRun(project: Project, config: AndroidRunConfigurationBase): Boolean {
      val runner = ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, config)
      return runner != null && !ExecutionManager.getInstance(project).isStarting(DefaultDebugExecutor.EXECUTOR_ID, runner.runnerId)
    }

    open fun anyActiveDebugSessions(project: Project, device: IDevice, applicationId: String): Boolean {
      return ExecutionManager.getInstance(project).getRunningProcesses().any { process ->
        (process as? AndroidRemoteDebugProcessHandler)?.isPackageRunning(device, applicationId) == true &&
        !(process.isProcessTerminating || process.isProcessTerminated)
      }
    }

    open fun attachAction(project: Project, debugger: AndroidDebugger<out AndroidDebuggerState>, client: Client, config: AndroidRunConfigurationBase) {
      AndroidConnectDebuggerAction.closeOldSessionAndRun(project, debugger, client, config)
    }
  }

  constructor(project: Project) : this(DebuggerHost(project))

  @VisibleForTesting
  val listener = object: AndroidDebugBridge.IClientChangeListener {
    override fun clientChanged(client: Client, changeMask: Int) {
      if (!StudioFlags.ATTACH_ON_WAIT_FOR_DEBUGGER.get()) {
        return
      }

      val config = host.runConfig ?: return
      val debugger = host.debugger(config) ?: return

      if ((changeMask and Client.CHANGE_DEBUGGER_STATUS) != Client.CHANGE_DEBUGGER_STATUS ||
          client.clientData.debuggerConnectionStatus != ClientData.DebuggerStatus.WAITING) {
        return
      }

      if (!host.enabled(config, debugger)) {
        return
      }

      val applicationId = config.applicationIdProvider?.packageName ?: return
      if (!host.canDebugRun(host.project, config) || host.anyActiveDebugSessions(host.project, client.device, applicationId)) {
        return
      }

      host.attachAction(host.project, debugger, client, config)
    }
  }

  init {
    AndroidDebugBridge.addClientChangeListener(listener)
  }

  override fun dispose() {
    AndroidDebugBridge.removeClientChangeListener(listener)
  }
}
