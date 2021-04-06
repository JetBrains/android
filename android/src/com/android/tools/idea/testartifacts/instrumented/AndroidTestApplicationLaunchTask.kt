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

/**
 * A [LaunchTask] which runs an instrumentation test asynchronously.
 *
 * Use one of [allInModuleTest], [allInPackageTest], [classTest], or [methodTest] to instantiate an object of this class.
 */
class AndroidTestApplicationLaunchTask private constructor(
  private val myInstrumentationTestRunner: String,
  private val myTestApplicationId: String,
  private val myArtifact: IdeAndroidArtifact?,
  private val myWaitForDebugger: Boolean,
  private val myInstrumentationOptions: String,
  private val myTestListener: ITestRunListener,
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
      createTestListener(processHandler, consolePrinter, device)) {}
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
        createTestListener(processHandler, consolePrinter, device)) { runner -> runner.setTestPackageName(packageName) }
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
        createTestListener(processHandler, consolePrinter, device)) { runner -> runner.setClassName(testClassName) }
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
        createTestListener(processHandler, consolePrinter, device)) { runner -> runner.setMethodName(testClassName, testMethodName) }
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
  }

  override fun run(launchContext: LaunchContext): LaunchResult? {
    val printer = launchContext.consolePrinter
    val device = launchContext.device
    val launchStatus = launchContext.launchStatus

    printer.stdout("Running tests\n")

    val runner = createRemoteAndroidTestRunner(device)

    printer.stdout("$ adb shell ${runner.amInstrumentCommand}")

    // Run "am instrument" command in a separate thread.
    val testExecutionFuture = ApplicationManager.getApplication().executeOnPooledThread {
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
        val listeners = arrayOf(myTestListener, UsageTrackerTestRunListener(myArtifact, device))
        runner.run(*listeners, checkLaunchState)

        // Call testRunEnded() if it hasn't called yet. This may happen by several situations,
        // such as disconnecting a device during the test (b/170235394) and calling runner.cancel()
        // which stops parsing the test results immediately.
        if (!hasTestRunEndedReported) {
          listeners.forEach {
            it.testRunEnded(0, mapOf())
          }
        }

        (launchStatus.processHandler as? AndroidProcessHandler)?.let { androidProcessHandler ->
          // runner.cancel() may leave application keep running (b/170232723).
          device.forceStop(androidProcessHandler.targetApplicationId)

          // Detach the device from the android process handler manually as soon as "am instrument" command finishes.
          // This is required because the android process handler may overlook target process especially when the test
          // runs really fast (~10ms). Because the android process handler discovers new processes by polling, this
          // race condition happens easily. By detaching the device manually, we can avoid the android process handler
          // waiting for (already finished) process to show up until it times out (10 secs).
          androidProcessHandler.detachDevice(device)
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