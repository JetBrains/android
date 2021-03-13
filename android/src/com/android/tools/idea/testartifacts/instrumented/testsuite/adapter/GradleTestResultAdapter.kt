/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.ddmlib.testrunner.TestIdentifier
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.android.tools.utp.TaskOutputProcessorListener
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.intellij.psi.util.ClassUtil
import java.util.UUID

/**
 * An adapter to parse instrumentation test result protobuf messages from AGP and forward them to AndroidTestResultListener
 */
class GradleTestResultAdapter(device: IDevice,
                              private val testSuiteDisplayName: String,
                              private val listener: AndroidTestResultListener): TaskOutputProcessorListener {

  val device: AndroidDevice = convertIDeviceToAndroidDevice(device)

  init {
    // Schedule test suite for selected devices when instrumentation tests are executed by AGP.
    listener.onTestSuiteScheduled(this.device)
  }

  private lateinit var myTestSuite: AndroidTestSuite

  private val myTestCases = mutableMapOf<TestIdentifier, AndroidTestCase>()

  // This map keeps track of number of rerun of the same test method.
  // This value is used to create a unique identifier for each test case
  // yet to be able to group them together across multiple devices.
  private val myTestCaseRunCount: MutableMap<String, Int> = mutableMapOf()

  override fun onTestSuiteStarted(testSuite: TestSuiteResultProto.TestSuiteMetaData) {
    myTestSuite = AndroidTestSuite(
      id = UUID.randomUUID().toString(),
      testSuiteDisplayName,
      testSuite.scheduledTestCaseCount)
    listener.onTestSuiteStarted(device, myTestSuite)
  }

  override fun onTestCaseStarted(testCase: TestCaseProto.TestCase) {
    val testId = testCase.toTestIdentifier()
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
    listener.onTestCaseStarted(device, myTestSuite, testCase)
  }

  override fun onTestCaseFinished(testCaseResult: TestResultProto.TestResult) {
    val testId = testCaseResult.testCase.toTestIdentifier()
    val testCase = myTestCases.getValue(testId)
    testCase.result = testCaseResult.testStatus.toAndroidTestCaseResult()
    testCase.endTimestampMillis = System.currentTimeMillis()

    if (testCaseResult.error.errorMessage.isNotBlank()) {
      testCase.errorStackTrace = testCaseResult.error.errorMessage
    }

    listener.onTestCaseFinished(device, myTestSuite, testCase)
  }

  override fun onTestSuiteFinished(testSuiteResult: TestSuiteResultProto.TestSuiteResult) {
    myTestSuite.result = testSuiteResult.testStatus.toAndroidTestSuiteResult()
    listener.onTestSuiteFinished(device, myTestSuite)
  }

  override fun onError() {}

  override fun onComplete() {}
}

private fun TestCaseProto.TestCase.toTestIdentifier(): TestIdentifier {
  return TestIdentifier("${testPackage}.${testClass}", testMethod)
}

private fun TestStatusProto.TestStatus.toAndroidTestCaseResult(): AndroidTestCaseResult {
  return when(this) {
    TestStatusProto.TestStatus.PASSED -> AndroidTestCaseResult.PASSED
    TestStatusProto.TestStatus.FAILED -> AndroidTestCaseResult.FAILED
    TestStatusProto.TestStatus.IGNORED -> AndroidTestCaseResult.SKIPPED
    TestStatusProto.TestStatus.ERROR -> AndroidTestCaseResult.FAILED
    TestStatusProto.TestStatus.ABORTED -> AndroidTestCaseResult.FAILED
    TestStatusProto.TestStatus.CANCELLED -> AndroidTestCaseResult.CANCELLED
    else -> AndroidTestCaseResult.IN_PROGRESS
  }
}

private fun TestStatusProto.TestStatus.toAndroidTestSuiteResult(): AndroidTestSuiteResult {
  return when(this) {
    TestStatusProto.TestStatus.PASSED -> AndroidTestSuiteResult.PASSED
    TestStatusProto.TestStatus.FAILED -> AndroidTestSuiteResult.FAILED
    TestStatusProto.TestStatus.IGNORED -> AndroidTestSuiteResult.PASSED
    TestStatusProto.TestStatus.ERROR -> AndroidTestSuiteResult.FAILED
    TestStatusProto.TestStatus.ABORTED -> AndroidTestSuiteResult.ABORTED
    TestStatusProto.TestStatus.CANCELLED -> AndroidTestSuiteResult.CANCELLED
    else -> AndroidTestSuiteResult.ABORTED
  }
}