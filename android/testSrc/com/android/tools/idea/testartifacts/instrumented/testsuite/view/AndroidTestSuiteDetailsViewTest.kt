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

import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultStats
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
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

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun setAndroidTestResultsShouldUpdateUiComponents() {
    val view = AndroidTestSuiteDetailsView(disposableRule.disposable, mockController, mockListener, projectRule.project)

    view.setAndroidTestResults(createTestResults("method1", "class1", AndroidTestCaseResult.PASSED))

    assertThat(view.titleTextViewForTesting.text).isEqualTo("class1.method1")
  }

  @Test
  fun clickOnCloseButtonShouldInvokeListener() {
    val view = AndroidTestSuiteDetailsView(disposableRule.disposable, mockController, mockListener, projectRule.project)

    view.closeButtonForTesting.doClick()

    verify(mockListener).onAndroidTestSuiteDetailsViewCloseButtonClicked()
  }

  private fun createTestResults(methodName: String, className: String, testCaseResult: AndroidTestCaseResult): AndroidTestResults {
    return object: AndroidTestResults {
      override val methodName: String = methodName
      override val className: String = className
      override val packageName: String = ""
      override fun getTestCaseResult(device: AndroidDevice): AndroidTestCaseResult? = testCaseResult
      override fun getTestResultSummary(): AndroidTestCaseResult = testCaseResult
      override fun getTestResultSummaryText(): String = ""
      override fun getResultStats() = AndroidTestResultStats()
      override fun getResultStats(device: AndroidDevice) = AndroidTestResultStats()
      override fun getLogcat(device: AndroidDevice): String = ""
      override fun getErrorStackTrace(device: AndroidDevice): String = ""
      override fun getBenchmark(device: AndroidDevice): String = ""
      override fun getRetentionSnapshot(device: AndroidDevice): File? = null
    }
  }
}