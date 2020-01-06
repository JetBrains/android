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

import com.android.builder.model.TestOptions
import com.android.ddmlib.IDevice
import com.android.ddmlib.testrunner.AndroidTestOrchestratorRemoteAndroidTestRunner
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner
import com.android.ide.common.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.run.AndroidProcessHandler
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.tasks.LaunchResult
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTaskDurations
import com.android.tools.idea.run.util.LaunchStatus
import com.android.tools.idea.stats.UsageTrackerTestRunListener
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.allInModuleTest
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.allInPackageTest
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.classTest
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.methodTest
import com.android.tools.idea.testartifacts.instrumented.testsuite.ANDROID_TEST_RESULT_LISTENER_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.DdmlibTestRunListenerAdapter
import com.intellij.execution.Executor
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
  private val myAndroidTestRunnerConfigurator: (RemoteAndroidTestRunner) -> Unit) : LaunchTask {

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
      artifact: IdeAndroidArtifact?): AndroidTestApplicationLaunchTask {
      return AndroidTestApplicationLaunchTask(
        instrumentationTestRunner,
        testApplicationId,
        artifact,
        waitForDebugger,
        instrumentationOptions) {}
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
      packageName: String): AndroidTestApplicationLaunchTask {
      return AndroidTestApplicationLaunchTask(
        instrumentationTestRunner,
        testApplicationId,
        artifact,
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
      artifact: IdeAndroidArtifact?,
      testClassName: String): AndroidTestApplicationLaunchTask {
      return AndroidTestApplicationLaunchTask(
        instrumentationTestRunner,
        testApplicationId,
        artifact,
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
      artifact: IdeAndroidArtifact?,
      testClassName: String,
      testMethodName: String): AndroidTestApplicationLaunchTask {
      return AndroidTestApplicationLaunchTask(
        instrumentationTestRunner,
        testApplicationId,
        artifact,
        waitForDebugger,
        instrumentationOptions) { runner -> runner.setMethodName(testClassName, testMethodName) }
    }
  }

  override fun run(executor: Executor, device: IDevice, launchStatus: LaunchStatus, printer: ConsolePrinter): LaunchResult {
    printer.stdout("Running tests\n")

    val runner = createRemoteAndroidTestRunner(device)

    printer.stdout("$ adb shell ${runner.amInstrumentCommand}")

    // Run "am instrument" command in a separate thread.
    val testExecutionFuture = ApplicationManager.getApplication().executeOnPooledThread {
      try {
        // Use testsuite's AndroidTestResultListener if one is attached to the process handler, otherwise use the default one.
        val androidTestResultListener = launchStatus.processHandler.getCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY)
        val ddmlibTestRunListener = if (androidTestResultListener != null) {
          DdmlibTestRunListenerAdapter(device, androidTestResultListener)
        } else {
          AndroidTestListener(printer)
        }

        // This issues "am instrument" command and blocks execution.
        runner.run(ddmlibTestRunListener, UsageTrackerTestRunListener(myArtifact, device))

        // Detach the device from the android process handler manually as soon as "am instrument" command finishes.
        // This is required because the android process handler may overlook target process especially when the test
        // runs really fast (~10ms). Because the android process handler discovers new processes by polling, this
        // race condition happens easily. By detaching the device manually, we can avoid the android process handler
        // waiting for (already finished) process to show up until it times out (10 secs).
        val androidProcessHandler = launchStatus.processHandler as? AndroidProcessHandler
        androidProcessHandler?.detachDevice(device)
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
      TestOptions.Execution.ANDROID_TEST_ORCHESTRATOR -> AndroidTestOrchestratorRemoteAndroidTestRunner(myTestApplicationId,
                                                                                                        myInstrumentationTestRunner,
                                                                                                        device,
                                                                                                        false)
      TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR -> AndroidTestOrchestratorRemoteAndroidTestRunner(myTestApplicationId,
                                                                                                         myInstrumentationTestRunner,
                                                                                                         device,
                                                                                                         true)
      else -> RemoteAndroidTestRunner(
        myTestApplicationId, myInstrumentationTestRunner, device, RemoteAndroidTestRunner.StatusReporterMode.PROTO_STD)
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