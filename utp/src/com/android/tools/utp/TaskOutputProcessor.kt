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
import java.util.Base64

/**
 * Processes UTP test results XML tags in stdout text from Gradle task.
 *
 * @param listeners a list of listeners to be notified of test events
 */
class TaskOutputProcessor(val listeners: List<TaskOutputProcessorListener>) {

  companion object {
    const val ON_RESULT_OPENING_TAG = "<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>"
    const val ON_RESULT_CLOSING_TAG = "</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>"
    const val ON_COMPLETED_TAG = "<UTP_TEST_RESULT_ON_COMPLETED />"
    const val ON_ERROR_TAG = "<UTP_TEST_RESULT_ON_ERROR />"
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
      trimmedLine == ON_ERROR_TAG -> {
        listeners.forEach(TaskOutputProcessorListener::onError)
        true
      }
      trimmedLine == ON_COMPLETED_TAG -> {
        listeners.forEach(TaskOutputProcessorListener::onComplete)
        true
      }
      else -> false
    }
  }

  private fun processEvent(event: TestResultEvent) {
    when(event.stateCase) {
      TestResultEvent.StateCase.TEST_SUITE_STARTED -> {
        listeners.forEach(TaskOutputProcessorListener::onTestSuiteStarted)
      }
      TestResultEvent.StateCase.TEST_CASE_STARTED -> {
        listeners.forEach(TaskOutputProcessorListener::onTestCaseStarted)
      }
      TestResultEvent.StateCase.TEST_CASE_FINISHED -> {
        listeners.forEach(TaskOutputProcessorListener::onTestCaseFinished)
      }
      TestResultEvent.StateCase.TEST_SUITE_FINISHED -> {
        listeners.forEach(TaskOutputProcessorListener::onTestSuiteFinished)
      }
      else -> {}
    }
  }
}

/**
 * Decodes base64 encoded text proto message into [TestResultEvent].
 */
private fun decodeBase64EncodedProto(base64EncodedProto: String): TestResultEvent {
  return TestResultEvent.parseFrom(Base64.getDecoder().decode(base64EncodedProto))
}