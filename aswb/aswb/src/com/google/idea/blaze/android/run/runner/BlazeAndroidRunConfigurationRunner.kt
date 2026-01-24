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

import com.android.tools.idea.editors.liveedit.LiveEditService
import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.execution.common.AndroidConfigurationExecutorRunProfileState
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.ApplicationDeployer
import com.android.tools.idea.execution.common.ComponentLaunchOptions
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.run.ApkProvisionException
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
import com.google.idea.blaze.android.run.BazelAndroidRunContext
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator
import com.google.idea.blaze.base.command.BlazeInvocationContext
import com.google.idea.blaze.base.experiments.ExperimentScope
import com.google.idea.blaze.base.issueparser.BlazeIssueParser
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.Scope
import com.google.idea.blaze.base.scope.ScopedFunction
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.google.idea.blaze.base.scope.output.StatusOutput
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope
import com.google.idea.blaze.base.scope.scopes.ProgressIndicatorScope
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BlazeUserSettings
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.google.idea.blaze.base.toolwindow.Task
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import java.io.IOException
import java.util.concurrent.CancellationException
import org.jetbrains.android.util.AndroidBundle

/**
 * Supports the execution. Used by both android_binary and android_test.
 *
 * Builds the APK and installs it, launches and debug tasks, etc.
 */
class BlazeAndroidRunConfigurationRunner(
  private val launchStrategy: BlazeAndroidDeployAndLaunchStrategy,
  private val runConfig: BlazeCommandRunConfiguration,
  private val apkBuildStep: ApkBuildStep,
  private val deployInfoExtractor: DeployInfoExtractor,
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

    return AndroidConfigurationExecutorRunProfileState(
      LazilyInitializedDelegatingBlazeAndroidConfigurationExecutor(runConfig) {
        val runContext =
          executeUnderBuildProgress(environment) { context ->
            val buildOutputs = apkBuildStep.build(context)
            val deployInfo = extractDeployInfo(context, environment.project, buildOutputs)

            launchStrategy.createBlazeAndroidRunContext(environment, deployInfo, runConfig)
          }
          ?: throw ExecutionException("APK build failed")

        val state = runConfig.handler.getState()

        val applicationProjectContext = runContext.applicationProjectContext
        val wearLaunchOptions = (state as? BlazeAndroidBinaryRunConfigurationState)?.currentWearLaunchOptions
        if (wearLaunchOptions != null) {
          getWearExecutor(wearLaunchOptions, environment, deployTarget, runContext)
        }
        else {
          val launchOptions = launchOptionsBuilder.build()
          BlazeAndroidConfigurationExecutor(
            runContext.consoleProvider,
            applicationProjectContext,
            environment,
            deviceFutures,
            runContext,
            launchStrategy,
            launchOptions,
            LiveEditService.getInstance(environment.project)
          )
        }
      }
    )
  }

  @Throws(ExecutionException::class)
  private fun getWearExecutor(
    launchOptions: ComponentLaunchOptions,
    env: ExecutionEnvironment,
    deployTarget: DeployTarget,
    runContext: BazelAndroidRunContext,
  ): AndroidConfigurationExecutor {
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

    return when (launchOptions) {
      is TileLaunchOptions -> ::AndroidTileConfigurationExecutor
      is WatchFaceLaunchOptions -> ::AndroidWatchFaceConfigurationExecutor
      is ComplicationLaunchOptions -> ::AndroidComplicationConfigurationExecutor
      else -> error("Unknown launch options " + launchOptions.javaClass.getName())
    }(
      env,
      deviceFutures,
      settings,
      runContext.apkProvider,
      runContext.applicationProjectContext,
      deployer
    )
  }

  override fun executeBeforeRunTask(environment: ExecutionEnvironment): Boolean {
    return true
  }

  private fun <T> executeUnderBuildProgress(
    environment: ExecutionEnvironment,
    buildInvocation: (context: BlazeContext) -> T,
  ): T? {
    val project = environment.project
    val settings = BlazeUserSettings.getInstance()
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
        try {
          val buildFuture =
            ProgressiveTaskWithProgressIndicator.builder(
              project,
              "Executing ${Blaze.buildSystemName(project)} apk build"
            )
              .submitTaskWithResult { progressIndicator ->
                context.push(ProgressIndicatorScope(progressIndicator))
                buildInvocation(context)
              }
          return@ScopedFunction Futures.getChecked(buildFuture, ExecutionException::class.java)
        }
        catch (e: ExecutionException) {
          context.setHasError()
        }
        catch (e: CancellationException) {
          context.setCancelled()
        }
        catch (e: Exception) {
          LOG.error(e)
        }
        return@ScopedFunction null
      })
  }

  @Throws(ExecutionException::class)
  private fun extractDeployInfo(
    context: BlazeContext,
    project: Project,
    buildOutputs: BlazeBuildOutputs,
  ): BlazeAndroidDeployInfo {
    context.output(StatusOutput("Deployment information parsed from build artifacts."))

    return try {
      deployInfoExtractor.extract(
        project,
        buildOutputs,
        context
      )
    }
    catch (e: ApkProvisionException) {
      LOG.warn("Unexpected error while retrieving deploy info", e)
      val message = "Error retrieving deployment info from build results: " + e.message
      IssueOutput.error(message).submit(context)
      throw ExecutionException(message, e)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(BlazeAndroidRunConfigurationRunner::class.java)

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

/**
 * A delegating executor that initializes the actual [AndroidConfigurationExecutor] lazily.
 * This is used to delay the creation of the executor until the build step is complete.
 */
private class LazilyInitializedDelegatingBlazeAndroidConfigurationExecutor(
  override val configuration: RunConfiguration,
  factory: () -> AndroidConfigurationExecutor,
) : AndroidConfigurationExecutor {

  private val delegate by lazy(mode = LazyThreadSafetyMode.PUBLICATION, factory)
  override fun run(indicator: ProgressIndicator): RunContentDescriptor = delegate.run(indicator)
  override fun debug(indicator: ProgressIndicator): RunContentDescriptor = delegate.debug(indicator)
}