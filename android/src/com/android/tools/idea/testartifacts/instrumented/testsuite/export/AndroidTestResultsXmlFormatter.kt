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
package com.android.tools.idea.testartifacts.instrumented.testsuite.export

import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultsTreeNode
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getFullTestCaseName
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getFullTestClassName
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.getName
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.application.ApplicationNamesInfo
import org.jdom.Element
import org.xml.sax.Attributes
import org.xml.sax.ContentHandler
import org.xml.sax.helpers.AttributesImpl
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale

/**
 * Formatter for a [AndroidTestResultsTreeNode] object and exports in XML file.
 */
class AndroidTestResultsXmlFormatter(
  private val executionDuration: Duration,
  private val rootResultsNode: AndroidTestResultsTreeNode,
  private val devices: List<AndroidDevice>,
  private val runConfiguration: RunConfiguration,
  private val resultHandler: ContentHandler,
  private val fileGenerationDateText: String = SimpleDateFormat("", Locale.US).format(Date())) {
  fun execute() {
    resultHandler.startDocument()

    addElement(
      "testrun",
      mapOf("duration" to rootResultsNode.results.getTotalDuration().toMillis().toString(),
            "footerText" to ExecutionBundle.message("export.test.results.footer",
                                                    ApplicationNamesInfo.getInstance().fullProductName,
                                                    fileGenerationDateText),
            "name" to runConfiguration.name)) {
      val stats = rootResultsNode.results.getResultStats()
      addElement(
        "count",
         mapOf("name" to "total",
               "value" to stats.total.toString()))
      if (stats.failed > 0) {
        addElement(
          "count",
          mapOf("name" to "failed",
                "value" to stats.failed.toString()))
      }
      if (stats.skipped > 0) {
        addElement(
          "count",
          mapOf("name" to "ignored",
                "value" to stats.skipped.toString()))
      }
      if (stats.cancelled > 0) {
        addElement(
          "count",
          mapOf("name" to "skipped",
                "value" to stats.cancelled.toString()))
      }
      if (stats.passed > 0) {
        addElement(
          "count",
          mapOf("name" to "passed",
                "value" to stats.passed.toString()))
      }

      addRunConfigElement()

      if (devices.size == 1) {
        addTestSuiteElementForDevice(devices.first())
      } else {
        devices.forEach { device ->
          val duration = rootResultsNode.results.getDuration(device)?.toMillis() ?: return@forEach
          val status = rootResultsNode.results.getTestCaseResult(device)?.toXmlStatusCode() ?: return@forEach
          addElement(
            "suite",
            mapOf(
              "name" to device.getName(),
              "duration" to duration.toString(),
              "status" to status)) {
            addTestSuiteElementForDevice(device)
          }
        }
      }

      addAndroidTestMatrixElement()
    }

    resultHandler.endDocument()
  }

  private fun addTestSuiteElementForDevice(device: AndroidDevice) {
    rootResultsNode.childResults.forEach { testSuite ->
      val duration = testSuite.results.getDuration(device)?.toMillis() ?: return@forEach
      val status = testSuite.results.getTestCaseResult(device)?.toXmlStatusCode() ?: return@forEach
      addElement(
        "suite",
        mapOf(
          "name" to testSuite.results.getFullTestClassName(),
          "duration" to duration.toString(),
          "status" to status)) {
        testSuite.childResults.forEach { testCase ->
          val duration = testCase.results.getDuration(device)?.toMillis() ?: return@forEach
          val status = testCase.results.getTestCaseResult(device)?.toXmlStatusCode() ?: return@forEach
          addElement(
            "test",
            mapOf(
              "name" to testCase.results.methodName,
              "duration" to duration.toString(),
              "status" to status)) {
            addOutputElement("stderr", testCase.results.getErrorStackTrace(device))
            addOutputElement("stdout", testCase.results.getBenchmark(device))
            addOutputElement("stdout", testCase.results.getLogcat(device))
          }
        }
      }
    }
  }

  private fun addOutputElement(type: String, content: String?) {
    if (content.isNullOrBlank()) {
      return
    }
    addElement("output", mapOf("type" to type)) {
      val chars = content.toCharArray()
      resultHandler.characters(chars, 0, chars.size)
    }
  }

  private fun Map<String, String>.toAttributes(): Attributes {
    val attributes = AttributesImpl()
    forEach { key, value ->
      attributes.addAttribute("", key, key, "CDATA", value)
    }
    return attributes
  }

  private fun AndroidTestCaseResult.toXmlStatusCode(): String {
    return when(this) {
      AndroidTestCaseResult.FAILED -> "failed"
      AndroidTestCaseResult.PASSED -> "passed"
      AndroidTestCaseResult.SKIPPED -> "ignored"
      AndroidTestCaseResult.IN_PROGRESS -> "skipped"
      AndroidTestCaseResult.CANCELLED -> "skipped"
      AndroidTestCaseResult.SCHEDULED -> "skipped"
    }
  }

  private fun addElement(name: String,
                         attributeMap: Map<String, String> = mapOf(),
                         innerElements: () -> Unit = {}) {
    resultHandler.startElement("", name, name, attributeMap.toAttributes())
    innerElements()
    resultHandler.endElement("", name, name)
  }

  private fun addElement(element: Element) {
    val attributes = element.attributes.fold(AttributesImpl()) { acc, attr ->
      acc.apply {
        addAttribute("", attr.name, attr.name, "CDATA", attr.value)
      }
    }
    resultHandler.startElement("", element.name, element.name, attributes)
    element.children.forEach(::addElement)
    resultHandler.endElement("", element.name, element.name)
  }

  private fun addRunConfigElement() {
    addElement(Element("config").apply {
      runConfiguration.writeExternal(this)
      setAttribute("configId", runConfiguration.type.id)
      setAttribute("name", runConfiguration.name)
    })
  }

  private fun addAndroidTestMatrixElement() {
    addElement(
      "androidTestMatrix",
      mapOf("executionDuration" to executionDuration.toMillis().toString())
    ) {
      devices.forEach { device ->
        addElement("device",
                   mapOf(
                     "id" to device.id,
                     "deviceName" to device.deviceName,
                     "deviceType" to device.deviceType.toString(),
                     "version" to device.version.apiLevel.toString())) {
          device.additionalInfo.forEach { (key, value) ->
            addElement("additionalInfo", mapOf("key" to key, "value" to value))
          }
        }
      }
      devices.forEach { device ->
        val testCasesForDevice = rootResultsNode.childResults.flatMap { testClasses ->
          testClasses.childResults.filter { testCases ->
            testCases.results.getTestCaseResult(device)?.isTerminalState == true
          }.map {
            it.results
          }
        }.toList()
        val testSuiteResult = when(rootResultsNode.results.getTestCaseResult(device)) {
          null -> null
          AndroidTestCaseResult.FAILED -> AndroidTestSuiteResult.FAILED
          AndroidTestCaseResult.CANCELLED -> AndroidTestSuiteResult.CANCELLED
          else -> AndroidTestSuiteResult.PASSED
        } ?: return@forEach
        addElement("testsuite",
                   mapOf(
                     "deviceId" to device.id,
                     "testCount" to testCasesForDevice.size.toString(),
                     "result" to testSuiteResult.toString())) {
          testCasesForDevice.forEach { testCase ->
            val result = testCase.getTestCaseResult(device) ?: return@forEach
            addElement(
              "testcase",
              mapOf(
                "id" to testCase.getFullTestCaseName(),
                "methodName" to testCase.methodName,
                "className" to testCase.className,
                "packageName" to testCase.packageName,
                "result" to result.toString(),
                "logcat" to testCase.getLogcat(device),
                "errorStackTrace" to testCase.getErrorStackTrace(device),
                "startTimestampMillis" to "0",
                "endTimestampMillis" to (testCase.getDuration(device)?.toMillis()?.toString() ?: "0"),
                "benchmark" to testCase.getBenchmark(device)
              ))
          }
        }
      }
    }
  }
}