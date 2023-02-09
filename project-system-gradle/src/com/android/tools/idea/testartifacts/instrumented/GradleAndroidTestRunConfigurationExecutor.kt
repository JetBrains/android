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
package com.android.tools.idea.testartifacts.instrumented

import com.android.ddmlib.IDevice
import com.android.tools.deployer.DeployerException
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.execution.common.AndroidExecutionException
import com.android.tools.idea.execution.common.ApplicationTerminator
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.createRunContentDescriptor
import com.android.tools.idea.run.configuration.execution.getDevices
import com.android.tools.idea.run.configuration.execution.println
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.ui.BaseAction
import com.android.tools.idea.stats.RunStats
import com.android.tools.idea.testartifacts.instrumented.orchestrator.MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.android.tools.idea.util.androidFacet
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.utils.keysToMap
import java.text.SimpleDateFormat
import java.util.Locale


class GradleAndroidTestRunConfigurationExecutor(
  private val env: ExecutionEnvironment,
  override val deviceFutures: DeviceFutures
) : AndroidConfigurationExecutor {

  override val configuration = env.runProfile as AndroidTestRunConfiguration
  private var applicationIdProvider = configuration.applicationIdProvider ?: throw RuntimeException(
    "Can't get ApplicationIdProvider for AndroidTestRunConfiguration")
  val module = configuration.configurationModule.module!!
  private val launchTasksProvider = GradleAndroidTestApplicationLaunchTasksProvider(env, module.androidFacet!!, applicationIdProvider)
  val project = env.project
  private val LOG = Logger.getInstance(this::class.java)

  /**
   * Returns a target Android process ID to be monitored by [AndroidProcessHandler].
   *
   * If this run is instrumentation test without test orchestration, the target Android process ID is simply the application name.
   * Otherwise, we should monitor the test orchestration process because the orchestrator starts and
   * kills the target application process per test case which confuses AndroidProcessHandler (b/150320657).
   */
  private fun getMasterAndroidProcessId(): String {
    return MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME.getOrDefault(
      configuration.getTestExecutionOption(AndroidFacet.getInstance(module)),
      applicationIdProvider.packageName)
  }

  override fun run(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable(indicator) {
    BaseAction.findRunningProcessHandler(project, configuration, env.executionTarget)?.destroyProcess()

    val devices = getDevices(deviceFutures, indicator, RunStats.from(env))

    waitPreviousProcessTermination(devices, getMasterAndroidProcessId(), indicator)

    val processId = getMasterAndroidProcessId()
    // AndroidProcessHandler should not be closed even if the target application process is killed. During an
    // instrumentation tests, the target application may be killed in between test cases by test runner. Only test
    // runner knows when all test run completes.
    val shouldAutoTerminate = false
    val processHandler = AndroidProcessHandler(project, processId, { it.forceStop(processId) }, shouldAutoTerminate)

    val console = createAndroidTestSuiteView()
    processHandler.putCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY, console)
    doRun(devices, processHandler, indicator, console)

    devices.forEach { device -> processHandler.addTargetDevice(device) }

    createRunContentDescriptor(processHandler, console, env)
  }

  private suspend fun doRun(devices: List<IDevice>,
                            processHandler: ProcessHandler,
                            indicator: ProgressIndicator,
                            console: AndroidTestSuiteView) = coroutineScope {
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
          project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingTest(device.serialNumber, project)
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
    BaseAction.findRunningProcessHandler(project, configuration, env.executionTarget)?.destroyProcess()

    val devices = getDevices(deviceFutures, indicator, RunStats.from(env))

    if (devices.size != 1) {
      throw ExecutionException("Cannot launch a debug session on more than 1 device.")
    }
    waitPreviousProcessTermination(devices, getMasterAndroidProcessId(), indicator)


    val processHandler = NopProcessHandler()
    val console = createAndroidTestSuiteView()
    processHandler.putCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY, console)
    doRun(devices, processHandler, indicator, console)

    val device = devices.single()
    val debuggerTask = launchTasksProvider.connectDebuggerTask
                       ?: throw RuntimeException(
                         "ConnectDebuggerTask is null for task provider " + launchTasksProvider.javaClass.name)
    indicator.text = "Connecting debugger"
    val session = debuggerTask.perform(device, applicationIdProvider.packageName, env, indicator, console)
    session.runContentDescriptor
  }

  private suspend fun createAndroidTestSuiteView() = withContext(AndroidDispatchers.uiThread) {
    AndroidTestSuiteView(project, project, configuration.configurationModule.androidTestModule, env.executor.toolWindowId, configuration)
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

  private fun printLaunchTaskStartedMessage(consoleView: AndroidTestSuiteView) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    consoleView.println("$dateFormat: Launching ${configuration.name} on '${env.executionTarget.displayName}.")
  }

  override fun applyChanges(indicator: ProgressIndicator) = throw UnsupportedOperationException(
    "Apply Changes are not supported for Instrumented tests")

  override fun applyCodeChanges(indicator: ProgressIndicator) = throw UnsupportedOperationException(
    "Apply Code Changes are not supported for Instrumented tests")
}