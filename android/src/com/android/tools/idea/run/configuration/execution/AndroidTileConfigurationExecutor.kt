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
import com.android.tools.deployer.model.component.Tile
import com.android.tools.deployer.model.component.Tile.ShellCommand.SHOW_TILE_COMMAND
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


class AndroidTileConfigurationExecutor(environment: ExecutionEnvironment) : AndroidWearConfigurationExecutorBase(environment) {

  @WorkerThread
  override fun doOnDevices(devices: List<IDevice>): RunContentDescriptor? {
    val isDebug = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID
    if (isDebug && devices.size > 1) {
      throw ExecutionException("Debugging is allowed only for a single device")
    }
    val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    Disposer.register(project, console)
    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()
    val applicationInstaller = getApplicationInstaller()
    val mode = if (isDebug) AppComponent.Mode.DEBUG else AppComponent.Mode.RUN
    val processHandler = TileProcessHandler(AppComponent.getFQEscapedName(appId, configuration.componentName!!), console)
    devices.forEach { device ->
      processHandler.addDevice(device)
      indicator?.checkCanceled()
      indicator?.text = "Installing app"
      val app = applicationInstaller.installAppOnDevice(device, appId, getApkPaths(device), configuration.installFlags) {
        console.print(it, ConsoleViewContentType.NORMAL_OUTPUT)
      }
      val receiver = TileIndexReceiver({ indicator?.isCanceled == true }, console)
      app.activateComponent(configuration.componentType, configuration.componentName!!, mode, receiver)
      val tileIndex = receiver.tileIndex ?: throw ExecutionException("Tile index is not found")
      val command = SHOW_TILE_COMMAND + tileIndex
      console.printShellCommand(command)
      device.executeShellCommand(command, AndroidLaunchReceiver({ indicator?.isCanceled == true }, console), 5, TimeUnit.SECONDS)
    }
    ProgressManager.checkCanceled()
    return createRunContentDescriptor(devices, processHandler, console)
  }

}

private class TileIndexReceiver(val isCancelledCheck: () -> Boolean,
                                consoleView: ConsoleView) : AndroidWearConfigurationExecutorBase.AndroidLaunchReceiver(isCancelledCheck,
                                                                                                                       consoleView) {
  var tileIndex: Int? = null
  val indexPattern = "Index=\\[(\\d+)]".toRegex()
  override fun processNewLines(lines: Array<String>) {
    super.processNewLines(lines)
    lines.forEach { line -> indexPattern.find(line)?.groupValues?.getOrNull(1)?.let { tileIndex = it.toInt() } }
  }
}

class TileProcessHandler(private val tileName:String, private val console: ConsoleView) : AndroidProcessHandlerForDevices() {
  override fun destroyProcessOnDevice(device: IDevice) {
    val receiver = AndroidWearConfigurationExecutorBase.AndroidLaunchReceiver({ false }, console)

    val removeTileCommand = Tile.ShellCommand.UNSET_TILE + tileName
    console.printShellCommand(removeTileCommand)
    device.executeShellCommand(removeTileCommand, receiver, 5, TimeUnit.SECONDS)
  }
}
