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

import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultStats
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultsTreeNode
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.util.toIoFile
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.testframework.export.ExportTestResultsConfiguration
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TemporaryDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.`when`
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
@RunsInEdt
class ExportUtilsTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val temporaryDirectoryRule = TemporaryDirectory()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)
    .around(temporaryDirectoryRule)

  @Test
  fun exportToXml() {
    val fileContent = exportAndGetContent(ExportTestResultsConfiguration.ExportFormat.Xml)
    assertThat(fileContent).contains("""<suite name="testDeviceName1" duration="1234" status="passed">""")
    assertThat(fileContent).contains("""<suite name="testDeviceName2" duration="7777" status="failed">""")
    assertThat(fileContent).contains("""<androidTestMatrix executionDuration="1234">""")
    assertThat(fileContent).contains("""<device id="testDeviceId1" deviceName="testDeviceName1" deviceType="LOCAL_EMULATOR" version="23">""")
    assertThat(fileContent).contains("""<device id="testDeviceId2" deviceName="testDeviceName2" deviceType="LOCAL_PHYSICAL_DEVICE" version="24">""")
    assertThat(fileContent).contains("""<testsuite deviceId="testDeviceId1" testCount="1" result="PASSED">""")
    assertThat(fileContent).contains("""<testcase id="testpackage.testclass.testmethod" methodName="testmethod" className="testclass" packageName="testpackage" result="PASSED" logcat="" errorStackTrace="" startTimestampMillis="0" endTimestampMillis="1234" benchmark=""/>""")
  }

  @Test
  fun exportToHtml() {
    val fileContent = exportAndGetContent(ExportTestResultsConfiguration.ExportFormat.BundledTemplate)
    assertThat(fileContent).contains("<title>Test Results &mdash; testRunConfig</title>")
  }

  private fun exportAndGetContent(format: ExportTestResultsConfiguration.ExportFormat): String {
    lateinit var outputVirtualFile: VirtualFile
    runWriteAction {
      val inputDir = temporaryDirectoryRule.createVirtualDir("outputDir")
      outputVirtualFile = inputDir.createChildData(this, "output.${format.defaultExtension}")
    }

    val outputFile = outputVirtualFile.toIoFile()
    val exportConfig = mock<ExportTestResultsConfiguration>().apply {
      `when`(exportFormat).thenReturn(format)
    }

    val runConfig = mock<RunConfiguration>().apply {
      `when`(name).thenReturn("testRunConfig")
      `when`(type).thenReturn(AndroidTestRunConfigurationType.getInstance())
    }
    val (devices, resultsNode) = createDevicesAndResultsNode()

    val countDownLatch = CountDownLatch(1)
    var fileContent: String? = null
    exportAndroidTestMatrixResultXmlFile(projectRule.project, "run", exportConfig, outputFile,
                                         Duration.ofMillis(1234), resultsNode, runConfig, devices) {
      fileContent = Files.asCharSource(outputFile, Charsets.UTF_8).read()
      countDownLatch.countDown()
    }
    assertThat(countDownLatch.await(30, TimeUnit.SECONDS)).isTrue()

    return requireNotNull(fileContent)
  }

  private fun createDevicesAndResultsNode(): Pair<List<AndroidDevice>, AndroidTestResultsTreeNode> {
    val device1 = AndroidDevice(
      "testDeviceId1", "testDeviceName1",
      AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(23),
      mutableMapOf("processorName" to "testProcessorName1"))
    val device2 = AndroidDevice(
      "testDeviceId2", "testDeviceName2",
      AndroidDeviceType.LOCAL_PHYSICAL_DEVICE, AndroidVersion(24),
      mutableMapOf("processorName" to "testProcessorName2"))

    val rootResults = MockitoKt.mock<AndroidTestResults>().apply {
      `when`(getTotalDuration()).thenReturn(Duration.ofMillis(9011L))
      `when`(getResultStats()).thenReturn(AndroidTestResultStats(passed = 1, failed = 1))

      `when`(getTestCaseResult(MockitoKt.eq(device1))).thenReturn(AndroidTestCaseResult.PASSED)
      `when`(getDuration(MockitoKt.eq(device1))).thenReturn(Duration.ofMillis(1234L))

      `when`(getTestCaseResult(MockitoKt.eq(device2))).thenReturn(AndroidTestCaseResult.FAILED)
      `when`(getDuration(MockitoKt.eq(device2))).thenReturn(Duration.ofMillis(7777L))
    }
    val classResults = MockitoKt.mock<AndroidTestResults>().apply {
      `when`(methodName).thenReturn("")
      `when`(className).thenReturn("testclass")
      `when`(packageName).thenReturn("testpackage")

      `when`(getTestCaseResult(MockitoKt.eq(device1))).thenReturn(AndroidTestCaseResult.PASSED)
      `when`(getDuration(MockitoKt.eq(device1))).thenReturn(Duration.ofMillis(1234L))

      `when`(getTestCaseResult(MockitoKt.eq(device2))).thenReturn(AndroidTestCaseResult.FAILED)
      `when`(getDuration(MockitoKt.eq(device2))).thenReturn(Duration.ofMillis(7777L))
    }
    val caseResults = MockitoKt.mock<AndroidTestResults>().apply {
      `when`(methodName).thenReturn("testmethod")
      `when`(className).thenReturn("testclass")
      `when`(packageName).thenReturn("testpackage")

      `when`(getTestCaseResult(MockitoKt.eq(device1))).thenReturn(AndroidTestCaseResult.PASSED)
      `when`(getDuration(MockitoKt.eq(device1))).thenReturn(Duration.ofMillis(1234L))
      `when`(getLogcat(MockitoKt.eq(device1))).thenReturn("")
      `when`(getErrorStackTrace(MockitoKt.eq(device1))).thenReturn("")
      `when`(getBenchmark(MockitoKt.eq(device1))).thenReturn("")

      `when`(getTestCaseResult(MockitoKt.eq(device2))).thenReturn(AndroidTestCaseResult.FAILED)
      `when`(getDuration(MockitoKt.eq(device2))).thenReturn(Duration.ofMillis(7777L))
      `when`(getLogcat(MockitoKt.eq(device2))).thenReturn("")
      `when`(getErrorStackTrace(MockitoKt.eq(device2))).thenReturn("")
      `when`(getBenchmark(MockitoKt.eq(device2))).thenReturn("")
    }

    return Pair(listOf(device1, device2), AndroidTestResultsTreeNode(
      rootResults,
      sequenceOf(
        AndroidTestResultsTreeNode(
          classResults,
          sequenceOf(
            AndroidTestResultsTreeNode(
              caseResults,
              sequenceOf()
            )
          )
        )
      )
    ))
  }
}