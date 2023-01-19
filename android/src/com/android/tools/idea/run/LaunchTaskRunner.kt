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

import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.ApplicationTerminator
import com.android.tools.idea.execution.common.RunConfigurationNotifier.notifyError
import com.android.tools.idea.execution.common.RunConfigurationNotifier.notifyWarning
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.ShowLogcatListener.Companion.getShowLogcatLinkText
import com.android.tools.idea.run.applychanges.findExistingSessionAndMaybeDetachForColdSwap
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.createRunContentDescriptor
import com.android.tools.idea.run.debug.captureLogcatOutputToProcessHandler
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchResult
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTasksProvider
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.stats.RunStats
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testartifacts.instrumented.orchestrator.MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBuildCommonUtils.isInstrumentationTestConfiguration
import org.jetbrains.android.util.AndroidBuildCommonUtils.isTestConfiguration
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.catchError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class LaunchTaskRunner(
  private val consoleProvider: ConsoleProvider,
  private val applicationIdProvider: ApplicationIdProvider,
  private val myEnv: ExecutionEnvironment,
  override val deployTarget: DeployTarget,
  private val myLaunchTasksProvider: LaunchTasksProvider,
) : AndroidConfigurationExecutor {

  val project = myEnv.project
  private val myOnFinished = ArrayList<Runnable>()
  override val configuration = myEnv.runProfile as RunConfiguration

  /**
   * Returns a target Android process ID to be monitored by [AndroidProcessHandler].
   *
   * If this run is a standard Android application or instrumentation test without test orchestration, the target Android process ID
   * is simply the application name. Otherwise we should monitor the test orchestration process because the orchestrator starts and
   * kills the target application process per test case which confuses AndroidProcessHandler (b/150320657).
   */
  @Throws(ExecutionException::class)
  private fun getMasterAndroidProcessId(runProfile: RunProfile): String {
    if (runProfile !is AndroidTestRunConfiguration) {
      return applicationIdProvider.packageName
    }
    return MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME.getOrDefault(
      runProfile.getTestExecutionOption(AndroidFacet.getInstance(runProfile.configurationModule.module!!)),
      applicationIdProvider.packageName)
  }

  override fun run(indicator: ProgressIndicator): Promise<RunContentDescriptor> {
    findExistingSessionAndMaybeDetachForColdSwap(myEnv)
    val devices = waitForDevices(indicator)
    waitPreviousProcessTermination(devices, applicationIdProvider.packageName, indicator)

    val processHandler = AndroidProcessHandler(
      project,
      getMasterAndroidProcessId(myEnv.runProfile),
      { it.forceStop(getMasterAndroidProcessId(myEnv.runProfile)) },
      shouldAutoTerminate()
    )

    val console = createConsole(processHandler)
    doRun(devices, processHandler, console, indicator)

    return createRunContentDescriptor(processHandler, console, myEnv)
  }

  private fun doRun(devices: List<IDevice>, processHandler: ProcessHandler, console: ConsoleView, indicator: ProgressIndicator) {
    val applicationId = applicationIdProvider.packageName
    val stat = RunStats.from(myEnv).apply { setPackage(applicationId) }
    stat.beginLaunchTasks()
    try {
      indicator.isIndeterminate = false
      val launchStatus = ProcessHandlerLaunchStatus(processHandler)
      val consolePrinter = ProcessHandlerConsolePrinter(processHandler)

      printLaunchTaskStartedMessage(consolePrinter)
      indicator.text = "Waiting for all target devices to come online"

      myLaunchTasksProvider.fillStats(stat)

      // Create launch tasks for each device.
      val launchTaskMap: MutableMap<IDevice, List<LaunchTask>> = HashMap(devices.size)
      for (device in devices) {
        val launchTasks = myLaunchTasksProvider.getTasks(device, launchStatus, consolePrinter)
        launchTaskMap[device] = launchTasks
      }
      val completedStepsCount = Ref(0)
      val totalScheduledStepsCount = launchTaskMap.values.sumOf { getTotalDuration(it) }

      // A list of devices that we have launched application successfully.
      val launchedDevices = ArrayList<IDevice>()
      for ((device, value) in launchTaskMap) {
        runLaunchTasks(
          value,
          LaunchContext(project, myEnv.executor, device, launchStatus, consolePrinter, processHandler, indicator),
          completedStepsCount,
          totalScheduledStepsCount
        )
        launchedDevices.add(device)
        // Notify listeners of the deployment.
        if (isLaunchingTest()) {
          project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingTest(device.serialNumber, project)
        }
        else {
          project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingApp(device.serialNumber, project)
        }
        if (processHandler is AndroidProcessHandler) {
          processHandler.addTargetDevice(device)
          if (!StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.get()) {
            console.printHyperlink(getShowLogcatLinkText(device)) { project ->
              project.messageBus.syncPublisher(ShowLogcatListener.TOPIC).showLogcat(device, applicationId)
            }
          }
        }
      }
      if (launchedDevices.isEmpty()) {
        throw ExecutionException("Failed to launch an application on all devices")
      }

      for (runnable in myOnFinished) {
        ApplicationManager.getApplication().invokeLater(runnable)
      }
    }
    finally {
      stat.endLaunchTasks()
    }
  }

  private fun waitPreviousProcessTermination(devices: List<IDevice>, applicationId:String, indicator: ProgressIndicator) {
    val waitApplicationTerminationTask = Futures.whenAllSucceed(
      ContainerUtil.map(devices) { device: IDevice ->
        MoreExecutors.listeningDecorator(AppExecutorUtil.getAppExecutorService()).submit {
          val terminator = ApplicationTerminator(device, applicationId)
          try {
            if (!terminator.killApp()) {
              throw CancellationException("Could not terminate running app $applicationId")
            }
          }
          catch (e: ExecutionException) {
            throw CancellationException("Could not terminate running app $applicationId")
          }
        }
      }).run({}, AppExecutorUtil.getAppExecutorService())

    ProgressIndicatorUtils.awaitWithCheckCanceled(waitApplicationTerminationTask, indicator)
    if (waitApplicationTerminationTask.isCancelled) {
      throw ExecutionException(String.format("Couldn't terminate the existing process for %s.", applicationId))
    }
  }

  override fun runAsInstantApp(indicator: ProgressIndicator): Promise<RunContentDescriptor> {
    return run(indicator)
  }

  override fun debug(indicator: ProgressIndicator): Promise<RunContentDescriptor> {
    val applicationId = applicationIdProvider.packageName

    val devices = waitForDevices(indicator)
    if (devices.size != 1) {
      throw ExecutionException("Cannot launch a debug session on more than 1 device.")
    }
    waitPreviousProcessTermination(devices, applicationId, indicator)

    findExistingSessionAndMaybeDetachForColdSwap(myEnv)

    val processHandler = NopProcessHandler()
    val console = createConsole(processHandler)
    doRun(devices, processHandler, console, indicator)


    val device = devices.single()
    val debuggerTask = myLaunchTasksProvider.connectDebuggerTask
                       ?: throw RuntimeException(
                         "ConnectDebuggerTask is null for task provider " + myLaunchTasksProvider.javaClass.name)
    indicator.text = "Connecting debugger"
    return debuggerTask.perform(device, applicationId, myEnv, processHandler)
      .then { session ->
        ApplicationManager.getApplication().executeOnPooledThread {
          DeploymentApplicationService.instance
            .findClient(device, applicationId).firstOrNull()?.let { client: Client ->
              captureLogcatOutputToProcessHandler(client, session.consoleView, session.debugProcess.processHandler)
            }
        }
        session.runContentDescriptor
      }
  }

  override fun applyChanges(indicator: ProgressIndicator): Promise<RunContentDescriptor> {
    val listenableDeviceFutures = deployTarget.getDevices(project)?.get() ?: throw ExecutionException("No devices found")
    val devices = listenableDeviceFutures.map { deviceFuture -> waitForDevice(deviceFuture, indicator) }
    val processHandler = findExistingSessionAndMaybeDetachForColdSwap(myEnv).processHandler ?: AndroidProcessHandler(
      project,
      getMasterAndroidProcessId(myEnv.runProfile),
      { it.forceStop(getMasterAndroidProcessId(myEnv.runProfile)) },
      shouldAutoTerminate()
    )

    val console = findExistingSessionAndMaybeDetachForColdSwap(myEnv).executionConsole as? ConsoleView ?: createConsole(processHandler)

    doRun(devices, processHandler, console, indicator)
    val descriptorPromise = AsyncPromise<RunContentDescriptor>()

    runInEdt {
      descriptorPromise.catchError {
        val descriptor = RunContentManager.getInstance(project).findContentDescriptor(myEnv.executor, processHandler)

        if (descriptor?.attachedContent == null) {
          createRunContentDescriptor(processHandler, console, myEnv).processed(descriptorPromise)
        }
        else {
          val hiddenRunContentDescriptor = descriptor.takeIf { it is HiddenRunContentDescriptor }
                                           ?: HiddenRunContentDescriptor(descriptor)
          descriptorPromise.setResult(hiddenRunContentDescriptor)
        }
      }
    }
    return descriptorPromise
  }

  private fun createConsole(processHandler: ProcessHandler): ConsoleView = invokeAndWaitIfNeeded {
    consoleProvider.createAndAttach(project, processHandler, myEnv.executor)
  }

  override fun applyCodeChanges(indicator: ProgressIndicator): Promise<RunContentDescriptor> {
    return applyChanges(indicator)
  }

  private fun runLaunchTasks(
    launchTasks: List<LaunchTask>,
    launchContext: LaunchContext,
    completedStepsCount: Ref<Int>,
    totalScheduledStepsCount: Int
  ) {
    // Update the indicator progress.
    val indicator = launchContext.progressIndicator
    val stat = RunStats.from(myEnv)
    indicator.fraction = (completedStepsCount.get().toFloat() / totalScheduledStepsCount).toDouble()
    for (task in launchTasks) {
      if (task.shouldRun(launchContext)) {
        indicator.checkCanceled()
        val details = stat.beginLaunchTask(task)
        indicator.text = task.description
        val launchResult = task.run(launchContext)
        myOnFinished.addAll(launchResult.onFinishedCallbacks())

        when (launchResult.result) {
          LaunchResult.Result.SUCCESS -> stat.endLaunchTask(task, details, true)
          LaunchResult.Result.ERROR -> {
            stat.endLaunchTask(task, details, false)
            if (launchResult.message.isNotEmpty()) {
              notifyError(project, configuration.name, launchResult.message)
            }
            stat.setErrorId(launchResult.errorId)
            throw ExecutionException(launchResult.message)
          }

          LaunchResult.Result.WARNING -> {
            stat.endLaunchTask(task, details, false)
            if (launchResult.message.isNotEmpty()) {
              notifyWarning(project, configuration.name, launchResult.message)
            }
          }
        }
      }

      // Update the indicator progress.
      completedStepsCount.set(completedStepsCount.get() + task.duration)
      indicator.fraction = (completedStepsCount.get().toFloat() / totalScheduledStepsCount).toDouble()
    }
  }

  private fun isLaunchingTest(): Boolean {
    val configTypeId = myEnv.runnerAndConfigurationSettings?.type?.id ?: return false
    return isTestConfiguration(configTypeId) || isInstrumentationTestConfiguration(configTypeId)
  }

  private fun shouldAutoTerminate(): Boolean {
    // AndroidProcessHandler should not be closed even if the target application process is killed. During an
    // instrumentation tests, the target application may be killed in between test cases by test runner. Only test
    // runner knows when all test run completes.
    return myEnv.runProfile !is AndroidTestRunConfiguration
  }

  private fun printLaunchTaskStartedMessage(consolePrinter: ConsolePrinter) {
    val launchString = StringBuilder("\n")
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    launchString.append(dateFormat.format(Date())).append(": ")
    val launchVerb = when (myEnv.getUserData(SwapInfo.SWAP_INFO_KEY)?.type) {
      SwapInfo.SwapType.APPLY_CHANGES -> "Applying changes to"
      SwapInfo.SwapType.APPLY_CODE_CHANGES -> "Applying code changes to"
      else -> "Launching"
    }
    launchString.append(launchVerb).append(" ")
    launchString.append("'").append(configuration.name).append("'")
    if (!StringUtil.isEmpty(myEnv.executionTarget.displayName)) {
      launchString.append(" on ")
      launchString.append(myEnv.executionTarget.displayName)
    }
    launchString.append(".")
    consolePrinter.stdout(launchString.toString())
  }

  private fun waitForDevices(indicator: ProgressIndicator): List<IDevice> {
    val listenableDeviceFutures = deployTarget.getDevices(project)?.get() ?: throw ExecutionException("No devices found")
    return listenableDeviceFutures.map { deviceFuture -> waitForDevice(deviceFuture, indicator) }
  }

  private fun waitForDevice(deviceFuture: ListenableFuture<IDevice>, indicator: ProgressIndicator): IDevice {
    indicator.text = "Waiting for device to come online"
    val stat = RunStats.from(myEnv)
    stat.beginWaitForDevice()
    while (!indicator.isCanceled) {
      try {
        val device = deviceFuture[1, TimeUnit.SECONDS]
        stat.endWaitForDevice(device)
        return device
      }
      catch (ignored: TimeoutException) {
        // Let's check the cancellation request then continue to wait for a device again.
      }
      catch (e: InterruptedException) {
        throw ExecutionException("Interrupted while waiting for device")
      }
      catch (e: java.util.concurrent.ExecutionException) {
        throw ExecutionException("Error while waiting for device: " + e.cause!!.message)
      }
    }
    throw ExecutionException("Device is not launched")
  }

  companion object {

    private fun getTotalDuration(launchTasks: List<LaunchTask>): Int {
      var total = 0
      for (task in launchTasks) {
        total += task.duration
      }
      return total
    }
  }
}