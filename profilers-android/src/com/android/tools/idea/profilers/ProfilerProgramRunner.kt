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

import com.android.ide.common.repository.AgpVersion
import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.profilers.analytics.StudioFeatureTracker
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.StudioProgramRunner
import com.android.tools.idea.run.profiler.AbstractProfilerExecutorGroup
import com.android.tools.idea.run.profiler.ProfilingMode
import com.android.tools.idea.run.util.SwapInfo
import com.google.wireless.android.sdk.stats.RunWithProfilingMetadata
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ProfilerProgramRunner : StudioProgramRunner() {
  override fun getRunnerId() = "ProfilerProgramRunner"

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    // Super class canRun checks if profile is AndroidRunConfigurationBase.
    return super.canRun(executorId, profile) &&
           canRunByProfiler(executorId, profile as AndroidRunConfigurationBase)
  }

  override fun canRunWithMultipleDevices(executorId: String) = false

  @Throws(ExecutionException::class)
  override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
    val executorId = environment.executor.id
    return if (ProfileRunExecutor.EXECUTOR_ID == executorId) {
      // Profile executor for ASwB.
      doExecuteInternal(state, environment)
    }
    else {
      // Profile executor group for Profileable Builds.
      when (AbstractProfilerExecutorGroup.getInstance()?.getRegisteredSettings(executorId)?.profilingMode) {
        ProfilingMode.DEBUGGABLE, ProfilingMode.NOT_SET -> doExecuteInternal(state, environment)
        ProfilingMode.PROFILEABLE -> checkProfileableSupportAndExecute(state, environment)
        else -> null
      }
    }
  }

  private fun doExecuteInternal(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
    val descriptor = super.doExecute(state, environment)
    createProfilerToolWindow(environment.project,
                             environment.runnerAndConfigurationSettings,
                             environment.getUserData(SwapInfo.SWAP_INFO_KEY) != null,
                             environment.executor.id)
    return descriptor
  }

  /**
   * Checks if Profileable Builds is supported. If so process to execution. Otherwise, prompt user to choose if they want to continue with
   * the debuggable fallback or abort.
   */
  private fun checkProfileableSupportAndExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
    if (isAgpVersionSupported(environment.project) && isDeviceSupported(environment)) {
      return doExecuteInternal(state, environment)
    }
    val dialog = object : DialogWrapper(environment.project) {
      override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
          add(JBLabel("<html>Profiling with Low Overhead requires Android Gradle Plugin 8.0 and a device with API level 29 or higher.<br>" +
                      "Do you want to continue to Profile with Complete Data?</html>"), BorderLayout.CENTER)
        }
      }

      init {
        title = "Confirmation"
        init()
      }
    }
    if (dialog.showAndGet()) {
      // Profileable is unsupported but user agrees to fall back to debuggable.
      return doExecuteInternal(state, environment)
    }
    // Cancel the profiling session.
    return null
  }

  companion object {
    @JvmOverloads
    @JvmStatic
    fun createProfilerToolWindow(
      project: Project,
      settings: RunnerAndConfigurationSettings?,
      isSwapExecution: Boolean = false,
      executorId: String? = null
    ) {
      ApplicationManager.getApplication().assertIsDispatchThread()

      // Prevents the Run tool window from taking over the Profiler tool window.
      // TODO(b/251297822): find a better fix than overwriting this user configuration.
      settings?.isActivateToolWindowBeforeRun = false

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

      // Metrics tracking.
      val featureTracker = StudioFeatureTracker(project)
      val metadataBuilder = RunWithProfilingMetadata.newBuilder()
      if (StudioFlags.PROFILEABLE_BUILDS.get() && executorId != null) {
        // Track profiling mode.
        // Executor will be null for legacy AGP version, which doesn't support profiling mode.
        // ASwB does not support profiling mode either, but it uses a different ProgramRunner so no event will be recorded.
        val profilingMode = AbstractProfilerExecutorGroup.getInstance()?.getRegisteredSettings(executorId)?.profilingMode
                            ?: ProfilingMode.NOT_SET
        metadataBuilder.profilingMode = profilingMode.analyticsProtoType
        // TODO(b/234158986): track build type metadata (debuggable, profileable, etc.)
      }
      featureTracker.trackRunWithProfiling(metadataBuilder.build())
    }

    private fun canRunByProfiler(executorId: String, androidRunConfig: AndroidRunConfigurationBase): Boolean {
      if (androidRunConfig.isProfilable) {
        if (StudioFlags.PROFILEABLE_BUILDS.get()) {
          // Profileable Builds support multiple profiling modes, wrapped in RegisteredSettings. To get the selected
          // mode, query the ExecutorGroup by executor ID. If no registered setting is found, the executor is not a
          // profiler one (e.g. Run).
          // See ProfileRunExecutorGroup for the registered settings.
          return AbstractProfilerExecutorGroup.getInstance()?.getRegisteredSettings(executorId) != null
        }
        // Legacy profiler executor.
        return ProfileRunExecutor.EXECUTOR_ID == executorId
      }
      return false
    }

    private fun isAgpVersionSupported(project: Project): Boolean {
      val agpVersion = GradleUtil.getLastKnownAndroidGradlePluginVersion(project)?.let { AgpVersion.tryParse(it) }
      return agpVersion != null && agpVersion.isAtLeastIncludingPreviews(8, 0, 0)
    }

    private fun isDeviceSupported(env: ExecutionEnvironment): Boolean {
      val deviceFutures = env.getCopyableUserData(DeviceFutures.KEY)
      val targetDevices = deviceFutures?.devices ?: emptyList()
      if (targetDevices.isNotEmpty()) {
        return targetDevices[0].version.isGreaterOrEqualThan(VersionCodes.Q)
      }
      return false
    }
  }
}