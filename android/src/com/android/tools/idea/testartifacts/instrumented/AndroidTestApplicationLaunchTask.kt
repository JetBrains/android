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
import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.run.AndroidProcessHandler
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.tasks.AppLaunchTask
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchResult
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTaskDurations
import com.android.tools.idea.stats.UsageTrackerTestRunListener
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.allInModuleTest
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.allInPackageTest
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.classTest
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.methodTest
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.DdmlibTestRunListenerAdapter
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.Future

/**
 * A [LaunchTask] which runs an instrumentation test asynchronously.
 *
 * Use one of [allInModuleTest], [allInPackageTest], [classTest], or [methodTest] to instantiate an object of this class.
 */
class AndroidTestApplicationLaunchTask(
  private val myInstrumentationTestRunner: String,
  private val myTestApplicationId: String,
  private val myArtifact: IdeAndroidArtifact?,
  private val myWaitForDebugger: Boolean,
  private val myInstrumentationOptions: String,
  private val myTestListeners: List<ITestRunListener>,
  private val myBackgroundTaskExecutor: (Runnable) -> Future<*> = ApplicationManager.getApplication()::executeOnPooledThread,
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
      artifact: IdeAndroidArtifact?,
      processHandler: ProcessHandler,
      consolePrinter: ConsolePrinter,
      device: IDevice): AndroidTestApplicationLaunchTask {
      return AndroidTestApplicationLaunchTask(
        instrumentationTestRunner,
        testApplicationId,
        artifact,
        waitForDebugger,
        instrumentationOptions,
        createRunListeners(processHandler, consolePrinter, device, artifact)) {}
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
      artifact: IdeAndroidArtifact?,
      processHandler: ProcessHandler,
      consolePrinter: ConsolePrinter,
      device: IDevice,
      packageName: String): AndroidTestApplicationLaunchTask {
      return AndroidTestApplicationLaunchTask(
        instrumentationTestRunner,
        testApplicationId,
        artifact,
        waitForDebugger,
        instrumentationOptions,
        createRunListeners(processHandler, consolePrinter, device, artifact)) { runner -> runner.setTestPackageName(packageName) }
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
      artifact: IdeAndroidArtifact?,
      processHandler: ProcessHandler,
      consolePrinter: ConsolePrinter,
      device: IDevice,
      testClassName: String): AndroidTestApplicationLaunchTask {
      return AndroidTestApplicationLaunchTask(
        instrumentationTestRunner,
        testApplicationId,
        artifact,
        waitForDebugger,
        instrumentationOptions,
        createRunListeners(processHandler, consolePrinter, device, artifact)) { runner -> runner.setClassName(testClassName) }
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
      artifact: IdeAndroidArtifact?,
      processHandler: ProcessHandler,
      consolePrinter: ConsolePrinter,
      device: IDevice,
      testClassName: String,
      testMethodName: String): AndroidTestApplicationLaunchTask {
      return AndroidTestApplicationLaunchTask(
        instrumentationTestRunner,
        testApplicationId,
        artifact,
        waitForDebugger,
        instrumentationOptions,
        createRunListeners(processHandler, consolePrinter, device, artifact)) { runner ->
          runner.setMethodName(testClassName, testMethodName)
        }
    }

    private fun createRunListeners(processHandler: ProcessHandler, printer: ConsolePrinter,
                                   device: IDevice, artifact: IdeAndroidArtifact?): List<ITestRunListener> {
      return listOf(
        createTestListener(processHandler, printer, device),
        createUsageTrackerTestRunListener(artifact, device)
      )
    }

    private fun createTestListener(processHandler: ProcessHandler, printer: ConsolePrinter, device: IDevice): ITestRunListener {
      // Use testsuite's AndroidTestResultListener if one is attached to the process handler, otherwise use the default one.
      val androidTestResultListener = processHandler.getCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY)
      return if (androidTestResultListener != null) {
        DdmlibTestRunListenerAdapter(device, androidTestResultListener)
      } else {
        AndroidTestListener(printer)
      }
    }

    private fun createUsageTrackerTestRunListener(artifact: IdeAndroidArtifact?, device: IDevice): ITestRunListener {
      return UsageTrackerTestRunListener(artifact, device)
    }
  }

  override fun run(launchContext: LaunchContext): LaunchResult? {
    val printer = launchContext.consolePrinter
    val device = launchContext.device
    val launchStatus = launchContext.launchStatus

    printer.stdout("Running tests\n")

    val runner = createRemoteAndroidTestRunner(device)

    printer.stdout("$ adb shell ${runner.amInstrumentCommand}")

    // Run "am instrument" command in a separate thread.
    val testExecutionFuture = myBackgroundTaskExecutor {
      try {
        var hasTestRunEndedReported = false
        val checkLaunchState = object: ITestRunListener {
          private fun checkStatusAndRequestCancel() {
            // Note: Should not use launchContext.processHandler. The process handler may
            // be replaced in later launch tasks. For instance ConnectJavaDebuggerTask.
            if (launchStatus.isLaunchTerminated ||
                launchStatus.processHandler.isProcessTerminating ||
                launchStatus.processHandler.isProcessTerminated) {
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

        // This issues "am instrument" command and blocks execution.
        runner.run(*myTestListeners.toTypedArray(), checkLaunchState)

        // Call testRunEnded() if it hasn't called yet. This may happen by several situations,
        // such as disconnecting a device during the test (b/170235394) and calling runner.cancel()
        // which stops parsing the test results immediately.
        if (!hasTestRunEndedReported) {
          myTestListeners.forEach {
            it.testRunEnded(0, mapOf())
          }
        }

        (launchStatus.processHandler as? AndroidProcessHandler)?.let { androidProcessHandler ->
          // runner.cancel() may leave application keep running (b/170232723).
          device.forceStop(androidProcessHandler.targetApplicationId)

          // Detach the device from the android process handler manually as soon as "am instrument" command finishes.
          androidProcessHandler.detachDevice(device)
          if (androidProcessHandler.isEmpty()) {
            androidProcessHandler.detachProcess()
          }
        }
      }
      catch (e: Exception) {
        LOG.info(e)
      }
    }
    // Add launch termination condition so that the launch is not considered as terminated until the test execution
    // is fully finished. This is required because test process on device might finish earlier than AndroidTestListener
    // is notified.
    launchStatus.addLaunchTerminationCondition(testExecutionFuture::isDone)

    return LaunchResult.success()
  }

  /**
   * Creates a [RemoteAndroidTestRunner] for a given [device].
   */
  fun createRemoteAndroidTestRunner(device: IDevice): RemoteAndroidTestRunner {
    return when (myArtifact?.testOptions?.execution) {
      IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR ->
        AndroidTestOrchestratorRemoteAndroidTestRunner(
          myTestApplicationId,
          myInstrumentationTestRunner,
          device,
          false
        )
      IdeTestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR ->
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