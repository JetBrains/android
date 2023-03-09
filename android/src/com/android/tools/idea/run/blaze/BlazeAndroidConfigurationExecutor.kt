/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.blaze

import com.android.ddmlib.IDevice
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.execution.common.ApplicationTerminator
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.ClearLogcatListener
import com.android.tools.idea.run.ConsoleProvider
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.ShowLogcatListener
import com.android.tools.idea.run.ShowLogcatListener.Companion.getShowLogcatLinkText
import com.android.tools.idea.run.applychanges.findExistingSessionAndMaybeDetachForColdSwap
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.createRunContentDescriptor
import com.android.tools.idea.run.configuration.execution.getDevices
import com.android.tools.idea.run.configuration.execution.println
import com.android.tools.idea.run.tasks.ConnectDebuggerTask
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.util.LaunchUtils
import com.android.tools.idea.stats.RunStats
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * [AndroidConfigurationExecutor] for [BlazeRunConfiguration].
 * TODO: Move to blaze module as we support Kotlin in it.
 */
class BlazeAndroidConfigurationExecutor(
  private val consoleProvider: ConsoleProvider,
  private val applicationIdProvider: ApplicationIdProvider,
  private val env: ExecutionEnvironment,
  override val deviceFutures: DeviceFutures,
  private val myLaunchTasksProvider: BlazeLaunchTasksProvider,
  private val launchOptions: LaunchOptions
) : AndroidConfigurationExecutor {

  val project = env.project
  override val configuration = env.runProfile as RunConfiguration
  private val LOG = Logger.getInstance(this::class.java)

  override fun run(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable(indicator) {
    findExistingSessionAndMaybeDetachForColdSwap(env)
    val devices = getDevices(deviceFutures, indicator, RunStats.from(env))

    val packageName = applicationIdProvider.packageName
    waitPreviousProcessTermination(devices, packageName, indicator)

    val processHandler = AndroidProcessHandler(project, packageName, { it.forceStop(packageName) })

    val console = createConsole(processHandler)
    doRun(devices, processHandler, indicator, console)

    devices.forEach { device ->
      processHandler.addTargetDevice(device)
      if (launchOptions.isOpenLogcatAutomatically) {
        project.messageBus.syncPublisher(ShowLogcatListener.TOPIC).showLogcat(device, packageName)
      }
      else {
        console.printHyperlink(getShowLogcatLinkText(device)) { project ->
          project.messageBus.syncPublisher(ShowLogcatListener.TOPIC).showLogcat(device, packageName)
        }
      }
    }

    createRunContentDescriptor(processHandler, console, env)
  }

  private suspend fun doRun(devices: List<IDevice>,
                            processHandler: ProcessHandler,
                            indicator: ProgressIndicator,
                            console: ConsoleView) = coroutineScope {
    val applicationId = applicationIdProvider.packageName
    val stat = RunStats.from(env).apply { setPackage(applicationId) }
    stat.beginLaunchTasks()
    try {
      printLaunchTaskStartedMessage(console)

      indicator.text = "Getting task for devices"

      // A list of devices that we have launched application successfully.
      indicator.text = "Launching on devices"
      devices.map {device ->
        async {
          if (launchOptions.isClearAppStorage) {
            project.messageBus.syncPublisher(ClearLogcatListener.TOPIC).clearLogcat(device.serialNumber)
          }

          LaunchUtils.initiateDismissKeyguard(device)
          LOG.info("Launching on device ${device.name}")
          val launchContext = BlazeLaunchContext(env, device, console, processHandler, indicator)
          myLaunchTasksProvider.getTasks(device).forEach {
            it.run(launchContext)
          }
          // Notify listeners of the deployment.
          project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingApp(device.serialNumber, project)
        }
      }.awaitAll()
    }
    finally {
      stat.endLaunchTasks()
    }
  }

  private suspend fun waitPreviousProcessTermination(devices: List<IDevice>,
                                                     applicationId: String,
                                                     indicator: ProgressIndicator) = coroutineScope {
    indicator.text = "Terminating the app"
    val results = devices.map { async { ApplicationTerminator(it, applicationId).killApp() } }.awaitAll()
    if (results.any { !it }) {
      throw ExecutionException("Couldn't terminate previous instance of app")
    }
  }

  override fun debug(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable(indicator) {
    val applicationId = applicationIdProvider.packageName

    val devices = getDevices(deviceFutures, indicator, RunStats.from(env))

    if (devices.size != 1) {
      throw ExecutionException("Cannot launch a debug session on more than 1 device.")
    }
    waitPreviousProcessTermination(devices, applicationId, indicator)

    findExistingSessionAndMaybeDetachForColdSwap(env)

    val processHandler = NopProcessHandler()
    val console = createConsole(processHandler)
    doRun(devices, processHandler, indicator, console)

    val device = devices.single()
    val debuggerTask = myLaunchTasksProvider.connectDebuggerTask
                       ?: throw RuntimeException(
                         "ConnectDebuggerTask is null for task provider " + myLaunchTasksProvider.javaClass.name)
    indicator.text = "Connecting debugger"
    val session = debuggerTask.perform(device, applicationId, env, indicator, console)
    session.runContentDescriptor
  }

  override fun applyChanges(indicator: ProgressIndicator): RunContentDescriptor = throw UnsupportedOperationException("Apply Changes are not supported for Blaze")

  override fun applyCodeChanges(indicator: ProgressIndicator): RunContentDescriptor = throw UnsupportedOperationException("Apply Code Changes are not supported for Blaze")

  private suspend fun createConsole(processHandler: ProcessHandler): ConsoleView = withContext(uiThread) {
    consoleProvider.createAndAttach(project, processHandler, env.executor)
  }

  private fun printLaunchTaskStartedMessage(consoleView: ConsoleView) {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
    consoleView.println("$date: Launching ${configuration.name} on '${env.executionTarget.displayName}.")
  }
}


interface BlazeLaunchTasksProvider {
  @Throws(ExecutionException::class)
  fun getTasks(device: IDevice): List<BlazeLaunchTask>

  @get:Throws(ExecutionException::class)
  val connectDebuggerTask: ConnectDebuggerTask?
}


interface BlazeLaunchTask {
  @Throws(ExecutionException::class)
  fun run(launchContext: BlazeLaunchContext)
}

class BlazeLaunchTaskWrapper(private val launchTask: LaunchTask):BlazeLaunchTask {
  override fun run(launchContext: BlazeLaunchContext) {
    launchTask.run(LaunchContext(launchContext.env, launchContext.device, launchContext.consoleView, launchContext.processHandler, launchContext.progressIndicator))
  }
}

class BlazeLaunchContext(val env: ExecutionEnvironment,
    val device: IDevice,
    val consoleView: ConsoleView,
    val processHandler: ProcessHandler,
    val progressIndicator: ProgressIndicator)