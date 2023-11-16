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
import com.android.tools.idea.model.TestExecutionOption
import com.android.tools.idea.run.configuration.execution.println
import com.android.tools.idea.run.configuration.execution.printlnError
import com.android.tools.idea.stats.UsageTrackerTestRunListener
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.allInModuleTest
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.allInPackageTest
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.classTest
import com.android.tools.idea.testartifacts.instrumented.AndroidTestApplicationLaunchTask.Companion.methodTest
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.DdmlibTestRunListenerAdapter
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.google.wireless.android.sdk.stats.TestLibraries
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.diagnostic.Logger

/**
 * Runs an instrumentation test asynchronously.
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
  private val myAndroidTestRunnerConfigurator: (RemoteAndroidTestRunner) -> Unit) {

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
      testExecutionOption: TestExecutionOption?): AndroidTestApplicationLaunchTask {
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


    private fun createTestListener(processHandler: ProcessHandler, testView: AndroidTestSuiteView, device: IDevice): ITestRunListener {
      return DdmlibTestRunListenerAdapter(device, testView).also {
        processHandler.addProcessListener(it)
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

  fun run(device: IDevice, testView: AndroidTestSuiteView, processHandler: ProcessHandler) {
    testView.println("Running tests")

    val runner = createRemoteAndroidTestRunner(device)

    testView.println("$ adb shell ${runner.amInstrumentCommand}")

    // Run "am instrument" command in a separate thread.
    try {
      processHandler.addProcessListener(object : ProcessAdapter() {
        override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
          runner.cancel()
        }
      })

      val testListener = arrayOf(
        createTestListener(processHandler, testView, device),
        createUsageTrackerTestRunListener(testLibrariesInUse, myExecutionOption, device)
      )

      // This issues "am instrument" command and blocks execution.
      runner.run(*testListener)

      // runner.cancel() may leave application keep running (b/170232723).
      device.forceStop(myTestApplicationId)
    }
    catch (e: Exception) {
      e.message?.let { testView.printlnError(it) }
      processHandler.detachProcess()
      LOG.warn(e)
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
}