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
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.deployer.model.component.WatchFace.ShellCommand.SHOW_WATCH_FACE
import com.android.tools.deployer.model.component.WatchFace.ShellCommand.UNSET_WATCH_FACE
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
import java.util.concurrent.TimeUnit


class AndroidWatchFaceConfigurationExecutor(environment: ExecutionEnvironment) : AndroidWearConfigurationExecutorBase(environment) {

  @WorkerThread
  override fun doOnDevices(devices: List<IDevice>): RunContentDescriptor? {
    val isDebug = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID
    if (isDebug && devices.size > 1) {
      throw ExecutionException("Debugging is allowed only for single device")
    }
    val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    Disposer.register(project, console)
    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()
    val applicationInstaller = getApplicationInstaller()
    val mode = if (isDebug) AppComponent.Mode.DEBUG else AppComponent.Mode.RUN
    val processHandler = WatchFaceProcessHandler(console)
    devices.forEach { device ->
      processHandler.addDevice(device)
      indicator?.checkCanceled()
      indicator?.text = "Installing app"
      val app = applicationInstaller.installAppOnDevice(device, appId, getApkPaths(device), configuration.installFlags) {
        console.print(it, ConsoleViewContentType.NORMAL_OUTPUT)
      }
      val receiver = AndroidLaunchReceiver({ indicator?.isCanceled == true }, console)
      app.activateComponent(configuration.componentType, configuration.componentName!!, mode, receiver)
      console.printShellCommand(SHOW_WATCH_FACE)
      device.executeShellCommand(SHOW_WATCH_FACE, receiver, 5, TimeUnit.SECONDS)
    }
    ProgressManager.checkCanceled()
    return createRunContentDescriptor(devices, processHandler, console)
  }
}


class WatchFaceProcessHandler(private val console: ConsoleView) : AndroidProcessHandlerForDevices() {

  override fun destroyProcessOnDevice(device: IDevice) {
    val receiver = AndroidWearConfigurationExecutorBase.AndroidLaunchReceiver({ false }, console)

    console.printShellCommand(UNSET_WATCH_FACE)
    device.executeShellCommand(UNSET_WATCH_FACE, receiver, 5, TimeUnit.SECONDS)
  }
}