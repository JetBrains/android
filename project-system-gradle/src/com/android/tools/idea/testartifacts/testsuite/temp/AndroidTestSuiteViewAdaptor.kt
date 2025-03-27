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
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.externalSystem.model.task.event.Failure
import com.intellij.openapi.externalSystem.model.task.event.FailureResult
import com.intellij.openapi.externalSystem.model.task.event.SkippedResult
import com.intellij.openapi.externalSystem.model.task.event.SuccessResult
import com.intellij.openapi.externalSystem.model.task.event.TestAssertionFailure
import com.intellij.openapi.externalSystem.model.task.event.TestFailure
import org.jetbrains.plugins.gradle.execution.test.runner.events.GradleXmlTestEventConverter
import org.jetbrains.plugins.gradle.execution.test.runner.events.TestEventType
import org.jetbrains.plugins.gradle.execution.test.runner.events.TestEventXPPXmlView
import java.util.UUID

/**
 * Adaptor to populate test status into [AndroidTestSuiteView] from [TestEventXPPXmlView].
 */
class AndroidTestSuiteViewAdaptor {
  val rootTestSuite: AndroidTestSuite = AndroidTestSuite(
    id = UUID.randomUUID().toString(),
    name = "",
    testCaseCount = 0,
  )

  // key: ID, value: parent ID.
  val parentId: MutableMap<String, String> = mutableMapOf()

  val deviceMap: MutableMap<String, AndroidDevice> = mutableMapOf()
  val testCaseMap: MutableMap<String, AndroidTestCase> = mutableMapOf()

  fun getRootTestIdAndDevice(xml: TestEventXPPXmlView): Pair<String, AndroidDevice>? {
    var id = xml.testId
    var device = deviceMap[id]
    while (device == null && id != "") {
      id = parentId[id] ?: ""
      device = deviceMap[id]
    }
    return if (device != null) {
      id to device
    } else {
      null
    }
  }

  fun processEvent(xml: TestEventXPPXmlView, executionConsole: AndroidTestSuiteView) {
    return when(TestEventType.fromValue(xml.testEventType)) {
      TestEventType.BEFORE_SUITE -> {
        parentId[xml.testId] = xml.testParentId
      }

      TestEventType.BEFORE_TEST -> {
        parentId[xml.testId] = xml.testParentId
        val (_, device) = getRootTestIdAndDevice(xml) ?: return
        val testCase = testCaseMap.computeIfAbsent(xml.testId) { id ->
          AndroidTestCase(
            id = id,
            methodName = xml.testName,
            className = xml.testClassName.substringAfterLast("."),
            packageName = xml.testClassName.substringBeforeLast(".")
          )
        }
        rootTestSuite.testCaseCount = testCaseMap.size

        executionConsole.onTestCaseStarted(
          device,
          rootTestSuite,
          testCase
        )
      }

      TestEventType.AFTER_TEST -> {
        val (_, device) = getRootTestIdAndDevice(xml) ?: return
        val testCase = testCaseMap.computeIfAbsent(xml.testId) { id ->
          AndroidTestCase(
            id = id,
            methodName = xml.testName,
            className = xml.testClassName.substringAfterLast("."),
            packageName = xml.testClassName.substringBeforeLast(".")
          )
        }
        rootTestSuite.testCaseCount = testCaseMap.size

        val result = GradleXmlTestEventConverter.convertOperationResult(xml)
        testCase.startTimestampMillis = result.startTime
        testCase.endTimestampMillis = result.endTime

        when (result) {
          is SuccessResult -> {
            testCase.result = AndroidTestCaseResult.PASSED
          }
          is FailureResult -> {
            testCase.result = AndroidTestCaseResult.FAILED
            when (val failure = result.failures.firstOrNull()) {
              is TestAssertionFailure -> {
                testCase.errorStackTrace = failure.stackTrace ?: ""
              }
              is TestFailure -> {
                testCase.errorStackTrace = failure.stackTrace ?: ""
              }
              is Failure -> {
                testCase.errorStackTrace = failure.message ?: ""
              }
            }
          }
          is SkippedResult -> {
            testCase.result = AndroidTestCaseResult.SKIPPED
          }
        }

        executionConsole.onTestCaseFinished(
          device,
          rootTestSuite,
          testCase
        )
      }

      TestEventType.AFTER_SUITE -> {
        val (id, device) = getRootTestIdAndDevice(xml) ?: return
        if (id != xml.testId) {
          return
        }
        rootTestSuite.result = AndroidTestSuiteResult.PASSED
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
                deviceMap.computeIfAbsent(xml.testId) {
                  AndroidDevice(
                    id = value,
                    deviceName = value,
                    avdName = "",
                    deviceType = AndroidDeviceType.LOCAL_EMULATOR,
                    version = AndroidVersion.DEFAULT
                  ).also {
                    executionConsole.onTestSuiteScheduled(it)
                    executionConsole.onTestSuiteStarted(it, rootTestSuite)
                  }
                }
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
}