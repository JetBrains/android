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
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.deployer.model.component.Complication
import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.deployer.model.component.WatchFace.ShellCommand.SHOW_WATCH_FACE
import com.android.tools.deployer.model.component.WatchFace.ShellCommand.UNSET_WATCH_FACE
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration
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

class AndroidComplicationConfigurationExecutor(environment: ExecutionEnvironment) : AndroidConfigurationExecutorBase(environment) {
  override val configuration = environment.runProfile as AndroidComplicationConfiguration

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
    val watchFaceInfo = "${(configuration as AndroidComplicationConfiguration).watchFaceInfo.appId} ${configuration.watchFaceInfo.watchFaceFQName}"
    val processHandler = ComplicationProcessHandler(AppComponent.getFQEscapedName(appId, configuration.componentName!!), console)
    devices.forEach { device ->
      processHandler.addDevice(device)
      indicator?.checkCanceled()
      val app = applicationInstaller.installAppOnDevice(device, appId, getApkPaths(device), configuration.installFlags) { info ->
        console.print(info, ConsoleViewContentType.NORMAL_OUTPUT)
      }
      indicator?.checkCanceled()
      val appWatchFace = installWatchApp(device, console)

      val receiver = AndroidLaunchReceiver({ indicator?.isCanceled == true }, console)
      configuration.chosenSlots.forEach { slot ->
        app.activateComponent(configuration.componentType, configuration.componentName!!, "$watchFaceInfo ${slot.id} ${slot.type}", mode,
                              receiver)
      }
      appWatchFace.activateComponent(ComponentType.WATCH_FACE, configuration.watchFaceInfo.watchFaceFQName, receiver)
      console.printShellCommand(SHOW_WATCH_FACE)
      device.executeShellCommand(SHOW_WATCH_FACE, receiver, 5, TimeUnit.SECONDS)
    }
    ProgressManager.checkCanceled()
    return createRunContentDescriptor(devices, processHandler, console)
  }

  private fun installWatchApp(device: IDevice, console: ConsoleView): App {
    val watchFaceInfo = (configuration as AndroidComplicationConfiguration).watchFaceInfo
    return getApplicationInstaller().installAppOnDevice(device, watchFaceInfo.appId, listOf(watchFaceInfo.apk), "") { info ->
      console.print(info, ConsoleViewContentType.NORMAL_OUTPUT)
    }
  }
}

/**
 * [complicationComponentName] format: appId/complicationFQName. e.g androidx.wear.samples.app/androidx.wear.samples.MyComplication
 */
class ComplicationProcessHandler(private val complicationComponentName: String,
                                 private val console: ConsoleView) : AndroidProcessHandlerForDevices() {
  override fun destroyProcessOnDevice(device: IDevice) {
    val receiver = AndroidLaunchReceiver({ false }, console)

    val removeComplicationCommand = Complication.ShellCommand.REMOVE_ALL_INSTANCES_FROM_CURRENT_WF + complicationComponentName
    console.printShellCommand(removeComplicationCommand)
    device.executeShellCommand(removeComplicationCommand, receiver, 5, TimeUnit.SECONDS)

    console.printShellCommand(UNSET_WATCH_FACE)
    device.executeShellCommand(UNSET_WATCH_FACE, receiver, 5, TimeUnit.SECONDS)
  }
}