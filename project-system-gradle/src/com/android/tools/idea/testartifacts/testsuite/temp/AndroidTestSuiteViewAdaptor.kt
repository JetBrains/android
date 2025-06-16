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
class AndroidTestSuiteViewAdaptor(private val runConfiguration: RunConfiguration?) : TestSuiteViewAdaptor {
  // key: ID, value: parent ID.
  val parentId: MutableMap<String, String> = mutableMapOf()

  val deviceMap: MutableMap<String, AndroidDevice> = mutableMapOf()
  val testSuiteMap: MutableMap<String, AndroidTestSuite> = mutableMapOf()
  val testCaseMap: MutableMap<String, AndroidTestCase> = mutableMapOf()

  override fun onBeforeSuite(testIdentifier: TestSuiteViewAdaptor.TestIdentifier, executionConsole: AndroidTestSuiteView) {
    if (testIdentifier.parentId != "") {
      parentId[testIdentifier.id] = testIdentifier.parentId
    }

    testSuiteMap.computeIfAbsent(testIdentifier.id) { id ->
      AndroidTestSuite(
        id = id,
        name = testIdentifier.name,
        testCaseCount = 0,
        result = null,
        runConfiguration
      )
    }
  }

  override fun onBeforeTest(testIdentifier: TestSuiteViewAdaptor.TestIdentifier, executionConsole: AndroidTestSuiteView) {
    parentId[testIdentifier.id] = testIdentifier.parentId

    val (rootTestSuiteId, device) = getRootTestIdAndDevice(testIdentifier.id) ?: return
    val rootTestSuite = testSuiteMap.get(rootTestSuiteId) ?: return

    val testCase = testCaseMap.computeIfAbsent(testIdentifier.id) { id ->
      val methodName = testIdentifier.name
      val className = testIdentifier.className.substringAfterLast(".")
      val packageName = if (testIdentifier.className.contains(".")) testIdentifier.className.substringBeforeLast(".") else ""
      AndroidTestCase(
        id = id,
        methodName = methodName,
        className = className,
        packageName = packageName,
        result = AndroidTestCaseResult.IN_PROGRESS,
      )
    }

    rootTestSuite.testCaseCount += 1

    executionConsole.onTestCaseStarted(device, rootTestSuite, testCase)
  }

  override fun onAfterTest(testIdentifier: TestSuiteViewAdaptor.TestIdentifier,
                           result: OperationResult,
                           executionConsole: AndroidTestSuiteView) {

    val (rootTestSuiteId, device) = getRootTestIdAndDevice(testIdentifier.id) ?: return
    val rootTestSuite = testSuiteMap[rootTestSuiteId] ?: return

    val testCase = testCaseMap.computeIfAbsent(testIdentifier.id) { id ->
      AndroidTestCase(
        id = id,
        methodName = testIdentifier.name,
        className = testIdentifier.className.substringAfterLast("."),
        packageName = testIdentifier.className.substringBeforeLast(".")
      )
    }

    applyResultToTestCase(testCase, result)

    executionConsole.onTestCaseFinished(device, rootTestSuite, testCase)
  }

  override fun onAfterSuite(testIdentifier: TestSuiteViewAdaptor.TestIdentifier,
                            result: OperationResult,
                            executionConsole: AndroidTestSuiteView) {

    val (rootTestId, device) = getRootTestIdAndDevice(testIdentifier.id) ?: return
    if (rootTestId != testIdentifier.id) {
      return
    }

    val rootTestSuite = testSuiteMap[rootTestId] ?: return
    applyResultToTestSuite(rootTestSuite, result)

    executionConsole.onTestSuiteFinished(device, rootTestSuite)
  }

  override fun onOutput(testIdentifier: TestSuiteViewAdaptor.TestIdentifier,
                        output: String,
                        contentType: TestSuiteViewAdaptor.OutputContentType,
                        executionConsole: AndroidTestSuiteView) {
    val testCase = testCaseMap[testIdentifier.id]
    if (testCase != null) {
      output.lineSequence().forEach { line ->
        if (line.startsWith("[additionalTestArtifacts]")) {
          val (key, value) = line.substringAfter("[additionalTestArtifacts]").split("=", limit = 2) + listOf("", "")
          testCase.additionalTestArtifacts[key] = value
        }
        else if (line.isNotBlank()) {
          testCase.logcat += line + "\n"
        }
      }
    }
    else {
      val contentType = if (contentType == TestSuiteViewAdaptor.OutputContentType.STD_OUT) {
        ConsoleViewContentType.NORMAL_OUTPUT
      }
      else {
        ConsoleViewContentType.ERROR_OUTPUT
      }
      output.lineSequence().forEach { line ->
        if (line.startsWith("[additionalTestArtifacts]")) {
          val (key, value) = line.substringAfter("[additionalTestArtifacts]").split("=", limit = 2) + listOf("", "")
          if (key == "deviceId") {
            val device = deviceMap.computeIfAbsent(testIdentifier.id) { id ->
              AndroidDevice(
                id = value,
                deviceName = value,
                avdName = "",
                deviceType = AndroidDeviceType.LOCAL_EMULATOR,
                version = AndroidVersion.DEFAULT
              )
            }
            val testSuite = testSuiteMap[testIdentifier.id] ?: return
            executionConsole.onTestSuiteScheduled(device)
            executionConsole.onTestSuiteStarted(device, testSuite)
          }
          else if (key == "deviceDisplayName") {
            deviceMap[testIdentifier.id]?.deviceName = value
          }
        }
        else if (line.isNotBlank()) {
          executionConsole.print(line, contentType)
        }
      }
    }
  }

  private fun getRootTestIdAndDevice(testId: String): Pair<String, AndroidDevice>? {
    var id = testId
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