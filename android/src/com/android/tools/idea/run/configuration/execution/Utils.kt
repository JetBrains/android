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
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.MultiLineReceiver
import com.android.ddmlib.MultiReceiver
import com.android.ddmlib.NullOutputReceiver
import com.android.sdklib.AndroidVersion
import com.android.tools.deployer.model.component.WearComponent
import com.android.tools.deployer.model.component.WearComponent.CommandResultReceiver
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ExecutionUiService
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
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

@WorkerThread
internal fun IDevice.executeShellCommand(command: String, console: ConsoleView, receiver: IShellOutputReceiver = NullOutputReceiver(),
                                         timeOut: Long = 5, timeOutUnits: TimeUnit = TimeUnit.SECONDS, indicator: ProgressIndicator?) {
  ApplicationManager.getApplication().assertIsNonDispatchThread()
  console.printShellCommand(command)
  val consoleReceiver = ConsoleOutputReceiver({ indicator?.isCanceled == true }, console)
  executeShellCommand(command, MultiReceiver(receiver, consoleReceiver), timeOut, timeOutUnits)
}

internal fun IDevice.getWearDebugSurfaceVersion(indicator: ProgressIndicator): Int {
  class VersionReceiver(private val isCancelledCheck: () -> Boolean) : MultiLineReceiver() {
    // Example of output: Broadcast completed: result=1, data="3"
    private val versionPattern = "data=\"(\\d+)\"".toRegex()
    var version = -1
      private set

    override fun isCancelled() = isCancelledCheck()

    override fun processNewLines(lines: Array<String>) {
      lines.forEach { line ->
        extractPattern(line, versionPattern)?.let { version = it.toInt() }
      }
    }
  }

  indicator.checkCanceled()
  indicator.text = "Checking Wear OS Surface API version"

  val startTime = System.currentTimeMillis()
  do {
    val outputReceiver = RecordOutputReceiver { indicator.isCanceled }
    val resultReceiver = CommandResultReceiver()
    val versionReceiver = VersionReceiver { indicator.isCanceled }
    val receiver = MultiReceiver(outputReceiver, resultReceiver, versionReceiver)
    executeShellCommand(WearComponent.ShellCommand.GET_WEAR_DEBUG_SURFACE_VERSION, receiver, 5, TimeUnit.SECONDS)

    val timeElapsed = System.currentTimeMillis() - startTime
    if (resultReceiver.resultCode <= 0 && timeElapsed < 5_000) {
      Thread.sleep(1_000)
      continue // This can happen when checking the version after cold boot. Try again.
    }

    var inferredVersion = versionReceiver.version
    if (resultReceiver.resultCode == CommandResultReceiver.INVALID_ARGUMENT_CODE) {
      // The version operation was not available initially.
      inferredVersion = 0
    } else if (resultReceiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
      Logger.getInstance("WearUtils").warn("Error while checking version, message: ${outputReceiver.getOutput()}")
        throw ExecutionException("Error while checking version")
    }

    // 2 is the minimum for all surfaces. 2 means the watch supports both start and stop commands
    if (inferredVersion < 2) {
      throw SurfaceVersionException(2, inferredVersion, isEmulator)
    }

    return inferredVersion
  } while (true) // Should not reach this point, as it will have return/throw above
}

internal fun checkAndroidVersionForWearDebugging(version: AndroidVersion, console: ConsoleView) {
  if (version < AndroidVersion(28)) {
    console.printError(AndroidBundle.message("android.run.configuration.wear.version.affects.debugging"))
  }
}

internal fun createRunContentDescriptor(
  processHandler: ProcessHandler,
  console: ConsoleView,
  environment: ExecutionEnvironment
): Promise<RunContentDescriptor> {
  val promise = AsyncPromise<RunContentDescriptor>()
  console.attachToProcess(processHandler)
  runInEdt {
    promise.setResult(ExecutionUiService.getInstance().showRunContent(DefaultExecutionResult(console, processHandler), environment))
  }
  return promise
}