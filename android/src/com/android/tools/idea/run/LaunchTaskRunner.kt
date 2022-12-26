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
import com.android.tools.idea.execution.common.RunConfigurationNotifier.notifyInfo
import com.android.tools.idea.execution.common.RunConfigurationNotifier.notifyWarning
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.ShowLogcatListener.Companion.getShowLogcatLinkText
import com.android.tools.idea.run.configuration.execution.createRunContentDescriptor
import com.android.tools.idea.run.debug.captureLogcatOutputToProcessHandler
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchResult
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTaskDurations
import com.android.tools.idea.run.tasks.LaunchTasksProvider
import com.android.tools.idea.run.util.LaunchStatus
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.stats.RunStats
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.android.util.AndroidBuildCommonUtils.isInstrumentationTestConfiguration
import org.jetbrains.android.util.AndroidBuildCommonUtils.isTestConfiguration
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class LaunchTaskRunner(
  val project: Project,
  private val myApplicationId: String,
  private val myEnv: ExecutionEnvironment,
  private val myProcessHandler: ProcessHandler,
  val deployTarget: DeployTarget,
  private val myLaunchTasksProvider: LaunchTasksProvider,
  private val myStats: RunStats,
  private val myConsole: ConsoleView
) {

  private var myError: String? = null
  private val myOnFinished = ArrayList<Runnable>()
  val configuration = myEnv.runProfile as RunConfiguration

  fun run(indicator: ProgressIndicator): Promise<RunContentDescriptor> {
    myStats.beginLaunchTasks()
    try {
      val destroyProcessOnCancellation = !isSwap
      indicator.isIndeterminate = false
      val launchStatus = ProcessHandlerLaunchStatus(myProcessHandler)
      val consolePrinter = ProcessHandlerConsolePrinter(myProcessHandler)
      val listenableDeviceFutures = deployTarget.getDevices(project)?.get() ?: throw ExecutionException("No devices found")
      val shouldConnectDebugger = myEnv.executor is DefaultDebugExecutor && !isSwap
      if (shouldConnectDebugger && listenableDeviceFutures.size != 1) {
        throw ExecutionException("Cannot launch a debug session on more than 1 device.")
      }
      printLaunchTaskStartedMessage(consolePrinter)
      indicator.text = "Waiting for all target devices to come online"
      val devices = listenableDeviceFutures.mapNotNull { deviceFuture ->
        waitForDevice(deviceFuture, indicator, launchStatus, destroyProcessOnCancellation)
      }
      if (devices.size != listenableDeviceFutures.size) {
        // Halt execution if any of target devices are unavailable.
        throw ExecutionException("Some of target devices are unavailable")
      }

      // Wait for the previous android process with the same application ID to be terminated before we start the new process.
      // This step is necessary only for the standard launch (non-swap, android process handler). Ignore this step for
      // hot-swapping or debug runs.
      if (!isSwap && myProcessHandler is AndroidProcessHandler) {
        val waitApplicationTerminationTask = Futures.whenAllSucceed(
          ContainerUtil.map(devices) { device: IDevice ->
            MoreExecutors.listeningDecorator(AppExecutorUtil.getAppExecutorService()).submit {
              val terminator = ApplicationTerminator(device, myApplicationId)
              try {
                if (!terminator.killApp()) {
                  throw CancellationException("Could not terminate running app $myApplicationId")
                }
              }
              catch (e: ExecutionException) {
                throw CancellationException("Could not terminate running app $myApplicationId")
              }
              if (device.isOnline) {
                myProcessHandler.addTargetDevice(device)
              }
            }
          }).run({}, AppExecutorUtil.getAppExecutorService())

        ProgressIndicatorUtils.awaitWithCheckCanceled(waitApplicationTerminationTask, indicator)
        if (waitApplicationTerminationTask.isCancelled) {
          throw ExecutionException(String.format("Couldn't terminate the existing process for %s.", myApplicationId))
        }
      }
      myLaunchTasksProvider.fillStats(myStats)

      // Create launch tasks for each device.
      val launchTaskMap: MutableMap<IDevice?, List<LaunchTask>> = HashMap(devices.size)
      for (device in devices) {
        try {
          val launchTasks = myLaunchTasksProvider.getTasks(device, launchStatus, consolePrinter)
          launchTaskMap[device] = launchTasks
        }
        catch (e: IllegalStateException) {
          launchStatus.terminateLaunch(e.message, !isSwap)
          Logger.getInstance(LaunchTaskRunner::class.java).error(e)
          throw ExecutionException(e)
        }
      }
      val completedStepsCount = Ref(0)
      val totalScheduledStepsCount = launchTaskMap.values.sumOf { getTotalDuration(it, shouldConnectDebugger) }

      // A list of devices that we have launched application successfully.
      val launchedDevices: MutableList<IDevice?> = ArrayList()
      for ((device, value) in launchTaskMap) {
        val isSucceeded = runLaunchTasks(
          value,
          LaunchContext(project, myEnv.executor, device!!, launchStatus, consolePrinter, myProcessHandler, indicator),
          destroyProcessOnCancellation,
          completedStepsCount,
          totalScheduledStepsCount
        )
        if (isSucceeded) {
          launchedDevices.add(device)
        }
        else {
          // Manually detach a device here because devices may not be detached automatically when
          // AndroidProcessHandler is instantiated with autoTerminate = false. For example,
          // AndroidTestRunConfiguration sets autoTerminate false because the target application
          // process may be killed and re-spawned for each test cases with Android test orchestrator
          // enabled. Please also see documentation in AndroidProcessHandler for more details.
          detachDevice(device)
        }
      }
      if (launchedDevices.isEmpty()) {
        throw ExecutionException("Failed to launch an application on all devices")
      }

      // A debug session task should be performed sequentially at the end.
      if (shouldConnectDebugger) {
        assert(launchedDevices.size == 1)
        val device = launchedDevices[0]
        val debuggerTask = myLaunchTasksProvider.connectDebuggerTask
                           ?: throw RuntimeException(
                             "ConnectDebuggerTask is null for task provider " + myLaunchTasksProvider.javaClass.name)
        indicator.text = "Connecting debugger"
        return debuggerTask.perform(device!!, myApplicationId, myEnv, myProcessHandler)
          .onSuccess { session ->
            ApplicationManager.getApplication().executeOnPooledThread {
              DeploymentApplicationService.instance
                .findClient(device, myApplicationId).firstOrNull()?.let { client: Client? ->
                  captureLogcatOutputToProcessHandler(client!!, session.consoleView, session.debugProcess.processHandler)
                }
            }
          }
          .then { session ->
            // Update the indicator progress bar.
            completedStepsCount.set(completedStepsCount.get() + LaunchTaskDurations.CONNECT_DEBUGGER)
            indicator.fraction = (completedStepsCount.get().toFloat() / totalScheduledStepsCount).toDouble()
            session.runContentDescriptor
          }
      }
      val descriptorPromise = AsyncPromise<RunContentDescriptor>()

      runInEdt {
        var descriptor: RunContentDescriptor? = null
        if (isSwap) {
          // If we're hot swapping, we want to use the currently-running ContentDescriptor,
          // instead of making a new one (which showRunContent actually does).
          val manager = RunContentManager.getInstance(project)
          // Note we may still end up with a null descriptor since the user could close the tool tab after starting a hotswap.
          descriptor = manager.findContentDescriptor(myEnv.executor, myProcessHandler)
        }
        if (descriptor?.attachedContent == null) {
          val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
          Disposer.register(project, console)
          createRunContentDescriptor(myProcessHandler, console, myEnv).processed(descriptorPromise)
        }
        else {
          descriptorPromise.setResult(descriptor)
        }
      }
      return descriptorPromise
    }
    finally {
      myStats.endLaunchTasks()
      if (myError != null) {
        myStats.fail()
      }
      else {
        myStats.success()
        for (runnable in myOnFinished) {
          ApplicationManager.getApplication().invokeLater(runnable)
        }
      }
    }
  }

  private fun runLaunchTasks(
    launchTasks: List<LaunchTask>,
    launchContext: LaunchContext,
    destroyProcessOnCancellation: Boolean,
    completedStepsCount: Ref<Int>,
    totalScheduledStepsCount: Int
  ): Boolean {
    // Update the indicator progress.
    val indicator = launchContext.progressIndicator
    indicator.fraction = (completedStepsCount.get().toFloat() / totalScheduledStepsCount).toDouble()
    val device = launchContext.device
    val launchStatus = launchContext.launchStatus
    var numWarnings = 0
    for (task in launchTasks) {
      if (!checkIfLaunchIsAliveAndTerminateIfCancelIsRequested(indicator, launchStatus, destroyProcessOnCancellation)) {
        return false
      }
      if (task.shouldRun(launchContext)) {
        val details = myStats.beginLaunchTask(task)
        indicator.text = task.description
        val launchResult = task.run(launchContext)
        myOnFinished.addAll(launchResult.onFinishedCallbacks())
        val result = launchResult.result
        myStats.endLaunchTask(task, details, result != LaunchResult.Result.ERROR)
        if (result != LaunchResult.Result.SUCCESS) {
          myError = launchResult.message
          launchContext.consolePrinter.stderr(launchResult.consoleMessage)
          if (launchResult.message.isNotEmpty()) {
            if (result == LaunchResult.Result.ERROR) {
              notifyError(project, configuration.name, launchResult.message)
            }
            else {
              notifyWarning(project, configuration.name, launchResult.message)
            }
          }
          // Show the tool window when we have an error.
          ApplicationManager.getApplication().invokeLater {
            RunContentManager.getInstance(
              project
            ).toFrontRunContent(
              myEnv.executor, myProcessHandler
            )
          }
          if (result == LaunchResult.Result.ERROR) {
            myStats.setErrorId(launchResult.errorId)
            return false
          }
          else {
            numWarnings++
          }
        }

        // Notify listeners of the deployment.
        if (isLaunchingTest()) {
          project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingTest(device.serialNumber, project)
        }
        else {
          project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingApp(device.serialNumber, project)
        }
      }

      // Update the indicator progress.
      completedStepsCount.set(completedStepsCount.get() + task.duration)
      indicator.fraction = (completedStepsCount.get().toFloat() / totalScheduledStepsCount).toDouble()
    }
    if (!StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.get()) {
      myConsole.printHyperlink(getShowLogcatLinkText(device)) { project ->
        project.messageBus.syncPublisher(ShowLogcatListener.TOPIC).showLogcat(device, myApplicationId)
      }
    }
    val launchType = myLaunchTasksProvider.launchTypeDisplayName
    if (numWarnings == 0) {
      notifyInfo(
        project, configuration.name,
        AndroidBundle.message("android.launch.task.succeeded", launchType)
      )
    }
    else {
      notifyWarning(
        project, configuration.name,
        AndroidBundle.message(
          "android.launch.task.succeeded.with.warnings", launchType,
          numWarnings
        )
      )
    }
    return true
  }

  private fun isLaunchingTest(): Boolean {
    val configTypeId = myEnv.runnerAndConfigurationSettings?.type?.id ?: return false
    return isTestConfiguration(configTypeId) || isInstrumentationTestConfiguration(configTypeId)
  }

  private fun detachDevice(device: IDevice?) {
    if (!isSwap && myProcessHandler is AndroidProcessHandler) {
      myProcessHandler.detachDevice(device!!)
    }
  }

  private fun printLaunchTaskStartedMessage(consolePrinter: ConsolePrinter) {
    val launchString = StringBuilder("\n")
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    launchString.append(dateFormat.format(Date())).append(": ")
    launchString.append(launchVerb).append(" ")
    launchString.append("'").append(configuration.name).append("'")
    if (!StringUtil.isEmpty(myEnv.executionTarget.displayName)) {
      launchString.append(" on ")
      launchString.append(myEnv.executionTarget.displayName)
    }
    launchString.append(".")
    consolePrinter.stdout(launchString.toString())
  }

  private fun waitForDevice(
    deviceFuture: ListenableFuture<IDevice>,
    indicator: ProgressIndicator,
    launchStatus: LaunchStatus,
    destroyProcess: Boolean
  ): IDevice? {
    myStats.beginWaitForDevice()
    var device: IDevice? = null
    while (checkIfLaunchIsAliveAndTerminateIfCancelIsRequested(indicator, launchStatus, destroyProcess)) {
      try {
        device = deviceFuture[1, TimeUnit.SECONDS]
        break
      }
      catch (ignored: TimeoutException) {
        // Let's check the cancellation request then continue to wait for a device again.
      }
      catch (e: InterruptedException) {
        launchStatus.terminateLaunch("Interrupted while waiting for device", destroyProcess)
        break
      }
      catch (e: java.util.concurrent.ExecutionException) {
        launchStatus.terminateLaunch("Error while waiting for device: " + e.cause!!.message, destroyProcess)
        break
      }
    }
    myStats.endWaitForDevice(device)
    return device
  }

  private val isSwap = myEnv.getUserData(SwapInfo.SWAP_INFO_KEY) != null
  private val launchVerb: String
    get() {
      val swapInfo = myEnv.getUserData(SwapInfo.SWAP_INFO_KEY)
      if (swapInfo != null) {
        if (swapInfo.type == SwapInfo.SwapType.APPLY_CHANGES) {
          return "Applying changes to"
        }
        else if (swapInfo.type == SwapInfo.SwapType.APPLY_CODE_CHANGES) {
          return "Applying code changes to"
        }
      }
      return "Launching"
    }

  companion object {
    /**
     * Checks if the launch is still alive and good to continue. Upon cancellation request, it updates a given `launchStatus` to
     * be terminated state. The associated process will be forcefully destroyed if `destroyProcess` is true.
     *
     * @param indicator      a progress indicator to check the user cancellation request
     * @param launchStatus   a launch status to be checked and updated upon the cancellation request
     * @param destroyProcess true to destroy the associated process upon cancellation, false to detach the process instead
     * @return true if the launch is still good to go, false otherwise.
     */
    private fun checkIfLaunchIsAliveAndTerminateIfCancelIsRequested(
      indicator: ProgressIndicator, launchStatus: LaunchStatus, destroyProcess: Boolean
    ): Boolean {
      // Check for cancellation via stop button or unexpected failures in launch tasks.
      if (launchStatus.isLaunchTerminated) {
        return false
      }

      // Check for cancellation via progress bar.
      if (indicator.isCanceled) {
        launchStatus.terminateLaunch("User cancelled launch", destroyProcess)
        return false
      }
      return true
    }

    private fun getTotalDuration(launchTasks: List<LaunchTask>, shouldConnectDebugger: Boolean): Int {
      var total = 0
      for (task in launchTasks) {
        total += task.duration
      }
      if (shouldConnectDebugger) {
        total += LaunchTaskDurations.CONNECT_DEBUGGER
      }
      return total
    }
  }
}