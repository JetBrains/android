/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.utp

import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto

class AgentGlobalTaskOutputProcessorListenerAdaptor(
  val listener: Listener
) : GlobalTaskOutputProcessorListener {
  override fun onTestSuiteStarted(deviceId: String,
                                  testSuite: TestSuiteResultProto.TestSuiteMetaData) = Unit

  override fun onTestCaseStarted(deviceId: String, testCase: TestCaseProto.TestCase) = Unit

  override fun onTestCaseFinished(deviceId: String,
                                  testCaseResult: TestResultProto.TestResult) = Unit

  override fun onTestSuiteFinished(deviceId: String,
                                   testSuiteResult: TestSuiteResultProto.TestSuiteResult) {
    val testCaseResult = Listener.TestSuiteResult(
      deviceId,
      testSuiteResult.testResultList.map {
        Listener.TestCaseResult(
          it.testCase.testPackage,
          it.testCase.testClass,
          it.testCase.testMethod,
          it.testStatus.name,
          it.error?.stackTrace
        )
      }
    )
    listener.onTestSuiteFinished(testCaseResult)
  }

  interface Listener {
    data class TestCaseResult(
      val packageName: String,
      val className: String,
      val methodName: String,
      val status: String,
      val errorStacktrace: String?
    )
    data class TestSuiteResult(
      val deviceId: String,
      val testCases: List<TestCaseResult>
    )
    fun onTestSuiteFinished(result: TestSuiteResult)
  }
}