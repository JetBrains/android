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
package com.android.tools.idea.testartifacts.instrumented.testsuite.api

import com.android.annotations.concurrency.AnyThread
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestStep

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
   * @param device a device which a test suite to be executed on
   */
  @AnyThread
  fun onTestSuiteScheduled(device: AndroidDevice) {
    // no-op
  }

  /**
   * Called when a test suite execution is started.
   *
   * @param device a device which a test suite to be executed on
   * @param testSuite a test suite metadata
   */
  @AnyThread
  fun onTestSuiteStarted(device: AndroidDevice, testSuite: AndroidTestSuite) {
    // no-op
  }

  /**
   * Called when a test case execution is started.
   *
   * @param device a device which a test suite to be executed on
   * @param testSuite a test suite metadata
   * @param testCase a test case metadata
   */
  @AnyThread
  fun onTestCaseStarted(device: AndroidDevice, testSuite: AndroidTestSuite, testCase: AndroidTestCase) {
    // no-op
  }

  /**
   * Called when a test step execution is started.
   *
   * @param device a device which a test suite to be executed on
   * @param testCase a test case metadata
   * @param testStep a test step metadata
   */
  @AnyThread
  fun onTestStepStarted(device: AndroidDevice, testCase: AndroidTestCase, testStep: AndroidTestStep) {
    // no-op
  }

  /**
   * Called when a test step execution is finished.
   *
   * @param device a device which a test suite to be executed on
   * @param testCase a test case metadata
   * @param testStep a test step metadata
   */
  @AnyThread
  fun onTestStepFinished(device: AndroidDevice, testCase: AndroidTestCase, testStep: AndroidTestStep) {
    // no-op
  }

  /**
   * Called when a test case execution is finished.
   *
   * @param device a device which a test suite to be executed on
   * @param testSuite a test suite metadata
   * @param testCase a test case metadata
   */
  @AnyThread
  fun onTestCaseFinished(device: AndroidDevice, testSuite: AndroidTestSuite, testCase: AndroidTestCase) {
    // no-op
  }

  /**
   * Called when a test suite execution is finished. This method is also called when the execution is cancelled by a user or aborted by
   * tool failures.
   *
   * @param device a device which a test suite to be executed on
   * @param testSuite a test suite metadata
   */
  @AnyThread
  fun onTestSuiteFinished(device: AndroidDevice, testSuite: AndroidTestSuite) {
    // no-op
  }

  /**
   * Called when a re-run test execution is scheduled on a given device.
   *
   * @param device a device which a test suite to be executed on
   */
  @AnyThread
  fun onRerunScheduled(device: AndroidDevice) {
    // no-op
  }
}