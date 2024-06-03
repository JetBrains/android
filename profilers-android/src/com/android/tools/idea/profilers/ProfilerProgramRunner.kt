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

import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.profilers.analytics.StudioFeatureTracker
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.execution.common.AndroidConfigurationProgramRunner
import com.android.tools.idea.run.configuration.AndroidTileConfigurationType
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfigurationType
import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.run.profiler.AbstractProfilerExecutorGroup
import com.android.tools.idea.run.profiler.ProfilingMode
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType
import com.android.tools.profilers.SupportLevel
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
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

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
    val projectSystem = profile.project.getProjectSystem()
    if (StudioFlags.PROFILEABLE_BUILDS.get()) {
      // There are multiple profiler executors. The project's build system determines their applicability.
      if (projectSystem.supportsProfilingMode()) {
        if (AbstractProfilerExecutorGroup.getInstance()?.getRegisteredSettings(executorId) == null) {
          // Anything other than "Run as profileable (low overhead)" and "Run as debuggable (complete data)" cannot run.
          return false
        }
      }
      else if (ProfileRunExecutor.EXECUTOR_ID != executorId) {
        // Anything other than "Profile" cannot run.
        return false
      }
    }
    return projectSystem.getSyncManager().run { !isSyncInProgress() && !isSyncNeeded() }
  }

  override val supportedConfigurationTypeIds = listOf(
    AndroidRunConfigurationType().id,
    AndroidTestRunConfigurationType().id,
    AndroidWatchFaceConfigurationType().id,
    AndroidTileConfigurationType().id
  )

  override fun canRunWithMultipleDevices(executorId: String) = false
  override fun run(environment: ExecutionEnvironment, executor: AndroidConfigurationExecutor, indicator: ProgressIndicator): RunContentDescriptor {
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
    val projectSupported = isProjectSupported(environment.project)
    val apiLevelSupported = isApiLevelSupported(environment)
    val systemSupported = isSystemSupported(environment)
    if (projectSupported && apiLevelSupported && systemSupported) {
      return doExecuteInternal(state, environment)
    }

    // Prompt the user.
    Messages.showErrorDialog(environment.project, buildProfileableRequirementMessage(projectSupported, apiLevelSupported, systemSupported),
                             "Unsupported Device or Emulator")

    // Reset user's selection stored on task enter (e.g. startup tasks being enabled) as the task execution has been canceled and thus the
    // state is invalid.
    if (StudioFlags.PROFILER_TASK_BASED_UX.get()) {
      val taskHomeTabModel = AndroidProfilerToolWindowFactory.getProfilerToolWindow(environment.project)?.profilers?.taskHomeTabModel
      taskHomeTabModel?.resetSelectionStateAndClearStartupTaskConfigs()
    }
    // Cancel the profiling session.
    return resolvedPromise()
  }

  companion object {
    @VisibleForTesting
    const val MAX_MESSAGE_LINE_LENGTH = 120

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

    fun isProfilerExecutor(executorId: String): Boolean {
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

    fun isProjectSupported(project: Project): Boolean {
      val projectSystem = project.getProjectSystem()
      val token = projectSystem.getTokenOrNull(ProfilerProgramRunnerToken.EP_NAME) ?: return false
      return token.isProfileableBuildSupported(projectSystem)
    }

    fun isApiLevelSupported(featureLevel: Int) = featureLevel >= (VersionCodes.Q)

    /**
     * Overload that finds the target device in addition to checking the device's api level.
     */
    private fun isApiLevelSupported(env: ExecutionEnvironment): Boolean {
      val deviceFutures = env.getCopyableUserData(DeviceFutures.KEY)
      val targetDevices = deviceFutures?.devices ?: emptyList()
      if (targetDevices.isNotEmpty()) {
        val device = targetDevices[0]
        return isApiLevelSupported(device.version.featureLevel)
      }
      return false
    }

    fun isSystemSupported(isDebuggable: Boolean) = !isDebuggable

    /**
     * Overload that finds the target device in addition to checking the system support.
     */
    private fun isSystemSupported(env: ExecutionEnvironment): Boolean {
      val deviceFutures = env.getCopyableUserData(DeviceFutures.KEY)
      val targetDevices = deviceFutures?.devices ?: emptyList()
      if (targetDevices.isNotEmpty()) {
        val device = targetDevices[0]
        return isSystemSupported(device.isDebuggable)
      }
      return false
    }


    @VisibleForTesting
    fun buildProfileableRequirementMessage(isProjectSupported: Boolean, isApiLevelSupported: Boolean, isSystemSupported: Boolean): String {
      val PROJECT_CRITERIA = "Android Gradle Plugin 7.3 or higher"
      val API_LEVEL_CRITERIA = "a device with API level 29 or higher"
      val NON_DEBUGGABLE_CRITERIA = "a system that is not debuggable. Example: A Google Play enabled emulator system image"

      var reasons = mutableListOf<String>()
      if (!isProjectSupported) reasons.add(PROJECT_CRITERIA)
      if (!isApiLevelSupported) reasons.add(API_LEVEL_CRITERIA)
      if (!isSystemSupported) reasons.add(NON_DEBUGGABLE_CRITERIA)

      var message = StringBuilder("“Run as profileable (low overhead)” is not available because it requires ")
      when (reasons.size) {
        0 -> assert(false)  // This method shouldn't be called with no unsupported reasons
        1 -> message.append(reasons[0])
        2 -> message.append(reasons[0]).append(" and ").append(reasons[1])
        else -> message.append(reasons[0]).append(", ").append(reasons[1]).append(", and ").append(reasons[2])
      }

      message.append(".<br><br>To proceed, either choose a device or emulator that meets the requirements above or run the app with " +
                     "\"Profiler: Run as debuggable (complete data)\". <a href=\"${SupportLevel.DOC_LINK}\">More Info</a>")
      return message.insert(0, "<html>").append("</html>").toString()
    }
  }
}