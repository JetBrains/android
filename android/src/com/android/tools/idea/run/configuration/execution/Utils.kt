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
@file:JvmName("ExecutionUtils")

package com.android.tools.idea.run.configuration.execution

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.MultiLineReceiver
import com.android.ddmlib.MultiReceiver
import com.android.ddmlib.NullOutputReceiver
import com.android.sdklib.AndroidVersion
import com.android.tools.deployer.model.component.WearComponent
import com.android.tools.deployer.model.component.WearComponent.CommandResultReceiver
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.execution.common.AndroidExecutionException
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.run.util.LaunchUtils
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ExecutionUiService
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import org.jetbrains.android.util.AndroidBundle
import java.util.concurrent.TimeUnit


fun ConsoleView.printShellCommand(command: String) = println("$ adb shell $command \n")

fun ConsoleView.println(text: String) {
  print(text + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
}

fun ConsoleView.printlnError(error: String) {
  print(error + "\n", ConsoleViewContentType.ERROR_OUTPUT)
}

const val TARGET_REGEX = "\\berror\\b"

val errorPattern = Regex(TARGET_REGEX, RegexOption.IGNORE_CASE)

@WorkerThread
@Throws(ExecutionException::class)
@JvmOverloads
fun IDevice.executeShellCommand(command: String, consoleView: ConsoleView, receiver: IShellOutputReceiver = NullOutputReceiver(),
                                timeOut: Long = 5, timeOutUnits: TimeUnit = TimeUnit.SECONDS, indicator: ProgressIndicator?) {
  ApplicationManager.getApplication().assertIsNonDispatchThread()
  consoleView.printShellCommand(command)
  val consoleReceiver = ConsoleOutputReceiver({ indicator?.isCanceled == true }, consoleView)
  val collectingOutputReceiver = CollectingOutputReceiver()
  try {
    executeShellCommand(command, MultiReceiver(receiver, consoleReceiver, collectingOutputReceiver), timeOut, timeOutUnits)
  }
  catch (e: Exception) {
    throw ExecutionException("Error while executing: '$command'")
  }
  if (collectingOutputReceiver.output.matches(errorPattern)) {
    throw ExecutionException("Error while executing: '$command'")
  }
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
    console.printlnError(AndroidBundle.message("android.run.configuration.wear.version.affects.debugging"))
  }
}

suspend fun createRunContentDescriptor(
  processHandler: ProcessHandler,
  console: ConsoleView,
  environment: ExecutionEnvironment
): RunContentDescriptor {
  console.attachToProcess(processHandler)
  return withContext(uiThread) {
    ExecutionUiService.getInstance().showRunContent(DefaultExecutionResult(console, processHandler), environment)
  }
}

@WorkerThread
fun prepareDevices(project: Project, indicator: ProgressIndicator, deployTarget: DeployTarget): DeviceFutures {
  indicator.text = "Launching devices"
  return deployTarget.getDevices(project)
}

private const val TARGET_DEVICE_NOT_FOUND = "TARGET_DEVICE_NOT_FOUND"

@Throws(ExecutionException::class)
@WorkerThread
suspend fun getDevices(deviceFutures: DeviceFutures, indicator: ProgressIndicator, stats: RunStats): List<IDevice> {
  indicator.text = "Waiting for all target devices to come online"

  val deviceFutureList = deviceFutures.get()

  if (deviceFutureList.isEmpty()) {
    throw AndroidExecutionException(TARGET_DEVICE_NOT_FOUND, AndroidBundle.message("deployment.target.not.found"))
  }

  return deviceFutureList.map {
    stats.beginWaitForDevice()
    val device = it.await()
    stats.endWaitForDevice(device)
    device
  }.onEach { LaunchUtils.initiateDismissKeyguard(it) }
}

private const val APPLICATION_ID_NOT_FOUND = "APPLICATION_ID_NOT_FOUND"

@Throws(ExecutionException::class)
@WorkerThread
suspend fun getDevices(project: Project, indicator: ProgressIndicator, deployTarget: DeployTarget, stats: RunStats): List<IDevice> {
  return getDevices(prepareDevices (project, indicator, deployTarget), indicator, stats)
}

@Throws(ExecutionException::class)
suspend fun getDevices(environment: ExecutionEnvironment, deviceFutures: DeviceFutures, indicator: ProgressIndicator): List<IDevice> {
  return getDevices(deviceFutures, indicator, RunStats.from(environment))
}

val ApplicationIdProvider.applicationIdOrAndroidExecutionException: String @Throws(ExecutionException::class) get() = try {
  packageName
} catch (e: ApkProvisionException) {
  throw AndroidExecutionException(APPLICATION_ID_NOT_FOUND, e.message)
}