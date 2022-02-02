/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.android.tools.idea.profilers.analytics.StudioFeatureTracker
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.StudioProgramRunner
import com.android.tools.idea.run.util.SwapInfo
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

class ProfilerProgramRunner : StudioProgramRunner() {
  override fun getRunnerId() = "ProfilerProgramRunner"

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    return super.canRun(executorId, profile) &&
           ProfileRunExecutor.EXECUTOR_ID == executorId &&  // Super class canRun checks if profile is AndroidRunConfigurationBase.
           (profile as AndroidRunConfigurationBase).isProfilable
  }

  override fun canRunWithMultipleDevices(executorId: String) = false

  @Throws(ExecutionException::class)
  override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
    val descriptor = super.doExecute(state, environment)
    createProfilerToolWindow(environment.project, descriptor, environment.getUserData(SwapInfo.SWAP_INFO_KEY) != null)
    return descriptor
  }

  companion object {
    @JvmOverloads
    @JvmStatic
    fun createProfilerToolWindow(
      project: Project,
      descriptor: RunContentDescriptor?,
      isSwapExecution: Boolean = false
    ) {
      ApplicationManager.getApplication().assertIsDispatchThread()

      descriptor?.isActivateToolWindowWhenAdded = false
      ToolWindowManager.getInstance(project).getToolWindow(AndroidProfilerToolWindowFactory.ID)?.apply {
        if (!isVisible) {
          // First unset the last run app info, showing the tool window can trigger the profiler to start profiling using the stale info.
          // The most current run app info will be set in AndroidProfilerToolWindowLaunchTask instead.
          project.putUserData(AndroidProfilerToolWindow.LAST_RUN_APP_INFO, null)
          isAvailable = true
          show(null)
        }
      }
      AndroidProfilerToolWindowFactory.getProfilerToolWindow(project)?.apply {
        // Prevents from starting profiling a pid restored by emulator snapshot or a pid that was previously alive.
        disableAutoProfiling()

        // Early-terminate a previous ongoing session to simplify startup profiling scenarios.
        // Configuration and start of startup profiling is done while the old process/profiling session (if there is one) is still running.
        // Previously, when the old process/session eventually ends and the new session starts, the daemon can accidentally undo/end the
        // startup recording. By first ending the session here, we ensure the following sequence:
        // 1. Stops profiling the old process
        // 2. Configures startup profiling for the process to be launched
        // 3. Starts profiling the new process
        //
        // Don't do it for swap (Apply Changes). Swap doesn't end the process and therefore the
        // current session is expected to continue.
        if (!isSwapExecution) {
          profilers!!.sessionsManager.endCurrentSession()
        }
      }
      val featureTracker = StudioFeatureTracker(project)
      featureTracker.trackRunWithProfiling()
    }
  }
}