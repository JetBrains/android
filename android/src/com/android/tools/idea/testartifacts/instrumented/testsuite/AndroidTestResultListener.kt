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
package com.android.tools.idea.testartifacts.instrumented.testsuite

/**
 * A listener to subscribe Android instrumentation test execution progress.
 *
 * A test suite means a set of test cases to be executed (including skipped). A test case means a single test method.
 * [onTestSuiteScheduled] callback is always invoked first, followed by [onTestSuiteStarted], [onTestCaseStarted],
 * [onTestCaseFinished], ([onTestCaseStarted], [onTestCaseFinished], ...), and [onTestSuiteFinished].
 */
interface AndroidTestResultListener {
  /**
   * Called when a test execution is scheduled on a given device.
   *
   * @param deviceId a device identifier for a test suite to be executed on
   */
  fun onTestSuiteScheduled(deviceId: String)

  /**
   * Called when a test suite execution is started.
   *
   * @param deviceId a device identifier for a test suite to be executed on
   * @param testSuiteId a test suite identifier. This can be arbitrary string as long as it is unique to other test suites.
   * @param testCaseCount a number of test cases in the suite including suppressed test cases
   */
  fun onTestSuiteStarted(deviceId: String, testSuiteId: String, testCaseCount: Int)

  /**
   * Called when a test case execution is started.
   *
   * @param deviceId a device identifier for a test suite to be executed on
   * @param testSuiteId a test suite identifier. This can be arbitrary string as long as it is unique to other test suites.
   * @param testCaseId a test case identifier. This can be arbitrary string as long as it is unique to other test cases.
   */
  fun onTestCaseStarted(deviceId: String, testSuiteId: String, testCaseId: String)

  /**
   * Called when a test case execution is finished.
   *
   * @param deviceId a device identifier for a test suite to be executed on
   * @param testSuiteId a test suite identifier. This can be arbitrary string as long as it is unique to other test suites.
   * @param testCaseId a test case identifier. This can be arbitrary string as long as it is unique to other test cases.
   * @param result a result of the test case execution
   */
  fun onTestCaseFinished(deviceId: String, testSuiteId: String, testCaseId: String, result: TestCaseResult)

  /**
   * Called when a test suite execution is finished. This method is also called when the execution is cancelled by a user or aborted by
   * tool failures. Check [result] to distinguish them.
   *
   * @param deviceId a device identifier for a test suite to be executed on
   * @param testSuiteId a test suite identifier. This can be arbitrary string as long as it is unique to other test suites.
   * @param result a result of the test suite execution
   */
  fun onTestSuiteFinished(deviceId: String, testSuiteId: String, result: TestSuiteResult)
}

/**
 * A result of a test case execution.
 */
enum class TestCaseResult {
  /**
   * A test case is passed.
   */
  PASSED,

  /**
   * A test case is failed.
   */
  FAILED,

  /**
   * A test case is skipped by test runner.
   */
  SKIPPED
}

/**
 * A result of a test suite execution.
 */
enum class TestSuiteResult {
  /**
   * All tests in a test suite are passed.
   */
  PASSED,

  /**
   * At least one test case in a test suite is failed.
   */
  FAILED,

  /**
   * A test suite execution is aborted by tool failure.
   */
  ABORTED,

  /**
   * A test suite execution is cancelled by a user before it completes.
   */
  CANCELLED
}