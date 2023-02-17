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
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.profilers.analytics.StudioFeatureTracker
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor
import com.android.tools.idea.run.profiler.AbstractProfilerExecutorGroup
import com.android.tools.idea.run.profiler.ProfilingMode
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType
import com.google.wireless.android.sdk.stats.RunWithProfilingMetadata
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ThreeState
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ProfilerProgramRunner : AndroidConfigurationProgramRunner() {
  override fun getRunnerId() = "ProfilerProgramRunner"

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    if (!super.canRun(executorId, profile)) {
      return false
    }
    if (!isProfilerExecutor(executorId)) {
      return false
    }
    if (profile !is RunConfiguration) {
      return false
    }
    val syncState = GradleSyncState.getInstance(profile.project)
    return !syncState.isSyncInProgress && syncState.isSyncNeeded() == ThreeState.NO
  }

  override val supportedConfigurationTypeIds = listOf(
    AndroidRunConfigurationType().id,
    AndroidTestRunConfigurationType().id
  )

  override fun canRunWithMultipleDevices(executorId: String) = false
  override fun run(environment: ExecutionEnvironment, state: RunProfileState, indicator: ProgressIndicator): RunContentDescriptor {
    val executor = state as AndroidConfigurationExecutor

    if (!isProfilerExecutor(environment.executor.id)) {
      throw RuntimeException("Not a profiler executor")
    }

    val swapInfo = environment.getUserData(SwapInfo.SWAP_INFO_KEY)

    return when (swapInfo?.type) {
      SwapInfo.SwapType.APPLY_CHANGES -> executor.applyChanges(indicator)
      SwapInfo.SwapType.APPLY_CODE_CHANGES -> executor.applyCodeChanges(indicator)
      else -> executor.run(indicator)
    }
  }

  @Throws(ExecutionException::class)
  override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
    val executorId = environment.executor.id
    return if (ProfileRunExecutor.EXECUTOR_ID == executorId) {
      // Profile executor for ASwB.
      doExecuteInternal(state, environment)
    }
    else {
      // Profile executor group for Profileable Builds.
      when (AbstractProfilerExecutorGroup.getExecutorSetting(executorId)?.profilingMode) {
        ProfilingMode.DEBUGGABLE, ProfilingMode.NOT_SET -> doExecuteInternal(state, environment)
        ProfilingMode.PROFILEABLE -> checkProfileableSupportAndExecute(state, environment)
        else -> resolvedPromise(null)
      }
    }
  }

  private fun doExecuteInternal(state: RunProfileState, environment: ExecutionEnvironment): Promise<RunContentDescriptor?> {
    val descriptor = super.execute(environment, state)
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
  private fun checkProfileableSupportAndExecute(state: RunProfileState, environment: ExecutionEnvironment): Promise<RunContentDescriptor?> {
    if (isAgpVersionSupported(environment.project) && isDeviceSupported(environment)) {
      return doExecuteInternal(state, environment)
    }
    val dialog = object : DialogWrapper(environment.project) {
      override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
          add(JBLabel("<html>Profiling with Low Overhead requires Android Gradle Plugin 7.3 or higher, a device with API level 29 or higher,<br>" +
                      "and a system that is not debuggable (e.g., a Google Play enabled emulator system image).<br>" +
                      "Do you want to Profile with Complete Data instead?</html>"), BorderLayout.CENTER)
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
    return resolvedPromise()
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
        val profilingMode = AbstractProfilerExecutorGroup.getExecutorSetting(executorId)?.profilingMode
                            ?: ProfilingMode.NOT_SET
        metadataBuilder.profilingMode = profilingMode.analyticsProtoType
        // TODO(b/234158986): track build type metadata (debuggable, profileable, etc.)
      }
      featureTracker.trackRunWithProfiling(metadataBuilder.build())
    }

    private fun isProfilerExecutor(executorId: String): Boolean {
      if (StudioFlags.PROFILEABLE_BUILDS.get() &&
          // Profileable Builds support multiple profiling modes, wrapped in RegisteredSettings. To get the selected
          // mode, query the ExecutorGroup by executor ID. If a registered setting is found, the executor is a profiler one.
          // See ProfileRunExecutorGroup for the registered settings.
          AbstractProfilerExecutorGroup.getInstance()?.getRegisteredSettings(executorId) != null) {
        return true
      }
      // Legacy profiler executor, used by non-gradle build settings such as ASwB and APK Profiling.
      return ProfileRunExecutor.EXECUTOR_ID == executorId
    }

    private fun isAgpVersionSupported(project: Project): Boolean {
      val agpVersion = GradleUtil.getLastKnownAndroidGradlePluginVersion(project)?.let { AgpVersion.tryParse(it) }
      return agpVersion != null && agpVersion.isAtLeastIncludingPreviews(7, 3, 0)
    }

    private fun isDeviceSupported(env: ExecutionEnvironment): Boolean {
      val deviceFutures = env.getCopyableUserData(DeviceFutures.KEY)
      val targetDevices = deviceFutures?.devices ?: emptyList()
      if (targetDevices.isNotEmpty()) {
        val device = targetDevices[0]
        return device.version.isGreaterOrEqualThan(VersionCodes.Q) && !device.isDebuggable
      }
      return false
    }
  }
}