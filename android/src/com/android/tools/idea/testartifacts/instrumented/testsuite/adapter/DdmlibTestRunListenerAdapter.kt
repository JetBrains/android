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
import com.android.tools.idea.testartifacts.instrumented.testsuite.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.TestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.TestSuiteResult

/**
 * An adapter to translate [ITestRunListener] callback methods into [AndroidTestResultListener].
 */
class DdmlibTestRunListenerAdapter(val device: IDevice,
                                   private val listener: AndroidTestResultListener) : ITestRunListener {

  private val deviceId = device.serialNumber
  private lateinit var testSuiteId: String
  private var testSuiteResult = TestSuiteResult.PASSED
  private val testCaseResults = mutableMapOf<TestIdentifier, TestCaseResult>()

  init {
    listener.onTestSuiteScheduled(deviceId)
  }

  override fun testRunStarted(runName: String, testCount: Int) {
    testSuiteId = runName
    listener.onTestSuiteStarted(deviceId, testSuiteId, testCount)
  }

  override fun testStarted(testId: TestIdentifier) {
    testCaseResults[testId] = TestCaseResult.PASSED
    listener.onTestCaseStarted(deviceId, testSuiteId, testId.toString())
  }

  override fun testFailed(testId: TestIdentifier, trace: String) {
    testCaseResults[testId] = TestCaseResult.FAILED
    testSuiteResult = TestSuiteResult.FAILED
  }

  override fun testAssumptionFailure(testId: TestIdentifier, trace: String) {
    testCaseResults[testId] = TestCaseResult.FAILED
    testSuiteResult = TestSuiteResult.FAILED
  }

  override fun testIgnored(testId: TestIdentifier) {
    testCaseResults[testId] = TestCaseResult.SKIPPED
  }

  override fun testEnded(testId: TestIdentifier, testMetrics: MutableMap<String, String>) {
    listener.onTestCaseFinished(deviceId, testSuiteId, testId.toString(), testCaseResults.getValue(testId))
  }

  override fun testRunFailed(errorMessage: String) {
    testSuiteResult = TestSuiteResult.ABORTED
  }

  override fun testRunStopped(elapsedTime: Long) {
    testSuiteResult = TestSuiteResult.CANCELLED
  }

  override fun testRunEnded(elapsedTime: Long, runMetrics: MutableMap<String, String>) {
    listener.onTestSuiteFinished(deviceId, testSuiteId, testSuiteResult)
  }
}