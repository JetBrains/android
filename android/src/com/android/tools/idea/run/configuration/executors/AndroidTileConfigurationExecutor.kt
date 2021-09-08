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
package com.android.tools.idea.run.configuration.executors

import com.android.ddmlib.MultiLineReceiver
import com.android.ddmlib.NullOutputReceiver
import com.android.tools.deployer.model.component.AppComponent.Mode
import com.android.tools.idea.run.ConsolePrinter
import com.intellij.execution.ExecutionException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import java.util.concurrent.TimeUnit


class AndroidTileConfigurationExecutor(private val environment: ExecutionEnvironment) : AndroidWearConfigurationExecutorBase(environment) {

  companion object {
    private val SHOW_TILE_COMMAND = "am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-tile --ei index"
  }

  override fun doOnDevice(deviceWearConfigurationExecutionSession: DeviceWearConfigurationExecutionSession) {
    val isDebug = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID

    val tileIndexReceiver = TileIndexReceiver(deviceWearConfigurationExecutionSession.processHandler,
                                              deviceWearConfigurationExecutionSession.consolePrinter)
    val app = deviceWearConfigurationExecutionSession.installAppOnDevice()
    deviceWearConfigurationExecutionSession.activateComponent(app, if (isDebug) Mode.DEBUG else Mode.RUN, tileIndexReceiver)
    if (tileIndexReceiver.tileIndex == null) {
      throw ExecutionException("Can't find Tile index")
    }
    deviceWearConfigurationExecutionSession.consolePrinter.stdout("Tile index ${tileIndexReceiver.tileIndex}")

    if (isDebug) {
      deviceWearConfigurationExecutionSession.attachDebuggerToClient()
    }

    // Brining the tile on the screen
    val command = "$SHOW_TILE_COMMAND ${tileIndexReceiver.tileIndex!! + 1}"
    deviceWearConfigurationExecutionSession.consolePrinter.stdout(command)
    deviceWearConfigurationExecutionSession.executeShellCommand(command, NullOutputReceiver(), 5, TimeUnit.SECONDS)
  }
}

private class TileIndexReceiver(private val processHandler: ProcessHandler, val consolePrinter: ConsolePrinter) : MultiLineReceiver() {
  var tileIndex: Int? = null
  val indexPattern = "Index=\\[(\\d+)]".toRegex()
  override fun isCancelled() = processHandler.isProcessTerminated
  override fun processNewLines(lines: Array<out String>) {
    lines.forEach { line -> indexPattern.find(line)?.groupValues?.getOrNull(1)?.let { tileIndex = it.toInt() } }
    lines.forEach { consolePrinter.stdout(it) }
  }
}
