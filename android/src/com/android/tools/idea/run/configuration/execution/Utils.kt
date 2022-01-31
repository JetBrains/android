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

import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.android.ddmlib.MultiReceiver
import com.android.tools.deployer.model.component.WearComponent
import com.android.tools.deployer.model.component.WearComponent.CommandResultReceiver
import com.intellij.execution.ExecutionException
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.progress.ProgressIndicatorProvider
import java.util.concurrent.TimeUnit

internal fun ConsoleView.printShellCommand(command: String) {
  print("$ adb shell $command \n", ConsoleViewContentType.NORMAL_OUTPUT)
}

internal fun ConsoleView.print(text: String) {
  print(text + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
}

internal fun ConsoleView.printError(error: String) {
  print(error + "\n", ConsoleViewContentType.ERROR_OUTPUT)
}

internal fun IDevice.getWearDebugSurfaceVersion(): Int {
  class VersionReceiver(private val isCancelledCheck: () -> Boolean) : MultiLineReceiver() {
    // Example of output: Broadcast completed: result=1, data="3"
    private val versionPattern = "data=\"(\\d+)\"".toRegex()
    var version = -1

    override fun isCancelled() = isCancelledCheck()

    override fun processNewLines(lines: Array<String>) {
      lines.forEach { line ->
        extractPattern(line, versionPattern)?.let { version = it.toInt() }
      }
    }
  }

  val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()?.apply {
    checkCanceled()
    text = "Checking Wear OS Surface API version"
  }

  val outputReceiver = RecordOutputReceiver { indicator?.isCanceled == true }
  val resultReceiver = CommandResultReceiver()
  val versionReceiver = VersionReceiver { indicator?.isCanceled == true }
  val receiver = MultiReceiver(outputReceiver, resultReceiver, versionReceiver)
  executeShellCommand(WearComponent.ShellCommand.GET_WEAR_DEBUG_SURFACE_VERSION, receiver, 5, TimeUnit.SECONDS)

  if (resultReceiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
    throw ExecutionException("Error while checking version, message: ${outputReceiver.getOutput()}")
  }
  // 2 is the minimum for all surfaces. 2 means the watch supports both start and stop commands
  if (versionReceiver.version < 2) {
    throw ExecutionException("Device software is out of date, message: ${outputReceiver.getOutput()}")
  }

  return versionReceiver.version
}