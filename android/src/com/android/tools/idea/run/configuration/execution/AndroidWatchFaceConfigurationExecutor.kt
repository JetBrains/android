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
import com.android.ddmlib.MultiReceiver
import com.android.tools.deployer.DeployerException
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.deployer.model.component.WatchFace.ShellCommand.SHOW_WATCH_FACE
import com.android.tools.deployer.model.component.WatchFace.ShellCommand.UNSET_WATCH_FACE
import com.android.tools.deployer.model.component.WearComponent.CommandResultReceiver
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfiguration
import com.intellij.execution.ExecutionException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import org.jetbrains.concurrency.Promise
import java.util.concurrent.TimeUnit

private const val WATCH_FACE_MIN_DEBUG_SURFACE_VERSION = 2

class AndroidWatchFaceConfigurationExecutor(environment: ExecutionEnvironment) : AndroidConfigurationExecutorBase(environment) {
  override val configuration = environment.runProfile as AndroidWatchFaceConfiguration

  @WorkerThread
  override fun doOnDevices(devices: List<IDevice>): Promise<RunContentDescriptor> {
    val isDebug = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID
    if (isDebug && devices.size > 1) {
      throw ExecutionException("Debugging is allowed only for single device")
    }
    val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    Disposer.register(project, console)
    val applicationInstaller = getApplicationInstaller()
    val mode = if (isDebug) AppComponent.Mode.DEBUG else AppComponent.Mode.RUN
    val processHandler = WatchFaceProcessHandler(console)
    devices.forEach { device ->
      processHandler.addDevice(device)
      val version = device.getWearDebugSurfaceVersion()
      if (version < WATCH_FACE_MIN_DEBUG_SURFACE_VERSION) {
        throw SurfaceVersionException(WATCH_FACE_MIN_DEBUG_SURFACE_VERSION, version)
      }
      val app = installWatchFace(device, applicationInstaller, console)
      setWatchFace(app, mode)
      showWatchFace(device, console)
    }
    ProgressManager.checkCanceled()
    return createRunContentDescriptor(devices, processHandler, console)
  }

  private fun installWatchFace(device: IDevice, applicationInstaller: ApplicationInstaller, console: ConsoleView): App {
    ProgressIndicatorProvider.getGlobalProgressIndicator()?.apply {
      checkCanceled()
      text = "Installing the watch face"
    }
    return applicationInstaller.installAppOnDevice(device, appId, getApkPaths(device), configuration.installFlags) {
      console.print(it, ConsoleViewContentType.NORMAL_OUTPUT)
    }
  }

  private fun setWatchFace(app: App, mode: AppComponent.Mode) {
    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()?.apply {
      checkCanceled()
      text = "Launching the watch face"
    }
    val outputReceiver = RecordOutputReceiver { indicator?.isCanceled == true }
    try {
      app.activateComponent(configuration.componentType, configuration.componentName!!, mode, outputReceiver)
    }
    catch (ex: DeployerException) {
      throw throw ExecutionException("Error while launching watch face, message: ${outputReceiver.getOutput()}", ex)
    }
  }
}

internal fun showWatchFace(device: IDevice, console: ConsoleView) {
  val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()?.apply {
    checkCanceled()
    text = "Jumping to the watch face"
  }
  val resultReceiver = CommandResultReceiver()
  val receiver = MultiReceiver(resultReceiver, ConsoleOutputReceiver({ indicator?.isCanceled == true }, console))
  console.printShellCommand(SHOW_WATCH_FACE)
  device.executeShellCommand(SHOW_WATCH_FACE, receiver, 5, TimeUnit.SECONDS)
  if (resultReceiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
    console.printError("Warning: Launch was successful, but you may need to bring up the watch face manually")
  }
}

class WatchFaceProcessHandler(private val console: ConsoleView) : AndroidProcessHandlerForDevices() {

  override fun destroyProcessOnDevice(device: IDevice) {
    val receiver = ConsoleOutputReceiver({ false }, console)

    console.printShellCommand(UNSET_WATCH_FACE)
    device.executeShellCommand(UNSET_WATCH_FACE, receiver, 5, TimeUnit.SECONDS)
  }
}