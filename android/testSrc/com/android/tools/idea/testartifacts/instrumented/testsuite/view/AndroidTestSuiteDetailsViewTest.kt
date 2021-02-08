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
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark.BenchmarkOutput
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultStats
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.AndroidTestSuiteLogger
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteDetailsView.AndroidTestSuiteDetailsViewListener
import com.google.common.truth.Truth.assertThat
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
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import java.io.File
import java.time.Duration

/**
 * Unit tests for [AndroidTestSuiteDetailsView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class AndroidTestSuiteDetailsViewTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)

  @Mock lateinit var mockController: AndroidTestSuiteViewController
  @Mock lateinit var mockListener: AndroidTestSuiteDetailsViewListener
  @Mock lateinit var mockLogger: AndroidTestSuiteLogger

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun setAndroidTestResultsShouldUpdateUiComponents() {
    val view = AndroidTestSuiteDetailsView(disposableRule.disposable, mockController, mockListener, projectRule.project, mockLogger)
    view.addDevice(AndroidDevice("id", "deviceName", "deviceName", AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(28)))
    view.setAndroidTestResults(createTestResults(AndroidTestCaseResult.PASSED))

    assertThat(view.titleTextView.text).isEqualTo("packageName.className.methodName")
    assertThat(view.contentView.myTestResultLabel.text)
      .isEqualTo("<html><font color='#6cad74'>Passed</font> on deviceName</html>")
  }

  @Test
  fun setAndroidTestResultsShouldUpdateUiComponentsNoTestResultAvailable() {
    val view = AndroidTestSuiteDetailsView(disposableRule.disposable, mockController, mockListener, projectRule.project, mockLogger)
    view.addDevice(AndroidDevice("id", "deviceName", "deviceName", AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(28)))

    view.setAndroidTestResults(createTestResults(null))

    assertThat(view.titleTextView.text).isEqualTo("packageName.className.methodName")
    assertThat(view.contentView.myTestResultLabel.text).isEqualTo("No test status available on deviceName")
  }

  @Test
  fun setAndroidTestResultsWithNoMethodName() {
    val view = AndroidTestSuiteDetailsView(disposableRule.disposable, mockController, mockListener, projectRule.project, mockLogger)
    view.setAndroidTestResults(createTestResults(AndroidTestCaseResult.PASSED, ""))

    assertThat(view.titleTextView.text).isEqualTo("packageName.className")
  }

  @Test
  fun setAndroidTestResultsWithNoClassName() {
    val view = AndroidTestSuiteDetailsView(disposableRule.disposable, mockController, mockListener, projectRule.project, mockLogger)
    view.setAndroidTestResults(createTestResults(AndroidTestCaseResult.PASSED, "", "", ""))

    assertThat(view.titleTextView.text).isEqualTo("Test Results")
  }

  @Test
  fun clickOnCloseButtonShouldInvokeListener() {
    val view = AndroidTestSuiteDetailsView(disposableRule.disposable, mockController, mockListener, projectRule.project, mockLogger)

    view.closeButton.doClick()

    verify(mockListener).onAndroidTestSuiteDetailsViewCloseButtonClicked()
  }

  private fun createTestResults(testCaseResult: AndroidTestCaseResult?,
                                methodName: String = "methodName",
                                className: String = "className",
                                packageName: String = "packageName"): AndroidTestResults {
    return object: AndroidTestResults {
      override val methodName: String = methodName
      override val className: String = className
      override val packageName: String = packageName
      override fun getTestCaseResult(device: AndroidDevice): AndroidTestCaseResult? = testCaseResult
      override fun getTestResultSummary(): AndroidTestCaseResult = testCaseResult ?: AndroidTestCaseResult.SCHEDULED
      override fun getTestResultSummaryText(): String = ""
      override fun getResultStats() = AndroidTestResultStats()
      override fun getResultStats(device: AndroidDevice) = AndroidTestResultStats()
      override fun getLogcat(device: AndroidDevice): String = ""
      override fun getStartTime(device: AndroidDevice): Long? = null
      override fun getDuration(device: AndroidDevice): Duration = Duration.ZERO
      override fun getTotalDuration(): Duration = Duration.ZERO
      override fun getErrorStackTrace(device: AndroidDevice): String = ""
      override fun getBenchmark(device: AndroidDevice): BenchmarkOutput = BenchmarkOutput.Empty
      override fun getRetentionInfo(device: AndroidDevice): File? = null
      override fun getRetentionSnapshot(device: AndroidDevice): File? = null
    }
  }
}