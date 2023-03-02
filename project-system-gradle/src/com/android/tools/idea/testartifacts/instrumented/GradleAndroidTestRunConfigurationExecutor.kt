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
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.execution.common.ApplicationTerminator
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.createRunContentDescriptor
import com.android.tools.idea.run.configuration.execution.getDevices
import com.android.tools.idea.run.configuration.execution.println
import com.android.tools.idea.run.tasks.getBaseDebuggerTask
import com.android.tools.idea.run.ui.BaseAction
import com.android.tools.idea.stats.RunStats
import com.android.tools.idea.testartifacts.instrumented.orchestrator.MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.android.tools.idea.util.androidFacet
import com.intellij.execution.ExecutionException
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
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


open class GradleAndroidTestRunConfigurationExecutor(
  private val env: ExecutionEnvironment,
  override val deviceFutures: DeviceFutures
) : AndroidConfigurationExecutor {

  override val configuration = env.runProfile as AndroidTestRunConfiguration
  private var applicationIdProvider = configuration.applicationIdProvider ?: throw RuntimeException(
    "Can't get ApplicationIdProvider for AndroidTestRunConfiguration")
  private val packageName = try {
    applicationIdProvider.packageName
  }
  catch (e: ApkProvisionException) {
    throw ExecutionException("Can't get application ID")
  }
  val module = configuration.configurationModule.module!!
  val facet = module.androidFacet ?: throw RuntimeException("GradleAndroidTestRunConfigurationExecutor shouldn't be invoked for module without facet")
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
      packageName
    )
  }

  override fun run(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable(indicator) {
    LOG.info("Start run tests")
    BaseAction.findRunningProcessHandler(project, configuration, env.executionTarget)?.destroyProcess()

    val devices = getDevices(deviceFutures, indicator, RunStats.from(env))

    waitPreviousProcessTermination(devices, getMasterAndroidProcessId(), indicator)

    val console = createAndroidTestSuiteView()
    doRun(devices, indicator, console, false)


    val processId = getMasterAndroidProcessId()
    // AndroidProcessHandler should not be closed even if the target application process is killed. During an
    // instrumentation tests, the target application may be killed in between test cases by test runner. Only test
    // runner knows when all test run completes.
    val shouldAutoTerminate = false
    val processHandler = AndroidProcessHandler(project, processId, autoTerminate = shouldAutoTerminate)
    devices.forEach { device -> processHandler.addTargetDevice(device) }

    createRunContentDescriptor(processHandler, console, env)
  }

  private suspend fun doRun(devices: List<IDevice>,
                            indicator: ProgressIndicator,
                            console: AndroidTestSuiteView,
                            isDebug: Boolean) = coroutineScope {
    RunStats.from(env).apply { setPackage(packageName) }
    try {
      printLaunchTaskStartedMessage(console)
      indicator.text = "Start gradle task"
      doRunGradleTask(devices, console, isDebug)
    }
    finally {
      devices.forEach {
        // Notify listeners of the deployment.
        project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingTest(it.serialNumber, project)
      }
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
    LOG.info("Start debug tests")
    BaseAction.findRunningProcessHandler(project, configuration, env.executionTarget)?.destroyProcess()

    val devices = getDevices(deviceFutures, indicator, RunStats.from(env))

    if (devices.size != 1) {
      throw ExecutionException("Cannot launch a debug session on more than 1 device.")
    }

    waitPreviousProcessTermination(devices, getMasterAndroidProcessId(), indicator)

    val console = createAndroidTestSuiteView()
    doRun(devices, indicator, console, true)

    val device = devices.single()
    val debuggerTask = getBaseDebuggerTask(configuration.androidDebuggerContext, facet, env, timeoutSeconds = Int.MAX_VALUE)
    indicator.text = "Connecting debugger"
    val session = debuggerTask.perform(device, applicationIdProvider.packageName, env, indicator, console)
    session.runContentDescriptor
  }

  private suspend fun createAndroidTestSuiteView() = withContext(AndroidDispatchers.uiThread) {
    AndroidTestSuiteView(project, project, configuration.configurationModule.androidTestModule, env.executor.toolWindowId, configuration)
  }

  private fun printLaunchTaskStartedMessage(consoleView: AndroidTestSuiteView) {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
    consoleView.println("$date: Launching ${configuration.name} on '${env.executionTarget.displayName}.")
  }

  fun doRunGradleTask(devices: List<IDevice>, androidTestSuiteView: AndroidTestSuiteView, isDebug: Boolean) {
    val androidModuleModel = requireNotNull(GradleAndroidModel.get(facet))
    val retentionConfiguration = RetentionConfiguration(
      configuration.RETENTION_ENABLED,
      configuration.RETENTION_MAX_SNAPSHOTS,
      configuration.RETENTION_COMPRESS_SNAPSHOTS
    )
    val extraInstrumentationOptions = configuration.getExtraInstrumentationOptions(facet)

    val gradleConnectedAndroidTestInvoker = gradleConnectedAndroidTestInvoker()

    when (configuration.TESTING_TYPE) {
      AndroidTestRunConfiguration.TEST_ALL_IN_MODULE -> {
        gradleConnectedAndroidTestInvoker.runGradleTask(
          project, devices, packageName, androidTestSuiteView, androidModuleModel, isDebug,
          "", "", "", configuration.TEST_NAME_REGEX, retentionConfiguration,
          extraInstrumentationOptions)
      }

      AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE -> {
        gradleConnectedAndroidTestInvoker.runGradleTask(
          project, devices, packageName, androidTestSuiteView, androidModuleModel, isDebug,
          configuration.PACKAGE_NAME, "", "", "", retentionConfiguration,
          extraInstrumentationOptions)
      }

      AndroidTestRunConfiguration.TEST_CLASS -> {
        gradleConnectedAndroidTestInvoker.runGradleTask(
          project, devices, packageName, androidTestSuiteView, androidModuleModel, isDebug,
          "", configuration.CLASS_NAME, "", "", retentionConfiguration,
          extraInstrumentationOptions)
      }

      AndroidTestRunConfiguration.TEST_METHOD -> {
        gradleConnectedAndroidTestInvoker.runGradleTask(
          project, devices, packageName, androidTestSuiteView, androidModuleModel, isDebug,
          "", configuration.CLASS_NAME, configuration.METHOD_NAME, "", retentionConfiguration,
          extraInstrumentationOptions)
      }

      else -> {
        throw RuntimeException("Unknown testing type is selected, testing type is ${configuration.TESTING_TYPE}")
      }
    }
  }

  @VisibleForTesting
  protected open fun gradleConnectedAndroidTestInvoker() =
    GradleConnectedAndroidTestInvoker(env, requireNotNull(GradleUtil.findGradleModuleData(module)?.data))

  override fun applyChanges(indicator: ProgressIndicator) = throw UnsupportedOperationException(
    "Apply Changes are not supported for Instrumented tests")

  override fun applyCodeChanges(indicator: ProgressIndicator) = throw UnsupportedOperationException(
    "Apply Code Changes are not supported for Instrumented tests")
}