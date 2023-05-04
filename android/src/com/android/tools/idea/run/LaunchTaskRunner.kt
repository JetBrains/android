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
import com.android.tools.idea.deploy.DeploymentConfiguration
import com.android.tools.idea.editors.literals.LiveEditService.Companion.usesCompose
import com.android.tools.idea.execution.common.AndroidExecutionException
import com.android.tools.idea.execution.common.ApplicationTerminator
import com.android.tools.idea.execution.common.clearAppStorage
import com.android.tools.idea.execution.common.getProcessHandlersForDevices
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.ShowLogcatListener.Companion.getShowLogcatLinkText
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.createRunContentDescriptor
import com.android.tools.idea.run.configuration.execution.getDevices
import com.android.tools.idea.run.configuration.execution.println
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.getBaseDebuggerTask
import com.android.tools.idea.run.util.LaunchUtils
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.stats.RunStats
import com.android.tools.idea.util.androidFacet
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.indicatorRunBlockingCancellable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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
  private val settings = env.runnerAndConfigurationSettings as RunnerAndConfigurationSettings

  val facet = configuration.configurationModule.module?.androidFacet ?: throw RuntimeException("Cannot get AndroidFacet")

  private val LOG = Logger.getInstance(this::class.java)

  override fun run(indicator: ProgressIndicator): RunContentDescriptor = indicatorRunBlockingCancellable(indicator) {
    val devices = getDevices(deviceFutures, indicator, RunStats.from(env))

    settings.getProcessHandlersForDevices(project, devices).forEach { it.destroyProcess() }

    val packageName = applicationIdProvider.packageName
    waitPreviousProcessTermination(devices, packageName, indicator)

    val processHandler = AndroidProcessHandler(project, packageName, { it.forceStop(packageName) })
    val console = createConsole()

    fillStats(RunStats.from(env), packageName)

    devices.forEach {
      if (configuration.CLEAR_LOGCAT) {
        project.messageBus.syncPublisher(ClearLogcatListener.TOPIC).clearLogcat(it.serialNumber)
      }
      if (configuration.CLEAR_APP_STORAGE) {
        clearAppStorage(project, it, packageName)
      }
      LaunchUtils.initiateDismissKeyguard(it)
    }

    doRun(devices, processHandler, indicator, console, false)

    devices.forEach { device ->
      processHandler.addTargetDevice(device)
      if (!StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.get()) {
        console.printHyperlink(getShowLogcatLinkText(device)) { project ->
          project.messageBus.syncPublisher(ShowLogcatListener.TOPIC).showLogcat(device, packageName)
        }
      }
      if (configuration.SHOW_LOGCAT_AUTOMATICALLY) {
        project.messageBus.syncPublisher(ShowLogcatListener.TOPIC).showLogcat(device, packageName)
      }
    }

    createRunContentDescriptor(processHandler, console, env)
  }

  private suspend fun doRun(devices: List<IDevice>,
                            processHandler: ProcessHandler,
                            indicator: ProgressIndicator,
                            console: ConsoleView,
                            isDebug: Boolean) = coroutineScope {
    val packageName = applicationIdProvider.packageName
    val launchTasksProvider = AndroidLaunchTasksProvider(configuration, env, facet, packageName, apkProvider,
                                                         configuration.getLaunchOptions(), isDebug)

    printLaunchTaskStartedMessage(console)


    // A list of devices that we have launched application successfully.
    indicator.text = "Launching on devices"
    devices.map { device ->
      async {
        LOG.info("Launching on device ${device.name}")
        val launchContext = LaunchContext(env, device, console, processHandler, indicator)

        val deployTasks = launchTasksProvider.getDeployTasks(device, packageName)
        runLaunchTasks(deployTasks, launchContext)

        val launchTasks = launchTasksProvider.getLaunchTasks(device)
        runLaunchTasks(launchTasks, launchContext)
        // Notify listeners of the deployment.
        project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingApp(device.serialNumber, project)
      }
    }.awaitAll()
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

  override fun debug(indicator: ProgressIndicator): RunContentDescriptor = indicatorRunBlockingCancellable(indicator) {
    val packageName = applicationIdProvider.packageName

    val devices = getDevices(deviceFutures, indicator, RunStats.from(env))

    if (devices.size != 1) {
      throw ExecutionException("Cannot launch a debug session on more than 1 device.")
    }
    fillStats(RunStats.from(env), packageName)

    settings.getProcessHandlersForDevices(project, devices).forEach { it.destroyProcess() }

    RunStats.from(env).runCustomTask("waitForProcessTermination") {
      waitPreviousProcessTermination(devices, packageName, indicator)
    }

    val processHandler = NopProcessHandler()
    val console = createConsole()
    val device = devices.single()

    if (configuration.CLEAR_LOGCAT) {
      project.messageBus.syncPublisher(ClearLogcatListener.TOPIC).clearLogcat(device.serialNumber)
    }
    if (configuration.CLEAR_APP_STORAGE) {
      clearAppStorage(project, device, packageName)
    }
    LaunchUtils.initiateDismissKeyguard(device)

    doRun(devices, processHandler, indicator, console, true)

    indicator.text = "Connecting debugger"
    val session = RunStats.from(env).runCustomTask("startDebuggerSession") {
      val debuggerTask = getBaseDebuggerTask(configuration.androidDebuggerContext, facet, env)
      debuggerTask.perform(device, packageName, env, indicator, console)
    }
    if (configuration.SHOW_LOGCAT_AUTOMATICALLY) {
      project.messageBus.syncPublisher(ShowLogcatListener.TOPIC).showLogcat(device, packageName)
    }
    session.runContentDescriptor
  }

  override fun applyChanges(indicator: ProgressIndicator): RunContentDescriptor = indicatorRunBlockingCancellable(indicator) {
    val devices = getDevices(deviceFutures, indicator, RunStats.from(env))

    /**
     * We use [distinct] because there can be more than one RunContentDescriptor for given configuration and given devices.
     *
     * Every time user does AC or ACC we are obligated to create a new RunContentDescriptor. So we create, but don't show it in UI by setting [RunContentDescriptor.isHiddenContent] to false.
     * Multiple [RunContentDescriptor] -> multiple [ProcessHandler]. But it's the same instance of [ProcessHandler].
     */
    val processHandlers = settings.getProcessHandlersForDevices(project, devices).distinct()

    /**
     * Searching for first not hidden [RunContentDescriptor].
     */
    val existingRunContentDescriptor = processHandlers.mapNotNull {
      withContext(uiThread) {
        RunContentManager.getInstance(project).findContentDescriptor(env.executor, it)?.takeIf { !it.isHiddenContent }
      }
    }.firstOrNull()

    val packageName = applicationIdProvider.packageName
    val processHandler = existingRunContentDescriptor?.processHandler ?: AndroidProcessHandler(project, packageName).apply {
      devices.forEach { addTargetDevice(it) }
    }
    fillStats(RunStats.from(env), packageName)

    val console = existingRunContentDescriptor?.executionConsole as? ConsoleView ?: createConsole()

    doRun(devices, processHandler, indicator, console, false)

    withContext(uiThread) {
      val attachedContent = existingRunContentDescriptor?.attachedContent
      if (attachedContent == null) {
        createRunContentDescriptor(processHandler, console, env)
      }
      else {
        object : RunContentDescriptor(existingRunContentDescriptor.executionConsole,
                                      existingRunContentDescriptor.processHandler,
                                      existingRunContentDescriptor.component,
                                      existingRunContentDescriptor.displayName,
                                      existingRunContentDescriptor.icon, null as Runnable?,
                                      existingRunContentDescriptor.restartActions) {
          override fun isHiddenContent() = true
        }.apply {
            setAttachedContent(attachedContent)
            // Same as [RunContentBuilder.showRunContent]
            Disposer.register(project, this)
          }
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
          if (e.cause is DeployerException) {
            throw AndroidExecutionException((e.cause as DeployerException).id, e.message)
          }
          throw e
        }
      }
    }
  }

  private fun fillStats(stats: RunStats, packageName: String) {
    stats.setPackage(packageName)
    stats.setApplyChangesFallbackToRun(isApplyChangesFallbackToRun())
    stats.setApplyCodeChangesFallbackToRun(isApplyCodeChangesFallbackToRun())
    stats.setRunAlwaysInstallWithPm(configuration.ALWAYS_INSTALL_WITH_PM)
    stats.setIsComposeProject(usesCompose(project))
  }

  private fun isApplyCodeChangesFallbackToRun(): Boolean {
    return DeploymentConfiguration.getInstance().APPLY_CODE_CHANGES_FALLBACK_TO_RUN
  }

  private fun isApplyChangesFallbackToRun(): Boolean {
    return DeploymentConfiguration.getInstance().APPLY_CHANGES_FALLBACK_TO_RUN
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

  private inline fun <T> RunStats.runCustomTask(taskId: String, task: () -> T): T {
    val customTask = beginCustomTask(taskId)
    return try {
      task()
    }
    catch (t: Throwable) {
      endCustomTask(customTask, t)
      throw t
    }.also {
      endCustomTask(customTask, null)
    }
  }
}
