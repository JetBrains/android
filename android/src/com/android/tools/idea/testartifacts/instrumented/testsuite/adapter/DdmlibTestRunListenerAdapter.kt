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
package com.android.tools.idea.testartifacts.instrumented.testsuite.adapter

import com.android.annotations.concurrency.AnyThread
import com.android.ddmlib.IDevice
import com.android.ddmlib.testrunner.IInstrumentationResultParser.StatusKeys.DDMLIB_LOGCAT
import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.TestIdentifier
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark.BenchmarkOutput
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.util.ClassUtil
import java.io.File

/**
 * An adapter to translate [ITestRunListener] and [ProcessListener] callback methods into [AndroidTestResultListener].
 */
class DdmlibTestRunListenerAdapter(private val myIDevice: IDevice,
                                   private val listener: AndroidTestResultListener) : ITestRunListener, ProcessListener {

  companion object {
    private val logger = Logger.getInstance(DdmlibTestRunListenerAdapter::class.java)
    const val BENCHMARK_TEST_METRICS_KEY = "android.studio.display.benchmark"
    const val BENCHMARK_V2_TEST_METRICS_KEY = "android.studio.v2display.benchmark"
    const val BENCHMARK_PATH_TEST_METRICS_KEY = "android.studio.v2display.benchmark.outputDirPath"
    private val benchmarkPrefixRegex = "^benchmark:( )?".toRegex(RegexOption.MULTILINE)

    /**
     * Retrieves benchmark output text from a given [testMetrics].
     */
    private fun getBenchmarkOutput(testMetrics: MutableMap<String, String>): String {
      // Workaround solution for b/154322086.
      // Newer libraries output strings on both BENCHMARK_TEST_METRICS_KEY and BENCHMARK_V2_OUTPUT_TEST_METRICS_KEY.
      // The V2 supports linking while the V1 does not. This is done to maintain backward compatibility with older versions of studio.
      var key = BENCHMARK_TEST_METRICS_KEY
      if (testMetrics.containsKey(BENCHMARK_V2_TEST_METRICS_KEY)) {
        key = BENCHMARK_V2_TEST_METRICS_KEY
      }
      return benchmarkPrefixRegex.replace(testMetrics.getOrDefault(key, ""), "")
    }
  }

  private val myDevice = convertIDeviceToAndroidDevice(myIDevice)

  private lateinit var myTestSuite: AndroidTestSuite
  private val myTestCases = mutableMapOf<TestIdentifier, AndroidTestCase>()

  // This map keeps track of number of rerun of the same test method.
  // This value is used to create a unique identifier for each test case
  // yet to be able to group them together across multiple devices.
  private val myTestCaseRunCount: MutableMap<String, Int> = mutableMapOf()

  init {
    listener.onTestSuiteScheduled(myDevice)
  }

  @Synchronized
  @AnyThread
  override fun testRunStarted(runName: String, testCount: Int) {
    myTestSuite = AndroidTestSuite(runName, runName, testCount)
    listener.onTestSuiteStarted(myDevice, myTestSuite)
  }

  @Synchronized
  @AnyThread
  override fun testStarted(testId: TestIdentifier) {
    val fullyQualifiedTestMethodName = "${testId.className}#${testId.testName}"
    val testCaseRunCount = myTestCaseRunCount.compute(fullyQualifiedTestMethodName) { _, currentValue ->
      currentValue?.plus(1) ?: 0
    }
    val testCase = AndroidTestCase("${testId} - ${testCaseRunCount}",
                                   testId.testName,
                                   ClassUtil.extractClassName(testId.className),
                                   ClassUtil.extractPackageName(testId.className),
                                   AndroidTestCaseResult.IN_PROGRESS,
                                   startTimestampMillis = System.currentTimeMillis())
    myTestCases[testId] = testCase
    listener.onTestCaseStarted(myDevice, myTestSuite, testCase)
  }

  @Synchronized
  @AnyThread
  override fun testFailed(testId: TestIdentifier, trace: String) {
    val testCase = myTestCases.getValue(testId)
    testCase.result = AndroidTestCaseResult.FAILED
    testCase.errorStackTrace = trace
    myTestSuite.result = AndroidTestSuiteResult.FAILED
  }

  @Synchronized
  @AnyThread
  override fun testAssumptionFailure(testId: TestIdentifier, trace: String) {
    val testCase = myTestCases.getValue(testId)
    testCase.result = AndroidTestCaseResult.SKIPPED
    testCase.errorStackTrace = trace
  }

  @Synchronized
  @AnyThread
  override fun testIgnored(testId: TestIdentifier) {
    val testCase = myTestCases.getValue(testId)
    testCase.result = AndroidTestCaseResult.SKIPPED
  }

  @Synchronized
  @AnyThread
  override fun testEnded(testId: TestIdentifier, testMetrics: MutableMap<String, String>) {
    val testCase = myTestCases.getValue(testId)
    if (!testCase.result.isTerminalState) {
      testCase.result = AndroidTestCaseResult.PASSED
    }
    testCase.logcat = testMetrics.getOrDefault(DDMLIB_LOGCAT, "")
    testCase.benchmark = getBenchmarkOutput(testMetrics)
    testCase.endTimestampMillis = System.currentTimeMillis()
    copyBenchmarkFilesIfNeeded(testCase.benchmark, testMetrics.getOrDefault(BENCHMARK_PATH_TEST_METRICS_KEY, ""))
    listener.onTestCaseFinished(myDevice, myTestSuite, testCase)
  }

  @Synchronized
  @AnyThread
  override fun testRunFailed(errorMessage: String) {
    myTestSuite.result = AndroidTestSuiteResult.ABORTED
  }

  @Synchronized
  @AnyThread
  override fun testRunStopped(elapsedTime: Long) {
    myTestSuite.result = AndroidTestSuiteResult.CANCELLED
  }

  @Synchronized
  @AnyThread
  override fun testRunEnded(elapsedTime: Long, runMetrics: MutableMap<String, String>) {
    // Ddmlib calls testRunEnded() callback if the target app process has crashed or
    // killed manually. (For example, if you click "stop" run button from Android Studio,
    // it kills the app process. Thus, we update test results to cancelled for all
    // pending tests.)
    if (!this::myTestSuite.isInitialized) {
      myTestSuite = AndroidTestSuite("", "", 0, AndroidTestSuiteResult.CANCELLED)
    }

    for (testCase in myTestCases.values) {
      if (!testCase.result.isTerminalState) {
        testCase.result = AndroidTestCaseResult.CANCELLED
        testCase.endTimestampMillis = System.currentTimeMillis()
        myTestSuite.result = myTestSuite.result ?: AndroidTestSuiteResult.CANCELLED
      }
    }

    myTestSuite.result = myTestSuite.result ?: AndroidTestSuiteResult.PASSED
    listener.onTestSuiteFinished(myDevice, myTestSuite)
  }

  private fun copyBenchmarkFilesIfNeeded(benchmark: String, deviceRoot: String) {
    if (benchmark.isBlank() || deviceRoot.isBlank()) {
      return;
    }
    val benchmarkOutput = BenchmarkOutput(benchmark)
    for (line in benchmarkOutput.lines) {
      var match = line.matches
      while (match != null) {
        val link = match.groups[BenchmarkOutput.LINK_GROUP]?.value ?: ""
        if (link.startsWith(BenchmarkOutput.BENCHMARK_TRACE_FILE_PREFIX)) {
          val task = object : Task.Backgroundable(null, "Pulling: $link", true) {
            override fun run(indicator: ProgressIndicator) {
              val relativeFilePath = link.replace(BenchmarkOutput.BENCHMARK_TRACE_FILE_PREFIX, "")
              val localFilePath = FileUtil.getTempDirectory() + File.separator + relativeFilePath
              val localFile = File(localFilePath)
              localFile.deleteOnExit()
              if (!localFile.exists() && (localFile.parentFile.exists() || localFile.parentFile.mkdirs())) {
                myIDevice.pullFile("$deviceRoot/$relativeFilePath", localFile.absolutePath)
              } else {
                logger.warn("Unable to copy latest trace file ($relativeFilePath) from device (${myIDevice.serialNumber})")
              }
            }
          }
          ProgressManager.getInstance().run(task)
        }
        match = match.next()
      }
    }
  }

  @Synchronized
  @AnyThread
  override fun startNotified(event: ProcessEvent) {}

  @Synchronized
  @AnyThread
  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {}

  @Synchronized
  @AnyThread
  override fun processTerminated(event: ProcessEvent) {
    event.processHandler.removeProcessListener(this)
    testRunEnded(0, mutableMapOf())
  }
}