/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run

import com.android.ddmlib.IDevice
import com.android.tools.deployer.DeployerException
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.execution.common.AndroidExecutionException
import com.android.tools.idea.execution.common.ApplicationTerminator
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.ShowLogcatListener.Companion.getShowLogcatLinkText
import com.android.tools.idea.run.applychanges.findExistingSessionAndMaybeDetachForColdSwap
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.createRunContentDescriptor
import com.android.tools.idea.run.configuration.execution.getDevices
import com.android.tools.idea.run.configuration.execution.println
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.getBaseDebuggerTask
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.stats.RunStats
import com.android.tools.idea.util.androidFacet
import com.intellij.execution.ExecutionException
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.utils.keysToMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LaunchTaskRunner(
  private val applicationIdProvider: ApplicationIdProvider,
  private val env: ExecutionEnvironment,
  override val deviceFutures: DeviceFutures,
  private val apkProvider: ApkProvider
) : AndroidConfigurationExecutor {
  val project = env.project
  override val configuration = env.runProfile as AndroidRunConfiguration

  val facet = configuration.configurationModule.module?.androidFacet ?: throw RuntimeException("Cannot get AndroidFacet")

  private val LOG = Logger.getInstance(this::class.java)

  override fun run(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable(indicator) {
    findExistingSessionAndMaybeDetachForColdSwap(env)
    val devices = getDevices(deviceFutures, indicator, RunStats.from(env))

    val packageName = applicationIdProvider.packageName
    waitPreviousProcessTermination(devices, packageName, indicator)

    val processHandler = AndroidProcessHandler(project, packageName, { it.forceStop(packageName) })

    val console = createConsole()
    doRun(devices, processHandler, indicator, console, false)

    devices.forEach { device ->
      processHandler.addTargetDevice(device)
      if (!StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.get()) {
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
                            console: ConsoleView,
                            isDebug: Boolean) = coroutineScope {
    val launchTasksProvider = AndroidLaunchTasksProvider(configuration, env, facet, applicationIdProvider, apkProvider,
                                                         configuration.launchOptions.build(), isDebug)

    val applicationId = applicationIdProvider.packageName
    val stat = RunStats.from(env).apply { setPackage(applicationId) }
    stat.beginLaunchTasks()
    try {

      printLaunchTaskStartedMessage(console)
      launchTasksProvider.fillStats(stat)

      // Create launch tasks for each device.
      indicator.text = "Getting task for devices"
      val launchTaskMap = devices.keysToMap { launchTasksProvider.getTasks(it) }

      // A list of devices that we have launched application successfully.
      indicator.text = "Launching on devices"
      launchTaskMap.entries.map { (device, tasks) ->
        async {
          LOG.info("Launching on device ${device.name}")
          val launchContext = LaunchContext(env, device, console, processHandler, indicator)
          runLaunchTasks(tasks, launchContext)
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
    val console = createConsole()
    doRun(devices, processHandler, indicator, console, true)

    val device = devices.single()
    indicator.text = "Connecting debugger"
    val debuggerTask = getBaseDebuggerTask(configuration.androidDebuggerContext, facet, env)
    val session = debuggerTask.perform(device, applicationId, env, indicator, console)
    session.runContentDescriptor
  }

  override fun applyChanges(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable(indicator) {
    val devices = getDevices(deviceFutures, indicator, RunStats.from(env))

    val oldSession = findExistingSessionAndMaybeDetachForColdSwap(env)
    val packageName = applicationIdProvider.packageName
    val processHandler = oldSession.processHandler ?: AndroidProcessHandler(project, packageName)

    val console = oldSession.executionConsole as? ConsoleView ?: createConsole()

    doRun(devices, processHandler, indicator, console, false)

    if (oldSession.processHandler == null && processHandler is AndroidProcessHandler) {
      devices.forEach { device ->
        processHandler.addTargetDevice(device)
        if (!StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.get()) {
          console.printHyperlink(getShowLogcatLinkText(device)) { project ->
            project.messageBus.syncPublisher(ShowLogcatListener.TOPIC).showLogcat(device, applicationIdProvider.packageName)
          }
        }
      }
    }

    withContext(uiThread) {
      val descriptor = RunContentManager.getInstance(project).findContentDescriptor(env.executor, processHandler)

      if (descriptor?.attachedContent == null) {
        createRunContentDescriptor(processHandler, console, env)
      }
      else {
        descriptor.takeIf { it is HiddenRunContentDescriptor } ?: HiddenRunContentDescriptor(descriptor)
      }
    }
  }

  private fun createConsole(): ConsoleView {
    val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    Disposer.register(project, console)
    return console
  }

  override fun applyCodeChanges(indicator: ProgressIndicator): RunContentDescriptor {
    return applyChanges(indicator)
  }

  private fun runLaunchTasks(launchTasks: List<LaunchTask>, launchContext: LaunchContext) {
    val stat = RunStats.from(env)
    for (task in launchTasks) {
      if (task.shouldRun(launchContext)) {
        val details = stat.beginLaunchTask(task)
        try {
          task.run(launchContext)
          stat.endLaunchTask(task, details, true)
        }
        catch (e: Exception) {
          stat.endLaunchTask(task, details, false)
          if (e is DeployerException) {
            throw AndroidExecutionException(e.id, e.message)
          }
          throw e
        }
      }
    }
  }

  private fun printLaunchTaskStartedMessage(consoleView: ConsoleView) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
    val launchVerb = when (env.getUserData(SwapInfo.SWAP_INFO_KEY)?.type) {
      SwapInfo.SwapType.APPLY_CHANGES -> "Applying changes to"
      SwapInfo.SwapType.APPLY_CODE_CHANGES -> "Applying code changes to"
      else -> "Launching"
    }
    consoleView.println("$dateFormat: $launchVerb ${configuration.name} on '${env.executionTarget.displayName}.")
  }
}