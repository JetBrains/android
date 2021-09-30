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
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.MultiLineReceiver
import com.android.tools.deployer.AdbClient
import com.android.tools.deployer.AdbInstaller
import com.android.tools.deployer.Deployer
import com.android.tools.deployer.DeployerException
import com.android.tools.deployer.DeployerOption
import com.android.tools.deployer.InstallOptions
import com.android.tools.deployer.MetricsRecorder
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.DeploymentService
import com.android.tools.idea.run.IdeService
import com.android.tools.idea.run.ProcessHandlerConsolePrinter
import com.android.tools.idea.run.configuration.AndroidWearConfiguration
import com.android.tools.idea.util.StudioPathManager
import com.intellij.debugger.engine.RemoteDebugProcessHandler
import com.intellij.debugger.ui.DebuggerPanelsManager
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.configurations.RemoteState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.android.util.AndroidBundle
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Session that is created for specific device for specific [ExecutionEnvironment] (configuration)
 *
 * It's created in [AndroidWearConfigurationExecutorBase] after user presses run/debug button for [AndroidWearConfiguration]
 * Via this class you can install app/activate component/attach debugger and so on during configuration execution for single device.
 *
 * This class already knows appId, chosen component name and so on from an environment.runProfile ([AndroidWearConfiguration]).
 */
class DeviceWearConfigurationExecutionSession(
  private val device: IDevice,
  private val environment: ExecutionEnvironment,
  var processHandler: ProcessHandler,
  val consolePrinter: ProcessHandlerConsolePrinter,
  private val indicator: ProgressIndicator
) {

  private val LOG = Logger.getInstance(DeviceWearConfigurationExecutionSession::class.java)
  private val configuration = environment.runProfile as AndroidWearConfiguration
  private val project = configuration.project
  private val appId = project.getProjectSystem().getApplicationIdProvider(configuration)?.packageName
                      ?: throw RuntimeException("Cannot get ApplicationIdProvider")

  @WorkerThread
  fun installAppOnDevice(): App {
    indicator.text = "Installing app"
    consolePrinter.stdout("Installing application: $appId")
    val deployer = getDeployer()
    val apkInfo = getApkInfo()
    val pathsToInstall = apkInfo.files.map { it.apkFile.path }

    try {
      val result = deployer.install(appId, pathsToInstall, getInstallOptions(device, appId), Deployer.InstallMode.DELTA)
      if (result.skippedInstall) {
        consolePrinter.stdout("App restart successful without requiring a re-install.")
      }
      return result.app
    }
    catch (e: DeployerException) {
      throw ExecutionException("Failed to install app. ${e.message ?: ""}", e.cause)
    }
  }

  @WorkerThread
  fun activateComponent(
    app: App,
    mode: AppComponent.Mode,
    shellReceiver: IShellOutputReceiver = AndroidLaunchReceiver(processHandler, consolePrinter)
  ) {
    indicator.text = "Starting ${configuration.componentName!!}"
    consolePrinter.stdout("Starting ${configuration.componentName!!}")

    try {
      app.activateComponent(configuration.componentType, configuration.componentName!!, mode, shellReceiver)
    }
    catch (e: DeployerException) {
      throw ExecutionException("Failed to start app component. ${e.message ?: ""}", e.cause)
    }
  }

  @WorkerThread
  fun attachDebuggerToClient() {
    waitForClient()
    val client = device.getClient(appId)
    val debugPort = client.debuggerListenPort.toString()
    val remoteConnection = RemoteConnection(true, "localhost", debugPort, false)
    val debugProcessHandler = RemoteDebugProcessHandler(project)
    consolePrinter.setProcessHandler(debugProcessHandler)
    processHandler.detachProcess()
    indicator.text = "Attaching debugger"
    invokeLater {
      val debugState = object : RemoteState {
        override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult {
          val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
          console.attachToProcess(debugProcessHandler)
          processHandler = debugProcessHandler
          return DefaultExecutionResult(console, debugProcessHandler)
        }

        override fun getRemoteConnection() = remoteConnection
      }

      consolePrinter.stdout("Attaching debugger to ${configuration.componentName!!}")

      DebuggerPanelsManager.getInstance(project).attachVirtualMachine(environment, debugState, remoteConnection, false)
      ?: throw ExecutionException("Unable to connect debugger to $appId")

      processHandler = debugProcessHandler
    }
  }

  @WorkerThread
  private fun waitForClient(): Client {
    indicator.text = "Waiting for a process to start"
    val appProcessCountDownLatch = CountDownLatch(1)
    val listener = object : AndroidDebugBridge.IDeviceChangeListener {
      override fun deviceConnected(device: IDevice) {}
      override fun deviceDisconnected(device: IDevice) {}

      override fun deviceChanged(changedDevice: IDevice, changeMask: Int) {
        if (changedDevice == device && changeMask and IDevice.CHANGE_CLIENT_LIST != 0) {
          val clients = changedDevice.clients
          if (clients.find { it.clientData.packageName == appId } != null) {
            appProcessCountDownLatch.countDown()
            AndroidDebugBridge.removeDeviceChangeListener(this)
          }
        }
      }
    }
    AndroidDebugBridge.addDeviceChangeListener(listener)

    if (device.getClient(appId) != null) {
      appProcessCountDownLatch.countDown()
      AndroidDebugBridge.removeDeviceChangeListener(listener)
    }

    consolePrinter.stdout("Waiting for process $appId to start")

    if (!appProcessCountDownLatch.await(15, TimeUnit.SECONDS)) {
      throw ExecutionException("Process $appId is not found. Aborting session.")
    }

    return device.getClient(appId)
  }

  private fun getDeployer(): Deployer {
    val logger = object : LogWrapper(LOG) {
      override fun info(msgFormat: String, vararg args: Any?) {
        // print to user console commands that we run on device
        if (msgFormat.contains("$ adb")) {
          consolePrinter.stdout(msgFormat)
        }
        super.info(msgFormat, *args)
      }
    }
    val adb = AdbClient(device, logger)
    val service = DeploymentService.getInstance(project)

    val option = DeployerOption.Builder()
      .setUseOptimisticSwap(StudioFlags.APPLY_CHANGES_OPTIMISTIC_SWAP.get())
      .setUseOptimisticResourceSwap(StudioFlags.APPLY_CHANGES_OPTIMISTIC_RESOURCE_SWAP.get())
      .setUseStructuralRedefinition(StudioFlags.APPLY_CHANGES_STRUCTURAL_DEFINITION.get())
      .setUseVariableReinitialization(StudioFlags.APPLY_CHANGES_VARIABLE_REINITIALIZATION.get())
      .enableCoroutineDebugger(StudioFlags.COROUTINE_DEBUGGER_ENABLE.get())
      .build()

    // Collection that will accumulate metrics for the deployment.
    val metrics = MetricsRecorder()

    val installer = AdbInstaller(getLocalInstaller(), adb, metrics.deployMetrics, logger, AdbInstaller.Mode.DAEMON)
    return Deployer(
      adb, service.deploymentCacheDatabase, service.dexDatabase, service.taskRunner,
      installer, IdeService(project), metrics, logger, option
    )
  }

  private fun getApkInfo(): ApkInfo {
    val apkProvider = project.getProjectSystem().getApkProvider(configuration)
                      ?: throw ExecutionException(AndroidBundle.message("android.run.configuration.not.supported", configuration.name))
    // There is no test ApkInfo for AndroidWatchFaceConfiguration, thus it should be always single ApkInfo. Only App.
    return apkProvider.getApks(device).single()
  }

  private fun getInstallOptions(device: IDevice, appId: String): InstallOptions {
    // All installations default to allow debuggable APKs
    val options = InstallOptions.builder().setAllowDebuggable()

    // Embedded devices (Android Things) have all runtime permissions granted since there's no requirement for user
    // interaction/display. However, regular installation will not grant some permissions until the next device reboot.
    // Installing with "-g" guarantees that the permissions are properly granted at install time.
    if (device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)) {
      options.setGrantAllPermissions()
    }

    // Skip verification if possible.
    options.setSkipVerification(device, appId)

    if (!configuration.installFlags.isEmpty()) {
      options.setUserInstallOptions(configuration.installFlags.trim().split("\\s+").toTypedArray())
    }

    return options.build()
  }

  private fun getLocalInstaller(): String? {
    val path = if (StudioPathManager.isRunningFromSources()) {
      // Development mode
      File(StudioPathManager.getSourcesRoot(), "bazel-bin/tools/base/deploy/installer/android-installer")
    }
    else {
      File(PathManager.getHomePath(), "plugins/android/resources/installer")
    }
    return path.absolutePath
  }

  fun executeShellCommand(command: String, receiver: IShellOutputReceiver, maxTimeToOutputResponse: Long, maxTimeUnits: TimeUnit) {
    device.executeShellCommand(command, receiver, maxTimeToOutputResponse, maxTimeUnits)
  }
}

private class AndroidLaunchReceiver(
  private val processHandler: ProcessHandler,
  private val consolePrinter: ConsolePrinter
) : MultiLineReceiver() {
  override fun isCancelled() = processHandler.isProcessTerminated

  override fun processNewLines(lines: Array<String>) = lines.forEach { consolePrinter.stdout(it) }
}