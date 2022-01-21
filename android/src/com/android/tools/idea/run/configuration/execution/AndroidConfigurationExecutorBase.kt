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
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.LaunchableAndroidDevice
import com.android.tools.idea.run.configuration.ComponentSpecificConfiguration
import com.android.tools.idea.run.configuration.isDebug
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider
import com.android.tools.idea.run.util.LaunchUtils
import com.android.tools.idea.stats.RunStats
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ExecutionUiService
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

abstract class AndroidConfigurationExecutorBase(protected val environment: ExecutionEnvironment) : RunProfileState {
  abstract val configuration: ComponentSpecificConfiguration
  protected val project = environment.project
  protected val appId
    get() = project.getProjectSystem().getApplicationIdProvider(configuration)?.packageName
            ?: throw RuntimeException("Cannot get ApplicationIdProvider")

  override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult? {
    throw RuntimeException("Unexpected code path")
  }

  @WorkerThread
  @Throws(ExecutionException::class)
  fun execute(stats: RunStats): RunContentDescriptor {
    val facet = AndroidFacet.getInstance(configuration.module!!)!!
    stats.setDebuggable(LaunchUtils.canDebugApp(facet))
    stats.setExecutor(environment.executor.id)
    stats.setPackage(appId)
    stats.setAppComponentType(configuration.componentType)

    ProgressManager.checkCanceled()
    ProgressIndicatorProvider.getGlobalProgressIndicator()?.text = "Waiting for all target devices to come online"
    val devices = getDevices(stats)
    devices.forEach { LaunchUtils.initiateDismissKeyguard(it) }
    stats.beginLaunchTasks()
    val runContentDescriptor = doOnDevices(devices)
    stats.endBeforeRunTasks()
    return runContentDescriptor
  }

  @VisibleForTesting
  @Throws(ExecutionException::class)
  abstract fun doOnDevices(devices: List<IDevice>): RunContentDescriptor

  private fun getDevices(stats: RunStats): List<IDevice> {
    val facet = AndroidFacet.getInstance(configuration.module!!)!!
    val provider = DeviceAndSnapshotComboBoxTargetProvider()
    val deployTarget = if (provider.requiresRuntimePrompt(project)) invokeAndWaitIfNeeded { provider.showPrompt(facet) }
    else provider.getDeployTarget(project)
    val deviceFutureList = deployTarget?.getDevices(facet) ?: throw ExecutionException(AndroidBundle.message("deployment.target.not.found"))

    // Record stat if we launched a device.
    stats.setLaunchedDevices(deviceFutureList.devices.any { it is LaunchableAndroidDevice })
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
    return devices
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

  fun getApplicationInstaller(): ApplicationInstaller {
    return ApplicationInstallerImpl(project)
  }

  internal fun createConsole(): ConsoleView {
    val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    Disposer.register(project, console)
    return console
  }

  @VisibleForTesting
  fun getDebugSessionStarter(): DebugSessionStarter {
    return DebugSessionStarter(environment)
  }

  protected fun getApkPaths(device: IDevice): List<String> {
    val apkProvider = project.getProjectSystem().getApkProvider(configuration) ?: throw ExecutionException(
      AndroidBundle.message("android.run.configuration.not.supported",
                            configuration.name)) // There is no test ApkInfo for AndroidWatchFaceConfiguration, thus it should be always single ApkInfo. Only App.
    return apkProvider.getApks(device).single().files.map { it.apkFile.path }
  }

  private fun startDebugger(device: IDevice, processHandler: AndroidProcessHandlerForDevices, console: ConsoleView): RunContentDescriptor {
    return getDebugSessionStarter().attachDebuggerToClient(device, processHandler, console)
  }

  protected fun createRunContentDescriptor(
    devices: List<IDevice>,
    processHandler: AndroidProcessHandlerForDevices,
    console: ConsoleView
  ): RunContentDescriptor {
    return if (environment.executor.isDebug) {
      processHandler.startNotify()
      startDebugger(devices.single(), processHandler, console)
    }
    else {
      invokeAndWaitIfNeeded {
        ExecutionUiService.getInstance().showRunContent(DefaultExecutionResult(console, processHandler), environment)
      }
    }
  }
}
