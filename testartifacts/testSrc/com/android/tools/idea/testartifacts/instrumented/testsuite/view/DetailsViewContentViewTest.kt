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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.AndroidTestSuiteLogger
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark.BenchmarkOutput
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

/**
 * Unit tests for [DetailsViewContentView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class DetailsViewContentViewTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  @Mock lateinit var mockLogger: AndroidTestSuiteLogger

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  lateinit var mockTestResults: AndroidTestResults

  @get:Rule val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)

  @Before
  fun setup() {
    MockitoAnnotations.openMocks(this)
  }

  @Test
  fun testResultLabelOnPassing() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getTestCaseResult(testDevice)).thenReturn(AndroidTestCaseResult.PASSED)

    view.setResults(testDevice, mockTestResults)

    assertThat(view.myDeviceTestResultLabel.text).isEqualTo("<html>device name</html>")
    assertThat(view.myTestResultLabel.text)
      .isEqualTo("<html><font color='#6cad74'>Passed</font></html>")
  }

  @Test
  fun testResultLabelOnFailing() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getTestCaseResult(testDevice)).thenReturn(AndroidTestCaseResult.FAILED)
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("")

    view.setResults(testDevice, mockTestResults)

    assertThat(view.myDeviceTestResultLabel.text).isEqualTo("<html>device name</html>")
    assertThat(view.myTestResultLabel.text).isEqualTo(
      "<html><font color='#b81708'>Failed</font></html>")
  }

  @Test
  fun testResultLabelOnFailingWithErrorStackTrace() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getTestCaseResult(testDevice)).thenReturn(AndroidTestCaseResult.FAILED)
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("ErrorStackTrace")

    view.setResults(testDevice, mockTestResults)

    assertThat(view.myDeviceTestResultLabel.text).isEqualTo("<html>device name</html>")
    assertThat(view.myTestResultLabel.text).isEqualTo(
      "<html><font color='#b81708'>Failed</font> ErrorStackTrace</html>")
  }

  @Test
  fun testResultLabelHtmlEscaping() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "<device name>")
    whenever(mockTestResults.getTestCaseResult(testDevice)).thenReturn(AndroidTestCaseResult.FAILED)
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("<ErrorStackTrace>")

    view.setResults(testDevice, mockTestResults)

    assertThat(view.myDeviceTestResultLabel.text).isEqualTo("<html>&lt;device name&gt;</html>")
    assertThat(view.myTestResultLabel.text).isEqualTo(
      "<html><font color='#b81708'>Failed</font> &lt;ErrorStackTrace&gt;</html>")
  }

  @Test
  fun testResultLabelOnRunning() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getTestCaseResult(testDevice)).thenReturn(AndroidTestCaseResult.IN_PROGRESS)

    view.setResults(testDevice, mockTestResults)

    assertThat(view.myDeviceTestResultLabel.text).isEqualTo("<html>device name</html>")
    assertThat(view.myTestResultLabel.text).isEqualTo("Running on device name")
  }

  @Test
  fun testResultLabelNoTestStatus() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getTestCaseResult(testDevice)).thenReturn(null)

    view.setResults(testDevice, mockTestResults)

    assertThat(view.myDeviceTestResultLabel.text).isEqualTo("<html>device name</html>")
    assertThat(view.myTestResultLabel.text).isEqualTo("No test status available")
  }

  @Test
  fun logsView() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("test logcat message")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("")

    view.setResults(testDevice, mockTestResults)

    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\n")
  }

  @Test
  fun logsViewWithErrorStackTrace() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("test logcat message")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("error stack trace")

    view.setResults(testDevice, mockTestResults)

    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\nerror stack trace")
  }

  @Test
  fun logsViewWithNoLogsAndErrorStackTrace() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("error stack trace")

    view.setResults(testDevice, mockTestResults)

    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("error stack trace")
  }

  @Test
  fun logsViewWithNoMessage() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")

    view.setResults(testDevice, mockTestResults)

    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("No logs available")
    assertThat(view.logsTab.isHidden).isFalse()
  }

  @Test
  fun logsViewShouldClearPreviousMessage() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")

    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("test logcat message")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("")
    view.setResults(testDevice, mockTestResults)
    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\n")

    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("test logcat message 2")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("")
    view.setResults(testDevice, mockTestResults)
    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message 2\n")
  }

  @Test
  fun logsViewShouldShouldNotRefreshWhenMessageUnchanged() {
    val view = spy(DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger))
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("test logcat message")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("")

    view.setResults(testDevice, mockTestResults)
    view.setResults(testDevice, mockTestResults)
    view.myLogsView.waitAllRequests()

    verify(view, times(1)).refreshLogsView()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\n")
  }

  @Test
  fun benchmarkTab() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getBenchmark(testDevice)).thenReturn(BenchmarkOutput("test benchmark message"))

    view.setResults(testDevice, mockTestResults)

    view.myBenchmarkView.waitAllRequests()
    assertThat(view.myBenchmarkView.text).isEqualTo("test benchmark message\n")
    assertThat(view.myBenchmarkTab.isHidden).isFalse()
    assertThat(view.tabs.selectedInfo).isEqualTo(view.myBenchmarkTab)
  }

  @Test
  fun benchmarkTabIsHiddenIfNoOutput() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getBenchmark(testDevice)).thenReturn(BenchmarkOutput.Empty)

    view.setResults(testDevice, mockTestResults)

    view.myBenchmarkView.waitAllRequests()
    assertThat(view.myBenchmarkView.text).isEqualTo("")
    assertThat(view.myBenchmarkTab.isHidden).isTrue()
  }

  @Test
  fun logging() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    verify(mockLogger).addImpressionWhenDisplayed(view.myLogsView.component,
                                                  ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_LOG_VIEW)
    verify(mockLogger).addImpressionWhenDisplayed(view.myDeviceInfoTableView.getComponent(),
                                                  ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_DEVICE_INFO_VIEW)
  }

  @Test
  fun screenshotTabsHiddenByDefault() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)

    assertThat(view.myScreenshotTab.isHidden).isTrue()
    assertThat(view.myScreenshotAttributesTab.isHidden).isTrue()
    assertThat(view.myDeviceInfoTab.isHidden).isFalse()
  }

  @Test
  fun screenshotTabsDisplayedForScreenshotTests() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getAdditionalTestArtifacts(testDevice)).thenReturn(
      mapOf("PreviewScreenshot.newImagePath" to "/path/to/newImage"))

    view.setResults(testDevice, mockTestResults)

    assertThat(view.myScreenshotTab.isHidden).isFalse()
    assertThat(view.myScreenshotAttributesTab.isHidden).isFalse()
    assertThat(view.myDeviceInfoTab.isHidden).isTrue()
  }

  @Test
  fun screenshotLogsTabAlwaysDisplayedForScreenshotTests() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getAdditionalTestArtifacts(testDevice)).thenReturn(
      mapOf("PreviewScreenshot.newImagePath" to "/path/to/newImage"))
    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("")
    view.setResults(testDevice, mockTestResults)

    view.myLogsView.waitAllRequests()

    assertThat(view.myScreenshotTab.isHidden).isFalse()
    assertThat(view.myScreenshotAttributesTab.isHidden).isFalse()
    assertThat(view.logsTab.isHidden).isFalse()
    assertThat(view.myLogsView.text).isEqualTo("No logs available")
    assertThat(view.myDeviceInfoTab.isHidden).isTrue()
  }

  @Test
  fun journeysResultsTabHiddenByDefault() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)

    assertThat(view.myJourneyScreenshotsTab.isHidden).isTrue()
  }

  @Test
  fun journeysResultsTabDisplayedWhenJourneyArtifactsExist() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getAdditionalTestArtifacts(testDevice)).thenReturn(
      mapOf(
        "Journeys.ActionPerformed.action1.screenshotPath" to "/path/to/screenshot.png",
        "Journeys.ActionPerformed.action1.description" to "The action taken",
        "Journeys.ActionPerformed.action1.modelReasoning" to "The reasoning behind the action"
      ))

    view.setResults(testDevice, mockTestResults)

    assertThat(view.myJourneyScreenshotsTab.isHidden).isFalse()
  }

  @Test
  fun logsTabIsSelectedWhenErrorProvidedAndUserHasNotYetSelectedATab() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("This is a test\n")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("error stack trace")
    whenever(mockTestResults.getTestCaseResult(testDevice)).thenReturn(null)
    whenever(mockTestResults.getBenchmark(testDevice)).thenReturn(BenchmarkOutput.Empty)

    view.setResults(testDevice, mockTestResults)

    assertThat(view.tabs.selectedInfo).isEqualTo(view.logsTab)
  }

  @Test
  fun logsAreAutomaticallyScrolledToTheEnd() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getLogcat(testDevice)).thenReturn("This is a test\n".repeat(100))
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("error stack trace")

    view.setResults(testDevice, mockTestResults)
    view.myLogsView.waitAllRequests()

    runInEdtAndWait {
      assertThat(view.myLogsView.editor?.caretModel?.logicalPosition?.line).isEqualTo(
        view.myLogsView.editor?.document?.lineCount?.minus(1) ?: -1)
    }
  }

  @Test
  fun logsTabIsNotSelectedWhenErrorProvidedAndUserHasAlreadySelectedADifferentTab() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    view.tabs.select(view.myDeviceInfoTab, false)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("error stack trace")

    view.refreshLogsView()
    view.setResults(testDevice, mockTestResults)

    assertThat(view.tabs.selectedInfo).isEqualTo(view.myDeviceInfoTab)
  }

  @Test
  fun journeysTabIsSelectedByDefaultWhenUserHasntSelectedATabYet() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    val testDevice = device("device id", "device name")
    whenever(mockTestResults.getAdditionalTestArtifacts(testDevice)).thenReturn(
      mapOf(
        "Journeys.ActionPerformed.action1.screenshotPath" to "/path/to/screenshot.png",
        "Journeys.ActionPerformed.action1.description" to "The action taken",
        "Journeys.ActionPerformed.action1.modelReasoning" to "The reasoning behind the action"
      ))
    // The Journeys tab should be selected even though there is an error stack trace
    whenever(mockTestResults.getErrorStackTrace(testDevice)).thenReturn("error stack trace")

    view.setResults(testDevice, mockTestResults)

    assertThat(view.tabs.selectedInfo).isEqualTo(view.myJourneyScreenshotsTab)
  }

  private fun device(id: String, name: String): AndroidDevice {
    return AndroidDevice(id, name, name, AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(29))
  }

  @Test
  fun `minimal console view test`() {
    runInEdtAndWait {
      // 1. Create a plain console view
      val console = ConsoleViewImpl(projectRule.project, true)
      Disposer.register(disposableRule.disposable, console)

      // Force the lazy initialization of the internal editor.
      console.component

      // 2. Print directly to it
      console.clear()
      console.print("Hello, World!", ConsoleViewContentType.NORMAL_OUTPUT)

      console.waitAllRequests()

      // 4. Assert
      assertThat(console.editor!!.document.text).isEqualTo("Hello, World!")
    }
  }
}