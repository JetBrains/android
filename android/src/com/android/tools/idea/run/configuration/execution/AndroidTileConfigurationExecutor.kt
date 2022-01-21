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
import com.android.tools.idea.run.configuration.AndroidTileConfiguration
import com.intellij.execution.ExecutionException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import java.util.concurrent.TimeUnit


class AndroidTileConfigurationExecutor(environment: ExecutionEnvironment) : AndroidConfigurationExecutorBase(environment) {
  override val configuration = environment.runProfile as AndroidTileConfiguration

  @WorkerThread
  override fun doOnDevices(devices: List<IDevice>): RunContentDescriptor {
    val isDebug = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID
    if (isDebug && devices.size > 1) {
      throw ExecutionException("Debugging is allowed only for a single device")
    }
    val console = createConsole()
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
      val addTileReceiver = AddTileCommandResultReceiver({ indicator?.isCanceled == true }, console)
      app.activateComponent(configuration.componentType, configuration.componentName!!, mode, addTileReceiver)
      verifyResponse(addTileReceiver, console)
      val showTileCommand = SHOW_TILE_COMMAND + addTileReceiver.index!!
      console.printShellCommand(showTileCommand)
      val showTileReceiver = ShowTileCommandResultReceiver({ indicator?.isCanceled == true }, console)
      device.executeShellCommand(showTileCommand, showTileReceiver, 5, TimeUnit.SECONDS)
      verifyResponse(showTileReceiver, console)
    }
    ProgressManager.checkCanceled()
    return createRunContentDescriptor(devices, processHandler, console)
  }

  private fun verifyResponse(receiver: AddTileCommandResultReceiver, console: ConsoleView) {
    if (receiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
      throw ExecutionException("Error while setting the tile, message: ${receiver.getOutput()}")
    }
    if (receiver.index == null) {
      throw ExecutionException("Tile index was not found.")
    }
  }

  private fun verifyResponse(receiver: ShowTileCommandResultReceiver, console: ConsoleView) {
    if (receiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
      console.printError("Warning: Launch was successful, but you may need to bring up the tile manually.")
    }
  }

}


private class AddTileCommandResultReceiver(isCancelledCheck: () -> Boolean, consoleView: ConsoleView) : CommandResultReceiver(
  isCancelledCheck, consoleView) {
  private val indexPattern = "Index=\\[(\\d+)]".toRegex()
  var index: Int? = null

  override fun processNewLines(lines: Array<String>) {
    super.processNewLines(lines)
    lines.forEach { line -> extractPattern(line, indexPattern)?.let { index = it.toInt() } }
  }
}

private class ShowTileCommandResultReceiver(isCancelledCheck: () -> Boolean, consoleView: ConsoleView) : CommandResultReceiver(
  isCancelledCheck, consoleView)

class TileProcessHandler(private val tileName: String, private val console: ConsoleView) : AndroidProcessHandlerForDevices() {
  override fun destroyProcessOnDevice(device: IDevice) {
    val receiver = AndroidLaunchReceiver({ false }, console)

    val removeTileCommand = Tile.ShellCommand.UNSET_TILE + tileName
    console.printShellCommand(removeTileCommand)
    device.executeShellCommand(removeTileCommand, receiver, 5, TimeUnit.SECONDS)
  }
}
