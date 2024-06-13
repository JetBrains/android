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
import com.android.tools.idea.editors.liveedit.LiveEditService
import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.ApplicationTerminator
import com.android.tools.idea.execution.common.clearAppStorage
import com.android.tools.idea.execution.common.getProcessHandlersForDevices
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.ConsoleProvider
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.LiveEditHelper
import com.android.tools.idea.run.ShowLogcatListener
import com.android.tools.idea.run.ShowLogcatListener.Companion.getShowLogcatLinkText
import com.android.tools.idea.run.configuration.execution.createRunContentDescriptor
import com.android.tools.idea.run.configuration.execution.getApplicationIdAndDevices
import com.android.tools.idea.run.configuration.execution.println
import com.android.tools.idea.run.util.LaunchUtils
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
import com.intellij.xdebugger.XDebugSession
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
  private val deviceFutures: DeviceFutures,
  private val myLaunchTasksProvider: BlazeLaunchTasksProvider,
  private val launchOptions: LaunchOptions,
  private val apkProvider: ApkProvider,
  private val liveEditService: LiveEditService
) : AndroidConfigurationExecutor {

  val project = env.project
  override val configuration = env.runProfile as RunConfiguration
  private val LOG = Logger.getInstance(this::class.java)

  override fun run(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable {
    val (applicationId, devices) = getApplicationIdAndDevices(env, deviceFutures, applicationIdProvider, indicator)

    env.runnerAndConfigurationSettings?.getProcessHandlersForDevices(project, devices)?.forEach { it.destroyProcess() }

    waitPreviousProcessTermination(devices, applicationId, indicator)

    val processHandler = AndroidProcessHandler(applicationId, { it.forceStop(applicationId) })

    val console = createConsole(processHandler)
    doRun(devices, processHandler, false, indicator, console, applicationId)

    devices.forEach { device ->
      processHandler.addTargetDevice(device)
      if (launchOptions.isOpenLogcatAutomatically) {
        project.messageBus.syncPublisher(ShowLogcatListener.TOPIC).showLogcat(device, applicationId)
      } else {
        console.printHyperlink(getShowLogcatLinkText(device)) { project ->
          project.messageBus.syncPublisher(ShowLogcatListener.TOPIC).showLogcat(device, applicationId)
        }
      }
    }

    AndroidSessionInfo.create(processHandler, devices, applicationId)
    createRunContentDescriptor(processHandler, console, env)
  }

  private suspend fun doRun(
    devices: List<IDevice>,
    processHandler: ProcessHandler,
    isDebug: Boolean,
    indicator: ProgressIndicator,
    console: ConsoleView,
    applicationId: String
  ) = coroutineScope {
    val stat = RunStats.from(env).apply { setPackage(applicationId) }
    stat.beginLaunchTasks()
    indicator.text = "Launching on devices"
    try {
      printLaunchTaskStartedMessage(console)

      devices.map { device ->
        async {
          if (launchOptions.isClearAppStorage) {
            clearAppStorage(project, device, applicationId, RunStats.from(env))
          }

          LaunchUtils.initiateDismissKeyguard(device)
          LOG.info("Launching on device ${device.name}")
          val launchContext = BlazeLaunchContext(env, device, console, processHandler, indicator)
          myLaunchTasksProvider.getTasks(device, isDebug).forEach {
            it.run(launchContext)
          }
          LiveEditHelper().invokeLiveEdit(
            liveEditService,
            env,
            applicationId,
            apkProvider.getApks(device),
            device
          ) // Notify listeners of the deployment.
          project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingApp(device.serialNumber, project)
        }
      }.awaitAll()
    } finally {
      stat.endLaunchTasks()
    }
  }

  private suspend fun waitPreviousProcessTermination(
    devices: List<IDevice>, applicationId: String, indicator: ProgressIndicator
  ) = coroutineScope {
    indicator.text = "Terminating the app"
    val results = devices.map { async { ApplicationTerminator(it, applicationId).killApp() } }.awaitAll()
    if (results.any { !it }) {
      throw ExecutionException("Couldn't terminate previous instance of app")
    }
  }

  override fun debug(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable {
    val (applicationId, devices) = getApplicationIdAndDevices(env, deviceFutures, applicationIdProvider, indicator)

    if (devices.size != 1) {
      throw ExecutionException("Cannot launch a debug session on more than 1 device.")
    }

    env.runnerAndConfigurationSettings?.getProcessHandlersForDevices(project, devices)?.forEach { it.destroyProcess() }

    waitPreviousProcessTermination(devices, applicationId, indicator)

    val processHandler = NopProcessHandler()
    val console = createConsole(processHandler)
    doRun(devices, processHandler, true, indicator, console, applicationId)

    val device = devices.single()
    indicator.text = "Connecting debugger"
    myLaunchTasksProvider.startDebugSession(env, device, console, indicator, applicationId).runContentDescriptor
  }

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
  fun getTasks(device: IDevice, isDebug: Boolean): List<BlazeLaunchTask>

  @Throws(ExecutionException::class)
  fun startDebugSession(
    environment: ExecutionEnvironment, device: IDevice, console: ConsoleView, indicator: ProgressIndicator, applicationId: String
  ): XDebugSession
}


interface BlazeLaunchTask {
  @Throws(ExecutionException::class)
  fun run(launchContext: BlazeLaunchContext)
}

class BlazeLaunchContext(
  val env: ExecutionEnvironment,
  val device: IDevice,
  val consoleView: ConsoleView,
  val processHandler: ProcessHandler,
  val progressIndicator: ProgressIndicator
)