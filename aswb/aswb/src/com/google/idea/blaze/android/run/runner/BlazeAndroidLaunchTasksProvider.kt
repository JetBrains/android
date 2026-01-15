/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.runner

import com.android.ddmlib.IDevice
import com.android.tools.deployer.ApkVerifierTracker
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.profilers.AndroidProfilerLaunchTaskContributor
import com.android.tools.idea.run.LaunchOptions
import com.google.idea.blaze.android.run.BazelAndroidRunContext
import com.google.idea.blaze.android.run.binary.UserIdHelper
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession

/** Normal launch tasks provider. #api4.1  */
class BlazeAndroidLaunchTasksProvider(
  private val project: Project,
  private val runContext: BazelAndroidRunContext,
  private val launchStrategy: BlazeAndroidDeployAndLaunchStrategy,
  private val launchOptions: LaunchOptions
) : BlazeLaunchTasksProvider {
  @Throws(ExecutionException::class)
  override fun getTasks(device: IDevice, isDebug: Boolean): List<BlazeLaunchTask> {
    return buildList {
      val launchTasks = this@buildList
      val packageName = runContext.applicationProjectContext.applicationId

      val userId = launchStrategy.getUserId(device)

      // NOTE: Task for opening the profiler tool-window should come before deployment
      // to ensure the tool-window opens correctly. This is required because starting
      // the profiler session requires the tool-window to be open.
      if (AndroidProfilerLaunchTaskContributor.isProfilerLaunch(runContext.executor)) {
        launchTasks.add(BlazeAndroidOpenProfilerWindowTask(project))
      }

      if (launchOptions.isDeploy) {
        val userIdFlags = UserIdHelper.getFlagsFromUserId(userId)
        val skipVerification =
          ApkVerifierTracker.getSkipVerificationInstallationFlag(device, packageName)
        val pmInstallOption = if (skipVerification != null) "$userIdFlags $skipVerification" else userIdFlags
        val deployOptions =
          DeployOptions(
            disabledDynamicFeatures = emptyList(),
            pmInstallFlags = pmInstallOption,
            installOnAllUsers = false,
            alwaysInstallWithPm = false,
            allowAssumeVerified = false
          )
        val deployTasks =
          launchStrategy.getDeployTasks(runContext, device, deployOptions)
        launchTasks.addAll(deployTasks)
      }

      if (isDebug) {
        launchTasks.add(
          CheckApkDebuggableTask(project, runContext.buildStep.getDeployInfo())
        )
      }

      val amStartOptions = mutableListOf<String>()
      amStartOptions.add(launchStrategy.getAmStartOptions())
      if (AndroidProfilerLaunchTaskContributor.isProfilerLaunch(runContext.executor)) {
        amStartOptions.add(
          AndroidProfilerLaunchTaskContributor.getAmStartOptions(
            project,
            packageName,
            runContext.profileState,
            device,
            runContext.executor
          )
        )
      }
      val appLaunchTask =
        launchStrategy.getApplicationLaunchTask(
          runContext, isDebug, userId, amStartOptions.joinToString(separator = " ")
        )
      if (appLaunchTask != null) {
        launchTasks.add(appLaunchTask)
        // TODO(arvindanekal): the live edit api changed and we cannot get the apk here to create
        // live
        // edit; the live edit team or Arvind need to fix this
      }
    }
  }

  @Throws(ExecutionException::class)
  override fun startDebugSession(
    environment: ExecutionEnvironment,
    device: IDevice,
    console: ConsoleView,
    indicator: ProgressIndicator
  ): XDebugSession {
    // Do not get debugger state directly from the debugger itself.
    // See BlazeAndroidDebuggerService#getDebuggerState for an explanation.
    val isNativeDebuggingEnabled = isNativeDebuggingEnabled(launchOptions)
    val debuggerService = BlazeAndroidDebuggerService.getInstance(project)
    val debugger = debuggerService.getDebugger(isNativeDebuggingEnabled) ?: throw ExecutionException("Can't find AndroidDebugger for launch")
    val debuggerState = debuggerService.getDebuggerState(debugger) ?: throw ExecutionException("Can't find AndroidDebuggerState for launch")
    if (isNativeDebuggingEnabled) {
      val deployInfo = runContext.buildStep.getDeployInfo()
      debuggerService.configureNativeDebugger(debuggerState, deployInfo)
    }

    return launchStrategy.startDebuggerSession(
      runContext, debugger, debuggerState, environment, device, console, indicator
    ) ?: throw ExecutionException("Failed to start debugger")
  }

  private fun isNativeDebuggingEnabled(launchOptions: LaunchOptions): Boolean {
    val flag = launchOptions.getExtraOption(NATIVE_DEBUGGING_ENABLED)
    return flag is Boolean && flag
  }

  companion object {
    const val NATIVE_DEBUGGING_ENABLED: String = "NATIVE_DEBUGGING_ENABLED"
  }
}