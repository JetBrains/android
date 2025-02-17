/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiReceiver
import com.android.tools.deployer.DeployerException
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.WatchFace
import com.android.tools.deployer.model.component.WearComponent.CommandResultReceiver
import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.ApplicationDeployer
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.configuration.AndroidDeclarativeWatchFaceConfiguration
import com.intellij.execution.ExecutionException
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll

/**
 * The minimum version required for the `com.google.android.wearable.app.DEBUG_SURFACE` broadcast receiver
 * running on the device to support setting a declarative watch face.
 */
private const val WATCH_FACE_MIN_DEBUG_SURFACE_VERSION = 4

/**
 * ADB Shell command that sets a declarative watch face on a device. The application ID of the declarative
 * watch face must be concatenated at the end of the command.
 */
private const val SET_DECLARATIVE_WATCH_FACE =
  "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-watchface --es watchFaceId" // + appId

private val LOG = Logger.getInstance(AndroidDeclarativeWatchFaceConfigurationExecutor::class.java)

class AndroidDeclarativeWatchFaceConfigurationExecutor(
  private val environment: ExecutionEnvironment,
  private val deviceFutures: DeviceFutures,
  private val applicationIdProvider: ApplicationIdProvider,
  private val apkProvider: ApkProvider,
  private val applicationDeployer: ApplicationDeployer,
) : AndroidConfigurationExecutor {

  override val configuration = environment.runProfile as AndroidDeclarativeWatchFaceConfiguration

  private val deployOptions =
    DeployOptions(
      disabledDynamicFeatures = emptyList(),
      pmInstallFlags = "",
      installOnAllUsers = true,
      alwaysInstallWithPm = true,
      allowAssumeVerified = false,
    )

  override fun run(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable {
    val applicationId = applicationIdProvider.packageName
    val devices = getDevices(environment, deviceFutures, indicator)
    RunStats.from(environment).setPackage(applicationId)
    val console = createConsole()
    val processHandler =
      AndroidProcessHandler(applicationId, getStopWatchFaceCallback(console, isDebug = false))

    val onDevice = { device: IDevice ->
      LOG.info("Launching on device ${device.name}")

      val result =
        try {
          // ApkProvider provides multiple ApkInfo only for instrumented tests.
          val app = apkProvider.getApks(device).single()
          val containsMakeBeforeRun = configuration.beforeRunTasks.any { it.isEnabled }

          applicationDeployer.fullDeploy(
            device,
            app,
            deployOptions,
            containsMakeBeforeRun,
            indicator,
          )
        } catch (e: DeployerException) {
          throw ExecutionException(
            "Failed to install app '$applicationId'. ${e.details.orEmpty()}",
            e,
          )
        }
      launch(device, result.app, console, indicator)
      processHandler.addTargetDevice(device)
    }

    devices.map { async { onDevice(it) } }.joinAll()

    AndroidSessionInfo.create(processHandler, devices, applicationId)
    createRunContentDescriptor(processHandler, console, environment)
  }

  override fun debug(indicator: ProgressIndicator): RunContentDescriptor {
    throw RuntimeException("Unsupported operation")
  }

  private fun launch(
    device: IDevice,
    app: App,
    console: ConsoleView,
    indicator: ProgressIndicator,
  ) {
    val version = device.getWearDebugSurfaceVersion(indicator)
    if (version < WATCH_FACE_MIN_DEBUG_SURFACE_VERSION) {
      throw SurfaceVersionException(
        WATCH_FACE_MIN_DEBUG_SURFACE_VERSION,
        version,
        device.isEmulator,
      )
    }

    setWatchFace(app, indicator, device)
    showWatchFace(device, console, indicator)
  }

  private fun setWatchFace(app: App, indicator: ProgressIndicator, device: IDevice) {
    indicator.checkCanceled()
    indicator.text = "Launching the watch face"

    val outputReceiver = RecordOutputReceiver { indicator.isCanceled == true }
    try {
      val resultReceiver = CommandResultReceiver()
      val multiReceiver = MultiReceiver(resultReceiver, outputReceiver)

      device.executeShellCommand(
        "$SET_DECLARATIVE_WATCH_FACE ${app.appId}",
        multiReceiver,
        15,
        TimeUnit.SECONDS,
      )

      if (resultReceiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
        throw DeployerException.componentActivationException(
          String.format("Invalid Success code `%d`", resultReceiver.resultCode)
        )
      }
    } catch (ex: Exception) {
      throw ExecutionException(
        "Error while launching declarative watch face, message: ${outputReceiver.getOutput()}",
        ex,
      )
    }
  }

  private fun showWatchFace(device: IDevice, console: ConsoleView, indicator: ProgressIndicator) {
    indicator.checkCanceled()
    indicator.text = "Showing the watch face"

    val resultReceiver = CommandResultReceiver()
    device.executeShellCommand(
      WatchFace.ShellCommand.SHOW_WATCH_FACE,
      console,
      resultReceiver,
      indicator = indicator,
    )
    if (resultReceiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
      console.printlnError(
        "Warning: Launch was successful, but you may need to bring up the watch face manually"
      )
    }
  }

  private fun createConsole(): ConsoleView {
    val console = TextConsoleBuilderFactory.getInstance().createBuilder(environment.project).console
    Disposer.register(environment.project, console)
    return console
  }
}
