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
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.AndroidTestSuiteLogger
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark.BenchmarkOutput
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.ParallelAndroidTestReportUiEvent
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import java.io.File

/**
 * Unit tests for [DetailsViewContentView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class DetailsViewContentViewTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  @Mock lateinit var mockLogger: AndroidTestSuiteLogger

  @get:Rule val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun testNoRetentionView() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    view.setAndroidTestCaseResult(AndroidTestCaseResult.PASSED)
    view.setAndroidDevice(device("device id", "device name"))
    assertThat(view.myRetentionTab.isHidden).isTrue()
  }

  @Test
  fun testWithRetentionView() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    view.setAndroidTestCaseResult(AndroidTestCaseResult.FAILED)
    view.setAndroidDevice(device("device id", "device name"))
    view.setRetentionSnapshot(File("foo"))
    assertThat(view.myRetentionTab.isHidden).isFalse()
  }

  @Test
  fun testResultLabelOnPassing() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    view.setAndroidTestCaseResult(AndroidTestCaseResult.PASSED)
    view.setAndroidDevice(device("device id", "device name"))
    assertThat(view.myTestResultLabel.text)
      .isEqualTo("<html><font color='#6cad74'>Passed</font> on device name</html>")
  }

  @Test
  fun testResultLabelOnFailing() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    view.setAndroidTestCaseResult(AndroidTestCaseResult.FAILED)
    view.setAndroidDevice(device("device id", "device name"))
    assertThat(view.myTestResultLabel.text).isEqualTo(
      "<html><font color='#b81708'>Failed</font> on device name</html>")
  }

  @Test
  fun testResultLabelOnFailingWithErrorStackTrace() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    view.setAndroidTestCaseResult(AndroidTestCaseResult.FAILED)
    view.setAndroidDevice(device("device id", "device name"))
    view.setErrorStackTrace("ErrorStackTrace")
    assertThat(view.myTestResultLabel.text).isEqualTo(
      "<html><font size='+1'>ErrorStackTrace</font><br><font color='#b81708'>Failed</font> on device name</html>")
  }

  @Test
  fun testResultLabelHtmlEscaping() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    view.setAndroidTestCaseResult(AndroidTestCaseResult.FAILED)
    view.setAndroidDevice(device("device id", "<device name>"))
    view.setErrorStackTrace("<ErrorStackTrace>")
    assertThat(view.myTestResultLabel.text).isEqualTo(
      "<html><font size='+1'>&lt;ErrorStackTrace&gt;</font><br><font color='#b81708'>Failed</font> on &lt;device name&gt;</html>")
  }

  @Test
  fun testResultLabelOnRunning() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    view.setAndroidTestCaseResult(AndroidTestCaseResult.IN_PROGRESS)
    view.setAndroidDevice(device("device id", "device name"))
    assertThat(view.myTestResultLabel.text).isEqualTo("Running on device name")
  }

  @Test
  fun testResultLabelNoTestStatus() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    view.setAndroidTestCaseResult(null)
    view.setAndroidDevice(device("device id", "device name"))
    assertThat(view.myTestResultLabel.text).isEqualTo("No test status available on device name")
  }

  @Test
  fun logsView() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)

    view.setLogcat("test logcat message")
    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\n")
  }

  @Test
  fun logsViewWithErrorStackTrace() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)

    view.setLogcat("test logcat message")
    view.setErrorStackTrace("error stack trace")
    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\nerror stack trace")
  }

  @Test
  fun logsViewWithNoLogsAndErrorStackTrace() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)

    view.setLogcat("")
    view.setErrorStackTrace("error stack trace")
    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("error stack trace")
  }

  @Test
  fun logsViewWithNoMessage() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)

    view.setLogcat("")
    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("")
    assertThat(view.logsTab.isHidden).isTrue()
  }

  @Test
  fun logsViewShouldClearPreviousMessage() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)

    view.setLogcat("test logcat message")
    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\n")

    view.setLogcat("test logcat message 2")
    view.myLogsView.waitAllRequests()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message 2\n")
  }

  @Test
  fun logsViewShouldShouldNotRefreshWhenMessageUnchanged() {
    val view = spy(DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger))

    view.setLogcat("test logcat message")
    view.setLogcat("test logcat message")
    view.myLogsView.waitAllRequests()

    verify(view, times(1)).refreshLogsView()
    assertThat(view.myLogsView.text).isEqualTo("test logcat message\n")
  }

  @Test
  fun benchmarkTab() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)

    view.setBenchmarkText(BenchmarkOutput("test benchmark message"))
    view.myBenchmarkView.waitAllRequests()

    assertThat(view.myBenchmarkView.text).isEqualTo("test benchmark message\n")
    assertThat(view.myBenchmarkTab.isHidden).isFalse()
  }

  @Test
  fun benchmarkTabIsHiddenIfNoOutput() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)

    view.setBenchmarkText(BenchmarkOutput.Empty)
    view.myBenchmarkView.waitAllRequests()

    assertThat(view.myBenchmarkView.text).isEqualTo("")
    assertThat(view.myBenchmarkTab.isHidden).isTrue()
  }

  @Test
  fun logging() {
    val view = DetailsViewContentView(disposableRule.disposable, projectRule.project, mockLogger)
    verify(mockLogger).addImpressionWhenDisplayed(view.myLogsView,
                                                  ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_LOG_VIEW)
    verify(mockLogger).addImpressionWhenDisplayed(view.myDeviceInfoTableView.getComponent(),
                                                  ParallelAndroidTestReportUiEvent.UiElement.TEST_SUITE_DEVICE_INFO_VIEW)
  }

  private fun device(id: String, name: String): AndroidDevice {
    return AndroidDevice(id, name, name, AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(29))
  }
}