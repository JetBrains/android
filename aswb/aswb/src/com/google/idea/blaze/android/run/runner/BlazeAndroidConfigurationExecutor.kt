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
package com.google.idea.blaze.android.run.runner

import com.android.ddmlib.IDevice
import com.android.tools.deployer.ApkVerifierTracker
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.editors.liveedit.LiveEditService
import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.ApplicationTerminator
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.execution.common.clearAppStorage
import com.android.tools.idea.execution.common.getProcessHandlersForDevices
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.profilers.AndroidProfilerLaunchTaskContributor
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ConsoleProvider
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.LiveEditHelper
import com.android.tools.idea.run.ShowLogcatListener
import com.android.tools.idea.run.ShowLogcatListener.Companion.getShowLogcatLinkText
import com.android.tools.idea.run.configuration.execution.createRunContentDescriptor
import com.android.tools.idea.run.configuration.execution.getDevices
import com.android.tools.idea.run.configuration.execution.println
import com.android.tools.idea.run.util.LaunchUtils
import com.google.idea.blaze.android.run.BazelAndroidRunContext
import com.google.idea.blaze.android.run.binary.UserIdHelper
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * [AndroidConfigurationExecutor] for [BlazeRunConfiguration].
 */
class BlazeAndroidConfigurationExecutor(
  private val consoleProvider: ConsoleProvider,
  private val applicationContext: ApplicationProjectContext,
  private val env: ExecutionEnvironment,
  private val deviceFutures: DeviceFutures,
  private val runContext: BazelAndroidRunContext,
  private val launchStrategy: BlazeAndroidDeployAndLaunchStrategy,
  private val launchOptions: LaunchOptions,
  private val liveEditService: LiveEditService
) : AndroidConfigurationExecutor {

  val project = env.project
  override val configuration = env.runProfile as RunConfiguration
  private val LOG = Logger.getInstance(this::class.java)

  override fun run(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable {
    val applicationId = applicationContext.applicationId
    val devices = getDevices(env, deviceFutures, indicator)

    env.runnerAndConfigurationSettings?.getProcessHandlersForDevices(project, devices)?.forEach { it.destroyProcess() }

    waitPreviousProcessTermination(devices, applicationId, indicator)

    val processHandler = AndroidProcessHandler(applicationId, { it.forceStop(applicationId) })

    val console = createConsole(processHandler)
    doRun(devices, processHandler, false, indicator, console, applicationContext)

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

  private suspend fun getTasks(device: IDevice, isDebug: Boolean): List<BlazeLaunchTask> {
    return buildList {
      val launchTasks = this
      val packageName = runContext.applicationProjectContext.applicationId

      val userId = launchStrategy.getUserId(device)

      // NOTE: Task for opening the profiler tool-window should come before deployment
      // to ensure the tool-window opens correctly. This is required because starting
      // the profiler session requires the tool-window to be open.
      if (AndroidProfilerLaunchTaskContributor.isProfilerLaunch(runContext.executor)) {
        launchTasks.add(BlazeAndroidOpenProfilerWindowTask(project))
      }

      if (launchOptions.isDeploy) {
        val userIdFlags = UserIdHelper.getFlagsFromUserId(userId)
        val skipVerification =
          ApkVerifierTracker.getSkipVerificationInstallationFlag(device, packageName)
        val pmInstallOption = if (skipVerification != null) "$userIdFlags $skipVerification" else userIdFlags
        val deployOptions =
          DeployOptions(
            disabledDynamicFeatures = emptyList(),
            pmInstallFlags = pmInstallOption,
            installOnAllUsers = false,
            alwaysInstallWithPm = false,
            allowAssumeVerified = false
          )
        val deployTasks =
          launchStrategy.getDeployTasks(runContext, device, deployOptions)
        launchTasks.addAll(deployTasks)
      }

      if (isDebug) {
        launchTasks.add(
          CheckApkDebuggableTask(project, runContext.apkProvider)
        )
      }

      val amStartOptions = mutableListOf<String>()
      amStartOptions.add(launchStrategy.getAmStartOptions())
      if (AndroidProfilerLaunchTaskContributor.isProfilerLaunch(runContext.executor)) {
        amStartOptions.add(
          AndroidProfilerLaunchTaskContributor.getAmStartOptions(
            project,
            packageName,
            runContext.profileState,
            device,
            runContext.executor
          )
        )
      }
      val appLaunchTask =
        launchStrategy.getApplicationLaunchTask(
          runContext, isDebug, userId, amStartOptions.joinToString(separator = " ")
        )
      if (appLaunchTask != null) {
        launchTasks.add(appLaunchTask)
        // TODO(arvindanekal): the live edit api changed and we cannot get the apk here to create
        // live
        // edit; the live edit team or Arvind need to fix this
      }
    }
  }

  private suspend fun doRun(
    devices: List<IDevice>,
    processHandler: ProcessHandler,
    isDebug: Boolean,
    indicator: ProgressIndicator,
    console: ConsoleView,
    applicationProjectContext: ApplicationProjectContext
  ) = coroutineScope {
    val applicationId = applicationProjectContext.applicationId
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
          getTasks(device, isDebug).forEach {
            it.run(launchContext)
          }
          LiveEditHelper().invokeLiveEdit(
            liveEditService,
            env,
            applicationProjectContext,
            runContext.apkProvider.getApks(device),
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
    val applicationId = applicationContext.applicationId
    val devices = getDevices(env, deviceFutures, indicator)

    if (devices.size != 1) {
      throw ExecutionException("Cannot launch a debug session on more than 1 device.")
    }

    env.runnerAndConfigurationSettings?.getProcessHandlersForDevices(project, devices)?.forEach { it.destroyProcess() }

    waitPreviousProcessTermination(devices, applicationId, indicator)

    val processHandler = NopProcessHandler()
    val console = createConsole(processHandler)
    doRun(devices, processHandler, true, indicator, console, applicationContext)

    val device = devices.single()
    indicator.text = "Connecting debugger"

    // Do not get debugger state directly from the debugger itself.
    // See BlazeAndroidDebuggerService#getDebuggerState for an explanation.
    val isNativeDebuggingEnabled = isNativeDebuggingEnabled(launchOptions)
    val debuggerService = BlazeAndroidDebuggerService.getInstance(project)
    val debugger = debuggerService.getDebugger(isNativeDebuggingEnabled) ?: throw ExecutionException("Can't find AndroidDebugger for launch")
    val debuggerState = debuggerService.getDebuggerState(debugger) ?: throw ExecutionException("Can't find AndroidDebuggerState for launch")
    if (isNativeDebuggingEnabled) {
      debuggerService.configureNativeDebugger(debuggerState, runContext.apkProvider)
    }

    val debugSession = launchStrategy.startDebuggerSession(
      runContext, debugger, debuggerState, env, device, console, indicator
    ) ?: throw ExecutionException("Failed to start debugger")

    debugSession.runContentDescriptor
  }

  private suspend fun createConsole(processHandler: ProcessHandler): ConsoleView = withContext(uiThread) {
    consoleProvider.createAndAttach(project, processHandler, env.executor)
  }

  private fun printLaunchTaskStartedMessage(consoleView: ConsoleView) {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
    consoleView.println("$date: Launching ${configuration.name} on '${env.executionTarget.displayName}.")
  }

  private fun isNativeDebuggingEnabled(launchOptions: LaunchOptions): Boolean {
    val flag = launchOptions.getExtraOption(NATIVE_DEBUGGING_ENABLED)
    return flag is Boolean && flag
  }

  companion object {
    const val NATIVE_DEBUGGING_ENABLED: String = "NATIVE_DEBUGGING_ENABLED"
  }
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