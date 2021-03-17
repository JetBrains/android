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
package com.android.tools.utp

import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import java.util.Base64

/**
 * Processes UTP test results XML tags in stdout text from Gradle task.
 *
 * @param listeners a map from device ID to listener to be notified of test events
 */
class TaskOutputProcessor(val listeners: Map<String, TaskOutputProcessorListener>) {

  companion object {
    const val ON_RESULT_OPENING_TAG = "<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>"
    const val ON_RESULT_CLOSING_TAG = "</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>"
  }

  /**
   * Processes stdout text from the Gradle task output. The input string can be
   * multi-line string. The UTP test results XML tags are removed from the
   * returning string.
   *
   * @param stdout a stdout text from the AGP task to be processed
   */
  fun process(stdout: String): String {
    return stdout.lineSequence().filterNot(this::processLine).joinToString("\n")
  }

  private fun processLine(line: String): Boolean {
    val trimmedLine = line.trim()
    return when {
      trimmedLine.startsWith(ON_RESULT_OPENING_TAG) && line.endsWith(ON_RESULT_CLOSING_TAG) -> {
        val base64EncodedProto = trimmedLine.removeSurrounding(ON_RESULT_OPENING_TAG, ON_RESULT_CLOSING_TAG)
        val eventProto = decodeBase64EncodedProto(base64EncodedProto)
        processEvent(eventProto)
        true
      }
      else -> false
    }
  }

  private fun processEvent(event: TestResultEvent) {
    when(event.stateCase) {
      TestResultEvent.StateCase.TEST_SUITE_STARTED -> {
        processTestSuiteStarted(event)
      }
      TestResultEvent.StateCase.TEST_CASE_STARTED -> {
        processTestCaseStarted(event)
      }
      TestResultEvent.StateCase.TEST_CASE_FINISHED -> {
        processTestCaseFinished(event)
      }
      TestResultEvent.StateCase.TEST_SUITE_FINISHED -> {
        processTestSuiteFinished(event)
      }
      else -> {}
    }
  }

  private fun processTestSuiteStarted(event: TestResultEvent) {
    val testSuiteStarted = event.testSuiteStarted
    val testSuite = testSuiteStarted.testSuiteMetadata.unpack(TestSuiteResultProto.TestSuiteMetaData::class.java)
    listeners[event.deviceId]?.onTestSuiteStarted(testSuite)
  }

  private fun processTestCaseStarted(event: TestResultEvent) {
    val testCaseStarted = event.testCaseStarted
    val testCase = testCaseStarted.testCase.unpack(TestCaseProto.TestCase::class.java)
    listeners[event.deviceId]?.onTestCaseStarted(testCase)
  }

  private fun processTestCaseFinished(event: TestResultEvent) {
    val testCaseFinished = event.testCaseFinished
    val testCaseResult = testCaseFinished.testCaseResult.unpack(TestResultProto.TestResult::class.java)
    listeners[event.deviceId]?.onTestCaseFinished(testCaseResult)
  }

  private fun processTestSuiteFinished(event: TestResultEvent) {
    val testSuiteFinished = event.testSuiteFinished
    val testSuiteResult = testSuiteFinished.testSuiteResult.unpack(TestSuiteResultProto.TestSuiteResult::class.java)
    listeners[event.deviceId]?.onTestSuiteFinished(testSuiteResult)
  }
}

/**
 * Decodes base64 encoded text proto message into [TestResultEvent].
 */
private fun decodeBase64EncodedProto(base64EncodedProto: String): TestResultEvent {
  return TestResultEvent.parseFrom(Base64.getDecoder().decode(base64EncodedProto))
}