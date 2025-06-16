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

import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.intellij.openapi.externalSystem.model.task.event.OperationResult
import org.jetbrains.plugins.gradle.execution.test.runner.events.GradleXmlTestEventConverter
import org.jetbrains.plugins.gradle.execution.test.runner.events.TestEventType
import org.jetbrains.plugins.gradle.execution.test.runner.events.TestEventXPPXmlView

interface TestSuiteViewAdaptor {

  fun processEvent(xml: TestEventXPPXmlView, executionConsole: AndroidTestSuiteView) {
    val testIdentifier = TestIdentifier(xml.testId, xml.testParentId, xml.testName, xml.testClassName)
    when (TestEventType.fromValue(xml.testEventType)) {
      TestEventType.BEFORE_SUITE -> {
        onBeforeSuite(testIdentifier, executionConsole)
      }

      TestEventType.BEFORE_TEST -> {
        onBeforeTest(testIdentifier, executionConsole)
      }

      TestEventType.AFTER_TEST -> {
        val result = GradleXmlTestEventConverter.convertOperationResult(xml)
        onAfterTest(testIdentifier, result, executionConsole)
      }

      TestEventType.AFTER_SUITE -> {
        val result = GradleXmlTestEventConverter.convertOperationResult(xml)
        onAfterSuite(testIdentifier, result, executionConsole)
      }

      TestEventType.ON_OUTPUT -> {
        val output = GradleXmlTestEventConverter.decode(xml.testEventTest.trim())
        val outputContentType = when (xml.testEventTestDescription) {
          "StdOut" -> OutputContentType.STD_OUT
          else -> OutputContentType.STD_ERR
        }
        onOutput(testIdentifier, output, outputContentType, executionConsole)
      }

      TestEventType.REPORT_LOCATION -> {}
      TestEventType.CONFIGURATION_ERROR -> {}
      TestEventType.UNKNOWN_EVENT -> {}
    }
  }

  fun onBeforeSuite(testIdentifier: TestIdentifier, executionConsole: AndroidTestSuiteView)
  fun onBeforeTest(testIdentifier: TestIdentifier, executionConsole: AndroidTestSuiteView)
  fun onAfterTest(testIdentifier: TestIdentifier, result: OperationResult, executionConsole: AndroidTestSuiteView)
  fun onAfterSuite(testIdentifier: TestIdentifier, result: OperationResult, executionConsole: AndroidTestSuiteView)
  fun onOutput(testIdentifier: TestIdentifier, output: String, contentType: OutputContentType, executionConsole: AndroidTestSuiteView)

  data class TestIdentifier(
    val id: String,
    val parentId: String,
    val name: String,
    val className: String,
  )

  enum class OutputContentType {
    STD_OUT,
    STD_ERR
  }
}
