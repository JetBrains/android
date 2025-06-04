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
package com.android.tools.idea.testartifacts.testsuite.temp

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.android.utils.ILogger
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.externalSystem.model.task.event.Failure
import com.intellij.openapi.externalSystem.model.task.event.FailureResult
import com.intellij.openapi.externalSystem.model.task.event.OperationResult
import com.intellij.openapi.externalSystem.model.task.event.SkippedResult
import com.intellij.openapi.externalSystem.model.task.event.SuccessResult
import com.intellij.openapi.externalSystem.model.task.event.TestAssertionFailure
import com.intellij.openapi.externalSystem.model.task.event.TestFailure
import org.jetbrains.plugins.gradle.execution.test.runner.events.GradleXmlTestEventConverter
import org.jetbrains.plugins.gradle.execution.test.runner.events.TestEventType
import org.jetbrains.plugins.gradle.execution.test.runner.events.TestEventXPPXmlView

/**
 * Adaptor to populate test status into [AndroidTestSuiteView] from [TestEventXPPXmlView].
 *
 * This class processes test events from a Gradle test runner, formatted as [TestEventXPPXmlView],
 * and updates a corresponding [AndroidTestSuiteView] to display the test results.
 *
 * ## Requirements
 * This adaptor is specifically designed to work with test engines that publish a `deviceId` as an
 * "additionalTestArtifacts" during the test execution, i.e.
 * `[additionalTestArtifacts]deviceId=emulator-5554`. This `deviceId` is crucial for associating
 * tests with the specific device on which they are run.
 *
 * ## Behaviour with Nested Test Suites
 * The [AndroidTestSuite] class does not support nested test suites. The test hierarchy shown in
 * the test result panel is constructed purely using the package and class names defined in the
 * [AndroidTestCase]. It only uses the reported test suites to update the test counts and result
 * status. As a result, the adaptor only considers the first parent test suite that publishes the
 * `deviceId` as the primary suite for reporting. Any nested or intermediate test suites are
 * effectively ignored in the final view presented by [AndroidTestSuiteView].
 */
class AndroidTestSuiteViewAdaptor(private val runConfiguration: RunConfiguration?) {
  // key: ID, value: parent ID.
  val parentId: MutableMap<String, String> = mutableMapOf()

  val deviceMap: MutableMap<String, AndroidDevice> = mutableMapOf()
  val testSuiteMap: MutableMap<String, AndroidTestSuite> = mutableMapOf()
  val testCaseMap: MutableMap<String, AndroidTestCase> = mutableMapOf()

  fun processEvent(xml: TestEventXPPXmlView, executionConsole: AndroidTestSuiteView) {
    return when(TestEventType.fromValue(xml.testEventType)) {
      TestEventType.BEFORE_SUITE -> {
        if (xml.testParentId != "") {
          parentId[xml.testId] = xml.testParentId
        }

        testSuiteMap.computeIfAbsent(xml.testId) { id ->
          AndroidTestSuite(
            id = id,
            name = xml.testName,
            testCaseCount = 0,
            result = null,
            runConfiguration
          )
        }

        Unit
      }

      TestEventType.BEFORE_TEST -> {
        parentId[xml.testId] = xml.testParentId

        val (rootTestSuiteId, device) = getRootTestIdAndDevice(xml) ?: return
        val rootTestSuite = testSuiteMap.get(rootTestSuiteId) ?: return

        val testCase = testCaseMap.computeIfAbsent(xml.testId) { id ->
          AndroidTestCase(
            id = id,
            methodName = xml.testName,
            className = xml.testClassName.substringAfterLast("."),
            packageName = xml.testClassName.substringBeforeLast("."),
            result = AndroidTestCaseResult.IN_PROGRESS
          )
        }

        rootTestSuite.testCaseCount += 1

        executionConsole.onTestCaseStarted(device, rootTestSuite, testCase)
      }

      TestEventType.AFTER_TEST -> {
        val (rootTestSuiteId, device) = getRootTestIdAndDevice(xml) ?: return
        val rootTestSuite = testSuiteMap[rootTestSuiteId] ?: return

        val testCase = testCaseMap.computeIfAbsent(xml.testId) { id ->
          AndroidTestCase(
            id = id,
            methodName = xml.testName,
            className = xml.testClassName.substringAfterLast("."),
            packageName = xml.testClassName.substringBeforeLast(".")
          )
        }

        val result = GradleXmlTestEventConverter.convertOperationResult(xml)
        applyResultToTestCase(testCase, result)

        executionConsole.onTestCaseFinished(device, rootTestSuite, testCase)
      }

      TestEventType.AFTER_SUITE -> {
        val (rootTestId, device) = getRootTestIdAndDevice(xml) ?: return
        if (rootTestId != xml.testId) {
          return
        }

        val result = GradleXmlTestEventConverter.convertOperationResult(xml)

        val rootTestSuite = testSuiteMap[rootTestId] ?: return
        applyResultToTestSuite(rootTestSuite, result)

        executionConsole.onTestSuiteFinished(device, rootTestSuite)
      }

      TestEventType.ON_OUTPUT -> {
        val output = GradleXmlTestEventConverter.decode(xml.testEventTest.trim())

        val testCase = testCaseMap[xml.testId]
        if (testCase != null) {
          output.lineSequence().forEach { line ->
            if (line.startsWith("[additionalTestArtifacts]")) {
              val (key, value) = line.substringAfter("[additionalTestArtifacts]").split("=", limit = 2) + listOf("", "")
              testCase.additionalTestArtifacts[key] = value
            } else if (line.isNotBlank()) {
              testCase.logcat += line + "\n"
            }
          }
        } else {
          val contentType = if ("StdOut" == xml.testEventTestDescription) {
            ConsoleViewContentType.NORMAL_OUTPUT
          } else {
            ConsoleViewContentType.ERROR_OUTPUT
          }
          output.lineSequence().forEach { line ->
            if (line.startsWith("[additionalTestArtifacts]")) {
              val (key, value) = line.substringAfter("[additionalTestArtifacts]").split("=", limit = 2) + listOf("", "")
              if (key == "deviceId") {
                val device = deviceMap.computeIfAbsent(xml.testId) {
                  AndroidDevice(
                    id = value,
                    deviceName = value,
                    avdName = "",
                    deviceType = AndroidDeviceType.LOCAL_EMULATOR,
                    version = AndroidVersion.DEFAULT
                  )
                }
                val testSuite = testSuiteMap[xml.testId] ?: return
                executionConsole.onTestSuiteScheduled(device)
                executionConsole.onTestSuiteStarted(device, testSuite)
              } else if (key == "deviceDisplayName") {
                deviceMap[xml.testId]?.deviceName = value
              }
            } else if (line.isNotBlank()) {
              executionConsole.print(line, contentType)
            }
          }
        }
      }
      TestEventType.REPORT_LOCATION -> {}
      TestEventType.CONFIGURATION_ERROR -> {}
      TestEventType.UNKNOWN_EVENT -> {}
    }
  }

  private fun getRootTestIdAndDevice(xml: TestEventXPPXmlView): Pair<String, AndroidDevice>? {
    var id = xml.testId
    var device = deviceMap[id]
    while (device == null && id != "") {
      id = parentId[id] ?: ""
      device = deviceMap[id]
    }
    return if (device != null) {
      id to device
    }
    else {
      null
    }
  }

  private fun applyResultToTestCase(testCase: AndroidTestCase, result: OperationResult) {
    testCase.startTimestampMillis = result.startTime
    testCase.endTimestampMillis = result.endTime

    when (result) {
      is SuccessResult -> testCase.result = AndroidTestCaseResult.PASSED
      is SkippedResult -> testCase.result = AndroidTestCaseResult.SKIPPED
      is FailureResult -> {
        testCase.result = AndroidTestCaseResult.FAILED
        when (val failure = result.failures.firstOrNull()) {
          is TestAssertionFailure -> testCase.errorStackTrace = failure.stackTrace ?: ""
          is TestFailure -> testCase.errorStackTrace = failure.stackTrace ?: ""
          is Failure -> testCase.errorStackTrace = failure.message ?: ""
        }
      }

      else -> {
        LOGGER.warning("Unhandled test result: $result")
      }
    }

  }

  private fun applyResultToTestSuite(testSuite: AndroidTestSuite, result: OperationResult) {
    when (result) {
      is SuccessResult -> testSuite.result = AndroidTestSuiteResult.PASSED
      is SkippedResult -> testSuite.result = AndroidTestSuiteResult.CANCELLED
      is FailureResult -> testSuite.result = AndroidTestSuiteResult.FAILED
      else -> {
        LOGGER.warning("Unhandled test result: $result")
      }
    }
  }

  companion object {
    val LOGGER: ILogger = LogWrapper(AndroidTestSuiteViewAdaptor::class.java)
  }
}