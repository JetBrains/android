/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.android.tools.idea.editors.liveedit.LiveEditService.Companion.getInstance
import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.execution.common.AndroidConfigurationExecutorRunProfileState
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.ApplicationDeployer
import com.android.tools.idea.execution.common.ComponentLaunchOptions
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.configuration.execution.AndroidComplicationConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.AndroidTileConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.AndroidWatchFaceConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.ApplicationDeployerImpl
import com.android.tools.idea.run.configuration.execution.ComplicationLaunchOptions
import com.android.tools.idea.run.configuration.execution.TileLaunchOptions
import com.android.tools.idea.run.configuration.execution.WatchFaceLaunchOptions
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.run.editor.DeployTargetState
import com.google.common.util.concurrent.Futures
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector.DeviceSession
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator
import com.google.idea.blaze.base.command.BlazeInvocationContext
import com.google.idea.blaze.base.experiments.ExperimentScope
import com.google.idea.blaze.base.issueparser.BlazeIssueParser
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner
import com.google.idea.blaze.base.scope.Scope
import com.google.idea.blaze.base.scope.ScopedFunction
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope
import com.google.idea.blaze.base.scope.scopes.ProgressIndicatorScope
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BlazeUserSettings
import com.google.idea.blaze.base.toolwindow.Task
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import java.util.concurrent.CancellationException
import org.jetbrains.android.util.AndroidBundle

/**
 * Supports the execution. Used by both android_binary and android_test.
 *
 * Builds the APK and installs it, launches and debug tasks, etc.
 */
class BlazeAndroidRunConfigurationRunner(
  private val runContext: BlazeAndroidRunContext,
  private val launchStrategy: BlazeAndroidDeployAndLaunchStrategy,
  private val runConfig: BlazeCommandRunConfiguration,
) : BlazeCommandRunConfigurationRunner {
  @Throws(ExecutionException::class)
  override fun getRunProfileState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
    val project = environment.project
    val isDebug = executor is DefaultDebugExecutor

    val deviceSelector = launchStrategy.getDeviceSelector()
    val deviceSession =
      deviceSelector.getDevice(project, executor, environment, isDebug, runConfig.uniqueID) ?: return null

    val deployTarget = deviceSession.deployTarget
    if (deployTarget.hasCustomRunProfileState(executor)) {
      return deployTarget.getRunProfileState(executor, environment, DeployTargetState.DEFAULT_STATE)
    }

    val deviceFutures = deviceSession.deviceFutures
                        ?: // The user deliberately canceled, or some error was encountered and exposed by the chooser.
                        // Quietly exit.
                        return null

    if (deviceFutures.get().isEmpty()) {
      throw ExecutionException(AndroidBundle.message("deployment.target.not.found"))
    }

    if (isDebug) {
      val error = canDebug(deviceFutures, runConfig.singleTargetPattern ?: "(unknown)")
      if (error != null) {
        throw ExecutionException(error)
      }
    }

    val launchOptionsBuilder = LaunchOptions.builder()
    launchStrategy.augmentLaunchOptions(launchOptionsBuilder)

    // Store the run context on the execution environment so before-run tasks can access it.
    environment.putCopyableUserData(RUN_CONTEXT_KEY, runContext)
    environment.putCopyableUserData(DEVICE_SESSION_KEY, deviceSession)

    val state = runConfig.handler.getState()

    val applicationProjectContext = runContext.getApplicationProjectContext()
    val wearLaunchOptions = (state as? BlazeAndroidBinaryRunConfigurationState)?.currentWearLaunchOptions
    if (wearLaunchOptions != null) {
      return getWearExecutor(wearLaunchOptions, environment, deployTarget)
    }

    val launchOptions = launchOptionsBuilder.build()
    val runner =
      BlazeAndroidConfigurationExecutor(
        runContext.getConsoleProvider(),
        applicationProjectContext,
        environment,
        deviceFutures,
        BlazeAndroidLaunchTasksProvider(project, runContext, launchStrategy, launchOptions),
        launchOptions,
        runContext.getApkProvider(),
        getInstance(environment.project)
      )
    return AndroidConfigurationExecutorRunProfileState(runner)
  }

  @Throws(ExecutionException::class)
  private fun getWearExecutor(
    launchOptions: ComponentLaunchOptions, env: ExecutionEnvironment, deployTarget: DeployTarget,
  ): RunProfileState {
    val settings: AppRunSettings =
      object : AppRunSettings {
        override val deployOptions: DeployOptions
          get() = DeployOptions(
            disabledDynamicFeatures = emptyList(),
            pmInstallFlags = "",
            installOnAllUsers = true,
            alwaysInstallWithPm = true,
            allowAssumeVerified = false
          )

        override val componentLaunchOptions: ComponentLaunchOptions
          get() = launchOptions
      }

    val deviceFutures = deployTarget.launchDevices(env.project)

    val deployer: ApplicationDeployer =
      ApplicationDeployerImpl(env.project, RunStats.from(env))

    val configurationExecutor: AndroidConfigurationExecutor =
      when (launchOptions) {
        is TileLaunchOptions ->
          AndroidTileConfigurationExecutor(
            env,
            deviceFutures,
            settings,
            runContext.getApkProvider(),
            runContext.getApplicationProjectContext(),
            deployer
          )

        is WatchFaceLaunchOptions ->
          AndroidWatchFaceConfigurationExecutor(
            env,
            deviceFutures,
            settings,
            runContext.getApkProvider(),
            runContext.getApplicationProjectContext(),
            deployer
          )

        is ComplicationLaunchOptions ->
          AndroidComplicationConfigurationExecutor(
            env,
            deviceFutures,
            settings,
            runContext.getApkProvider(),
            runContext.getApplicationProjectContext(),
            deployer
          )

        else ->
          error("Unknown launch options " + launchOptions.javaClass.getName())
      }

    return AndroidConfigurationExecutorRunProfileState(configurationExecutor)
  }

  override fun executeBeforeRunTask(environment: ExecutionEnvironment): Boolean {
    val project = environment.project
    val settings = BlazeUserSettings.getInstance()
    val runData = runContext
    return Scope.root(
      ScopedFunction { context ->
        context
          .push(ProblemsViewScope(project, settings.showProblemsViewOnRun))
          .push(ExperimentScope())
          .push(ToolWindowScope.Builder(project, Task(project, "Build apk"))
              .setPopupBehavior(settings.showBlazeConsoleOnRun)
              .setIssueParsers(
                BlazeIssueParser.defaultIssueParsers(
                  project,
                  WorkspaceRoot.fromProject(project),
                  BlazeInvocationContext.ContextType.BeforeRunTask
                )
              )
              .build()
          )
          .push(IdeaLogScope())
        val deviceSession = environment.getCopyableUserData(DEVICE_SESSION_KEY)

        val buildStep = runData.getBuildStep()
        try {
          val buildFuture =
            ProgressiveTaskWithProgressIndicator.builder(
              project,
              "Executing ${Blaze.buildSystemName(project)} apk build"
            )
              .submitTaskWithResult { progressIndicator ->
                context.push(ProgressIndicatorScope(progressIndicator))
                buildStep.build(context, deviceSession)
              }
          Futures.getChecked(buildFuture, ExecutionException::class.java)
        } catch (e: ExecutionException) {
          context.setHasError()
        } catch (e: CancellationException) {
          context.setCancelled()
        } catch (e: Exception) {
          LOG.error(e)
          return@ScopedFunction false
        }
        context.shouldContinue()
      })
  }

  companion object {
    private val LOG = Logger.getInstance(BlazeAndroidRunConfigurationRunner::class.java)

    private val RUN_CONTEXT_KEY = Key.create<BlazeAndroidRunContext>("blaze.run.context")
    val DEVICE_SESSION_KEY: Key<DeviceSession> = Key.create<DeviceSession>("blaze.device.session")

    private fun canDebug(
      deviceFutures: DeviceFutures, runConfigName: String,
    ): String? {
      // TODO: b/475173918 - Implement isDebuggable check for a given target.
      // The old check used to say the app is debuggable or the device is debuggable, but the first check would always return true.
      // ASwB currently relies on error messages returned by deployment.
      return null
    }

  }
}
