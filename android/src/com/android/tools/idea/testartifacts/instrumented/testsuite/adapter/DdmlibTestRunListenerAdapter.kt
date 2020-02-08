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

import com.android.ddmlib.IDevice
import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.TestIdentifier
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult

/**
 * An adapter to translate [ITestRunListener] callback methods into [AndroidTestResultListener].
 */
class DdmlibTestRunListenerAdapter(device: IDevice,
                                   private val listener: AndroidTestResultListener) : ITestRunListener {

  private val myDevice = AndroidDevice(device.serialNumber,
                                       device.avdName ?: device.serialNumber)
  private lateinit var myTestSuite: AndroidTestSuite
  private val myTestCases = mutableMapOf<TestIdentifier, AndroidTestCase>()

  init {
    listener.onTestSuiteScheduled(myDevice)
  }

  override fun testRunStarted(runName: String, testCount: Int) {
    myTestSuite = AndroidTestSuite(runName, runName, testCount)
    listener.onTestSuiteStarted(myDevice, myTestSuite)
  }

  override fun testStarted(testId: TestIdentifier) {
    val testCase = AndroidTestCase(testId.toString(), testId.toString())
    myTestCases[testId] = testCase
    listener.onTestCaseStarted(myDevice, myTestSuite, testCase)
  }

  override fun testFailed(testId: TestIdentifier, trace: String) {
    val testCase = myTestCases.getValue(testId)
    testCase.result = AndroidTestCaseResult.FAILED
    myTestSuite.result = AndroidTestSuiteResult.FAILED
  }

  override fun testAssumptionFailure(testId: TestIdentifier, trace: String) {
    val testCase = myTestCases.getValue(testId)
    testCase.result = AndroidTestCaseResult.FAILED
    myTestSuite.result = AndroidTestSuiteResult.FAILED
  }

  override fun testIgnored(testId: TestIdentifier) {
    val testCase = myTestCases.getValue(testId)
    testCase.result = AndroidTestCaseResult.SKIPPED
  }

  override fun testEnded(testId: TestIdentifier, testMetrics: MutableMap<String, String>) {
    val testCase = myTestCases.getValue(testId)
    testCase.result = testCase.result ?: AndroidTestCaseResult.PASSED
    listener.onTestCaseFinished(myDevice, myTestSuite, testCase)
  }

  override fun testRunFailed(errorMessage: String) {
    myTestSuite.result = AndroidTestSuiteResult.ABORTED
  }

  override fun testRunStopped(elapsedTime: Long) {
    myTestSuite.result = AndroidTestSuiteResult.CANCELLED
  }

  override fun testRunEnded(elapsedTime: Long, runMetrics: MutableMap<String, String>) {
    myTestSuite.result = myTestSuite.result ?: AndroidTestSuiteResult.PASSED
    listener.onTestSuiteFinished(myDevice, myTestSuite)
  }
}