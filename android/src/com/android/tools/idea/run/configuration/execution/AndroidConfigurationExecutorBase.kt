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
import com.android.ddmlib.IDevice
import com.android.tools.deployer.Activator
import com.android.tools.deployer.DeployerException
import com.android.tools.deployer.model.App
import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.ApplicationDeployer
import com.android.tools.idea.execution.common.ApplicationTerminator
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.DeviceFutures
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.impl.XDebugSessionImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll

abstract class AndroidConfigurationExecutorBase(
  protected val environment: ExecutionEnvironment,
  private val deviceFutures: DeviceFutures,
  protected val appRunSettings: AppRunSettings,
  protected val applicationIdProvider: ApplicationIdProvider,
  protected val apkProvider: ApkProvider,
  protected val applicationContext: ApplicationProjectContext,
  protected val applicationDeployer: ApplicationDeployer
) : AndroidConfigurationExecutor {

  private val LOG = Logger.getInstance(this::class.java)
  val logger = LogWrapper(LOG)

  override val configuration: RunConfiguration = environment.runProfile as RunConfiguration

  protected val project = environment.project

  internal fun getActivator(app: App) : Activator {
    return Activator(app, logger)
  }

  @WorkerThread
  override fun run(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable {
    val (applicationId, devices) = getApplicationIdAndDevices(environment, deviceFutures, applicationIdProvider, indicator)
    RunStats.from(environment).setPackage(applicationId)
    val console = createConsole()
    val processHandler = AndroidProcessHandler(applicationId, getStopCallback(console, applicationId, false))

    val onDevice = { device: IDevice ->
      LOG.info("Launching on device ${device.name}")

      val result = try {
        // ApkProvider provides multiple ApkInfo only for instrumented tests.
        val app = apkProvider.getApks(device).single()
        applicationDeployer.fullDeploy(device, app, appRunSettings.deployOptions, indicator)
      }
      catch (e: DeployerException) {
        throw ExecutionException("Failed to install app '$applicationId'. ${e.details.orEmpty()}", e)
      }
      launch(device, result.app, console, false, indicator)
      processHandler.addTargetDevice(device)
    }

    devices.map { async { onDevice(it) } }.joinAll()

    AndroidSessionInfo.create(processHandler, devices, applicationId)
    createRunContentDescriptor(processHandler, console, environment)
  }

  @WorkerThread
  override fun debug(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable {
    val (applicationId, devices) = getApplicationIdAndDevices(environment, deviceFutures, applicationIdProvider, indicator)
    RunStats.from(environment).setPackage(applicationId)
    if (devices.size > 1) {
      throw ExecutionException("Debugging is allowed only for single device")
    }

    val console = createConsole()
    val device = devices.single()

    // ApkProvider provides multiple ApkInfo only for instrumented tests.
    val app = apkProvider.getApks(device).single()
    val deployResult = applicationDeployer.fullDeploy(device, app, appRunSettings.deployOptions, indicator)

    val runContentDescriptorDeferred = async {
      startDebugSession(device, applicationContext, console, indicator).runContentDescriptor
    }

    launch(device, deployResult.app, console, true, indicator)

    try {
      runContentDescriptorDeferred.await()
    }
    catch (e: ExecutionException) {
      if (!device.isOffline) {
        try {
          getStopCallback(console, applicationId, true).invoke(device)
        }
        catch (e: Exception) {
          LOG.warn(e)
        }
        try {
          ApplicationTerminator(device, applicationId).killApp()
        }
        catch (e: Exception) {
          LOG.warn(e)
        }
      }
      throw e
    }
  }

  @Throws(ExecutionException::class)
  protected abstract fun getStopCallback(console: ConsoleView, applicationId: String, isDebug: Boolean): (IDevice) -> Unit

  @VisibleForTesting
  @Throws(ExecutionException::class)
  abstract fun launch(device: IDevice, app: App, console: ConsoleView, isDebug: Boolean, indicator: ProgressIndicator)

  protected abstract suspend fun startDebugSession(
    device: IDevice,
    applicationContext: ApplicationProjectContext,
    console: ConsoleView, indicator: ProgressIndicator
  ): XDebugSessionImpl


  private fun createConsole(): ConsoleView {
    val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    Disposer.register(project, console)
    return console
  }
}
