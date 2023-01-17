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
import com.android.tools.deployer.DeployerException
import com.android.tools.deployer.model.App
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.ApplicationDeployer
import com.android.tools.idea.execution.common.ApplicationTerminator
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.configuration.isDebug
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.run.util.LaunchUtils
import com.android.tools.idea.stats.RunStats
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.impl.XDebugSessionImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.concurrency.AsyncPromise
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

abstract class AndroidConfigurationExecutorBase(
  protected val environment: ExecutionEnvironment,
  override val deployTarget: DeployTarget,
  protected val appRunSettings: AppRunSettings,
  protected val applicationIdProvider: ApplicationIdProvider,
  protected val apkProvider: ApkProvider
) : AndroidConfigurationExecutor {

  private val LOG = Logger.getInstance(this::class.java)

  override val configuration: RunConfiguration = environment.runProfile as RunConfiguration

  protected val project = environment.project
  protected val appId
    get() = applicationIdProvider.packageName

  protected val isDebug = environment.executor.isDebug

  @WorkerThread
  override fun run(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable(indicator) {
    RunStats.from(environment).beginLaunchTasks()

    val devices = getDevices(indicator, RunStats.from(environment))
    val console = createConsole()
    val processHandler = AndroidProcessHandler(project, appId, getStopCallback(console, false))

    val applicationInstaller = getApplicationDeployer(console)

    val onDevice = { device: IDevice ->
      LOG.info("Launching on device ${device.name}")

      terminatePreviousAppInstance(device)

      val result = try {
        // ApkProvider provides multiple ApkInfo only for instrumented tests.
        val app = apkProvider.getApks(device).single()
        applicationInstaller.fullDeploy(device, app, appRunSettings.deployOptions, indicator)
      }
      catch (e: DeployerException) {
        throw ExecutionException("Failed to install app '$appId'. ${e.details.orEmpty()}", e)
      }
      launch(device, result.app, console, false, indicator)
      processHandler.addTargetDevice(device)
    }

    devices.map { async { onDevice(it) } }.joinAll()

    RunStats.from(environment).endLaunchTasks()

    createRunContentDescriptor(processHandler, console, environment)
  }

  @WorkerThread
  override fun debug(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable(indicator) {
    RunStats.from(environment).beginLaunchTasks()
    AsyncPromise<RunContentDescriptor>()

    val devices = getDevices(indicator, RunStats.from(environment))
    if (devices.size > 1) {
      throw ExecutionException("Debugging is allowed only for single device")
    }

    val console = createConsole()
    val device = devices.single()

    terminatePreviousAppInstance(device)

    // ApkProvider provides multiple ApkInfo only for instrumented tests.
    val app = apkProvider.getApks(device).single()
    val deployResult = getApplicationDeployer(console).fullDeploy(device, app, appRunSettings.deployOptions, indicator)

    val runContentDescriptorDeferred = async {
      startDebugSession(device, console, indicator).runContentDescriptor
    }

    launch(device, deployResult.app, console, true, indicator)

    try {
      val runContentDescriptor = runContentDescriptorDeferred.await()
      RunStats.from(environment).endLaunchTasks()
      return@runBlockingCancellable runContentDescriptor
    }
    catch (e: ExecutionException) {
      if (!device.isOffline) {
        try {
          getStopCallback(console, isDebug).invoke(device)
        }
        catch (e: Exception) {
          LOG.warn(e)
        }
        try {
          ApplicationTerminator(device, appId).killApp()
        }
        catch (e: Exception) {
          LOG.warn(e)
        }
      }
      throw e
    }
  }

  @Throws(ExecutionException::class)
  protected abstract fun getStopCallback(console: ConsoleView, isDebug: Boolean): (IDevice) -> Unit

  @VisibleForTesting
  @Throws(ExecutionException::class)
  abstract fun launch(device: IDevice, app: App, console: ConsoleView, isDebug: Boolean, indicator: ProgressIndicator)

  protected abstract fun startDebugSession(device: IDevice, console: ConsoleView, indicator: ProgressIndicator): XDebugSessionImpl

  @Throws(ExecutionException::class)
  private fun getDevices(indicator: ProgressIndicator, stats: RunStats): List<IDevice> {
    indicator.checkCanceled()
    indicator.text = "Waiting for all target devices to come online"

    val deviceFutureList = deployTarget.getDevices(project) ?: throw ExecutionException(
      AndroidBundle.message("deployment.target.not.found"))

    val devices = deviceFutureList.get().map {
      ProgressManager.checkCanceled()
      stats.beginWaitForDevice()
      val device = waitForDevice(it)
      stats.endWaitForDevice(device)
      device
    }
    if (devices.isEmpty()) {
      throw ExecutionException(AndroidBundle.message("deployment.target.not.found"))
    }
    return devices.onEach { LaunchUtils.initiateDismissKeyguard(it) }
  }

  private fun terminatePreviousAppInstance(device: IDevice) {
    val terminator = ApplicationTerminator(device, appId)
    if (!terminator.killApp()) {
      throw ExecutionException("Could not terminate running app $appId")
    }
  }

  private fun waitForDevice(deviceFuture: ListenableFuture<IDevice>): IDevice {
    val start = System.currentTimeMillis()
    val timeoutMs = TimeUnit.MINUTES.toMillis(1)
    while (System.currentTimeMillis() - start < timeoutMs) {
      try {
        return deviceFuture.get(1, TimeUnit.SECONDS)
      }
      catch (ignored: TimeoutException) {
        // Let's check the cancellation request then continue to wait for a device again.
        ProgressManager.checkCanceled()
      }
      catch (e: InterruptedException) {
        throw ExecutionException("Interrupted while waiting for device", e)
      }
      catch (e: java.util.concurrent.ExecutionException) {
        throw ExecutionException("Error while waiting for device: " + e.cause!!.message, e)
      }
    }
    throw ExecutionException("Device didn't come online")
  }

  @Throws(ExecutionException::class)
  open fun getApplicationDeployer(console: ConsoleView): ApplicationDeployer {
    return ApplicationDeployerImpl(project, console)
  }

  private fun createConsole(): ConsoleView {
    val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    Disposer.register(project, console)
    return console
  }
}
