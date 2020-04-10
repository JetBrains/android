/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.annotations.concurrency.WorkerThread
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.google.test.platform.core.proto.TestStatusProto
import com.google.test.platform.core.proto.TestSuiteResultProto
import java.io.InputStream

/**
 * An adapter to parse Unified Test Platform (UTP) result protobuf, and forward them to
 * AndroidTestResultListener
 *
 * @param listener the listener to receive the test results
 */
class UtpTestResultAdapter(private val listener: AndroidTestResultListener) {
  /**
   * Parse UTP test results and forward to the listener
   *
   * @param inputStream contains the binary protobuf of the UTP test suite results
   */
  @WorkerThread
  fun importResult(inputStream: InputStream) {
    val resultProto = TestSuiteResultProto.TestSuiteResult.parseFrom(inputStream)
    // TODO(b/154140562): Populate device data in test result protobuf
    val device = AndroidDevice("external",
                               "external device",
                               AndroidDeviceType.LOCAL_PHYSICAL_DEVICE,
                               AndroidVersion.DEFAULT)
    val testSuite = AndroidTestSuite(resultProto.testSuiteMetaData.testSuiteName,
                                     resultProto.testSuiteMetaData.testSuiteName,
                                     resultProto.testResultCount,
                                     null)
    var testSuiteResult = AndroidTestSuiteResult.PASSED
    listener.onTestSuiteScheduled(device)
    listener.onTestSuiteStarted(device, testSuite)
    for (testResultProto in resultProto.testResultList) {
      val testCaseProto = testResultProto.testCase
      val fullName = "${testCaseProto.testPackage}:${testCaseProto.testClass}#${testCaseProto.testMethod}"
      val testCase = AndroidTestCase(fullName, testCaseProto.testMethod, when (testResultProto.testStatus) {
        TestStatusProto.TestStatus.PASSED -> AndroidTestCaseResult.PASSED
        TestStatusProto.TestStatus.FAILED -> AndroidTestCaseResult.FAILED
        else -> AndroidTestCaseResult.SKIPPED
      })
      if (testResultProto.testStatus == TestStatusProto.TestStatus.FAILED) {
        testSuiteResult = AndroidTestSuiteResult.FAILED
      }
      listener.onTestCaseStarted(device, testSuite, testCase)
      listener.onTestCaseFinished(device, testSuite, testCase)
    }
    testSuite.result = testSuiteResult
    listener.onTestSuiteFinished(device, testSuite)
  }
}