/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.ddmlib.testrunner.AndroidTestOrchestratorRemoteAndroidTestRunner
import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner.StatusReporterMode
import com.android.ddmlib.testrunner.TestIdentifier
import com.android.tools.idea.execution.common.RunConfigurationNotifier
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.model.TestExecutionOption
import com.android.tools.idea.run.configuration.execution.println
import com.android.tools.idea.run.tasks.AppLaunchTask
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTaskDurations
import com.android.tools.idea.stats.UsageTrackerTestRunListener
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.allInModuleTest
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.allInPackageTest
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.classTest
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.methodTest
import com.android.tools.idea.testartifacts.instrumented.configuration.AndroidTestConfiguration
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.DdmlibTestRunListenerAdapter
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.google.wireless.android.sdk.stats.TestLibraries
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.Future

/**
 * A [LaunchTask] which runs an instrumentation test asynchronously.
 *
 * Use one of [allInModuleTest], [allInPackageTest], [classTest], or [methodTest] to instantiate an object of this class.
 */
class AndroidTestApplicationLaunchTask(
  private val testLibrariesInUse: TestLibraries?,
  private val myInstrumentationTestRunner: String,
  private val myTestApplicationId: String,
  private val myExecutionOption: TestExecutionOption?,
  private val myWaitForDebugger: Boolean,
  private val myInstrumentationOptions: String,
  private val myBackgroundTaskExecutor: (Runnable) -> Future<*> = ApplicationManager.getApplication()::executeOnPooledThread,
  private val myAndroidTestConfigurationProvider: () -> AndroidTestConfiguration = { AndroidTestConfiguration.getInstance() },
  private val myAndroidTestRunnerConfigurator: (RemoteAndroidTestRunner) -> Unit) : AppLaunchTask() {

  companion object {
    private val LOG = Logger.getInstance(AndroidTestApplicationLaunchTask::class.java)

    /**
     * Creates [AndroidTestApplicationLaunchTask] for all-in-module test.
     */
    @JvmStatic
    fun allInModuleTest(
      instrumentationTestRunner: String,
      testApplicationId: String,
      waitForDebugger: Boolean,
      instrumentationOptions: String,
      testLibrariesInUse: TestLibraries?,
      testExecutionOption: TestExecutionOption?,
      device: IDevice): AndroidTestApplicationLaunchTask {
      return AndroidTestApplicationLaunchTask(
        testLibrariesInUse,
        instrumentationTestRunner,
        testApplicationId,
        testExecutionOption,
        waitForDebugger,
        instrumentationOptions,
      ) {}
    }

    /**
     * Creates [AndroidTestApplicationLaunchTask] for all-in-package test.
     */
    @JvmStatic
    fun allInPackageTest(
      instrumentationTestRunner: String,
      testApplicationId: String,
      waitForDebugger: Boolean,
      instrumentationOptions: String,
      testLibrariesInUse: TestLibraries?,
      testExecutionOption: TestExecutionOption?,
      device: IDevice,
      packageName: String): AndroidTestApplicationLaunchTask {
      return AndroidTestApplicationLaunchTask(
        testLibrariesInUse,
        instrumentationTestRunner,
        testApplicationId,
        testExecutionOption,
        waitForDebugger,
        instrumentationOptions) { runner -> runner.setTestPackageName(packageName) }
    }

    /**
     * Creates [AndroidTestApplicationLaunchTask] for a single class test.
     */
    @JvmStatic
    fun classTest(
      instrumentationTestRunner: String,
      testApplicationId: String,
      waitForDebugger: Boolean,
      instrumentationOptions: String,
      testLibrariesInUse: TestLibraries?,
      testExecutionOption: TestExecutionOption?,
      device: IDevice,
      testClassName: String): AndroidTestApplicationLaunchTask {
      return AndroidTestApplicationLaunchTask(
        testLibrariesInUse,
        instrumentationTestRunner,
        testApplicationId,
        testExecutionOption,
        waitForDebugger,
        instrumentationOptions) { runner -> runner.setClassName(testClassName) }
    }

    /**
     * Creates [AndroidTestApplicationLaunchTask] for a single method test.
     */
    @JvmStatic
    fun methodTest(
      instrumentationTestRunner: String,
      testApplicationId: String,
      waitForDebugger: Boolean,
      instrumentationOptions: String,
      testLibrariesInUse: TestLibraries?,
      testExecutionOption: TestExecutionOption?,
      device: IDevice,
      testClassName: String,
      testMethodName: String): AndroidTestApplicationLaunchTask {
      return AndroidTestApplicationLaunchTask(
        testLibrariesInUse,
        instrumentationTestRunner,
        testApplicationId,
        testExecutionOption,
        waitForDebugger,
        instrumentationOptions) { runner ->
        runner.setMethodName(testClassName, testMethodName)
      }
    }

    private fun createRunListeners(
      processHandler: ProcessHandler,
      printer: ConsoleView,
      device: IDevice,
      testLibrariesInUse: TestLibraries?,
      testExecutionOption: TestExecutionOption?
    ): List<ITestRunListener> {
      return listOf(
        createTestListener(processHandler, printer, device),
        createUsageTrackerTestRunListener(testLibrariesInUse, testExecutionOption, device)
      )
    }

    private fun createTestListener(processHandler: ProcessHandler, printer: ConsoleView, device: IDevice): ITestRunListener {
      // Use testsuite's AndroidTestResultListener if one is attached to the process handler, otherwise use the default one.
      val androidTestResultListener = processHandler.getCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY)
      return if (androidTestResultListener != null) {
        DdmlibTestRunListenerAdapter(device, androidTestResultListener).also {
          processHandler.addProcessListener(it)
        }
      }
      else {
        AndroidTestListener(printer)
      }
    }

    private fun createUsageTrackerTestRunListener(
      testLibrariesInUse: TestLibraries?,
      testExecutionOption: TestExecutionOption?,
      device: IDevice
    ): ITestRunListener {
      return UsageTrackerTestRunListener(testLibrariesInUse, testExecutionOption, device)
    }
  }

  override fun run(launchContext: LaunchContext) {
    val console = launchContext.consoleView
    val device = launchContext.device

    if (myAndroidTestConfigurationProvider().RUN_ANDROID_TEST_USING_GRADLE) {
      RunConfigurationNotifier.notifyWarning(launchContext.env.project,
                                             launchContext.env.runProfile.name,
                                             "\"Run Android instrumented tests using Gradle\" option was ignored because this module type is not supported yet.")
    }

    console.println("Running tests")

    val runner = createRemoteAndroidTestRunner(device)

    console.println("$ adb shell ${runner.amInstrumentCommand}")

    // Run "am instrument" command in a separate thread.
    myBackgroundTaskExecutor {
      try {
        var hasTestRunEndedReported = false
        val checkLaunchState = object : ITestRunListener {
          private fun checkStatusAndRequestCancel() {
            // Note: Should not use launchContext.processHandler. The process handler may
            // be replaced in later launch tasks. For instance ConnectJavaDebuggerTask.
            if (!launchContext.progressIndicator.isCanceled || launchContext.processHandler.isProcessTerminated) {
              runner.cancel()
            }
          }
          override fun testRunStarted(runName: String?, testCount: Int) = checkStatusAndRequestCancel()
          override fun testStarted(test: TestIdentifier?) = checkStatusAndRequestCancel()
          override fun testFailed(test: TestIdentifier?, trace: String?) {}
          override fun testAssumptionFailure(test: TestIdentifier?, trace: String?) {}
          override fun testIgnored(test: TestIdentifier?) {}
          override fun testEnded(test: TestIdentifier?, testMetrics: MutableMap<String, String>?) = checkStatusAndRequestCancel()
          override fun testRunFailed(errorMessage: String?) {}
          override fun testRunStopped(elapsedTime: Long) {}
          override fun testRunEnded(elapsedTime: Long, runMetrics: MutableMap<String, String>?) {
            hasTestRunEndedReported = true
          }
        }

        val testListener = createRunListeners(launchContext.processHandler, launchContext.consoleView, device, testLibrariesInUse,
                                              myExecutionOption)

        // This issues "am instrument" command and blocks execution.
        runner.run(*testListener.toTypedArray(), checkLaunchState)

        // Call testRunEnded() if it hasn't called yet. This may happen by several situations,
        // such as disconnecting a device during the test (b/170235394) and calling runner.cancel()
        // which stops parsing the test results immediately.
        if (!hasTestRunEndedReported) {
          testListener.forEach {
            it.testRunEnded(0, mapOf())
          }
        }

        (launchContext.processHandler as? AndroidProcessHandler)?.let { androidProcessHandler ->
          // runner.cancel() may leave application keep running (b/170232723).
          device.forceStop(myTestApplicationId)

          // Detach the device from the android process handler manually as soon as "am instrument" command finishes.
          androidProcessHandler.detachDevice(device)
        }
      }
      catch (e: Exception) {
        launchContext.processHandler.detachProcess()
        LOG.warn(e)
      }
    }
  }

  /**
   * Creates a [RemoteAndroidTestRunner] for a given [device].
   */
  fun createRemoteAndroidTestRunner(device: IDevice): RemoteAndroidTestRunner {
    return when (myExecutionOption) {
      TestExecutionOption.ANDROID_TEST_ORCHESTRATOR ->
        AndroidTestOrchestratorRemoteAndroidTestRunner(
          myTestApplicationId,
          myInstrumentationTestRunner,
          device,
          false
        )
      TestExecutionOption.ANDROIDX_TEST_ORCHESTRATOR ->
        AndroidTestOrchestratorRemoteAndroidTestRunner(
          myTestApplicationId,
          myInstrumentationTestRunner,
          device,
          true
        )
      else -> {
        val statusReporterMode =
          if (device.version.apiLevel >= StatusReporterMode.PROTO_STD.minimumApiLevel) {
            StatusReporterMode.PROTO_STD
          }
          else {
            StatusReporterMode.RAW_TEXT
          }
        RemoteAndroidTestRunner(myTestApplicationId,
                                myInstrumentationTestRunner,
                                device,
                                statusReporterMode)
      }
    }.apply {
      setDebug(myWaitForDebugger)
      runOptions = myInstrumentationOptions
      myAndroidTestRunnerConfigurator(this)
    }
  }

  override fun getId(): String = "INSTRUMENTATION_RUNNER"
  override fun getDescription(): String = "Launching instrumentation runner"
  override fun getDuration(): Int = LaunchTaskDurations.LAUNCH_ACTIVITY
}