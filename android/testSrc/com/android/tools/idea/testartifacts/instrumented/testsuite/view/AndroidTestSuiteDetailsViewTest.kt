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

import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteDetailsView.AndroidTestSuiteDetailsViewListener
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockApplication
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

/**
 * Unit tests for [AndroidTestSuiteDetailsView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class AndroidTestSuiteDetailsViewTest {

  @get:Rule val edtRule = EdtRule()
  @get:Rule val disposableRule = DisposableRule()

  @Mock lateinit var mockListener: AndroidTestSuiteDetailsViewListener

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
    MockApplication.setUp(disposableRule.disposable)
  }

  @Test
  fun setAndroidTestResultsShouldUpdateUiComponents() {
    val view = AndroidTestSuiteDetailsView(disposableRule.disposable, mockListener)

    view.setAndroidTestResults(createTestResults("testName", AndroidTestCaseResult.PASSED))

    assertThat(view.titleTextViewForTesting.text).isEqualTo("testName")
  }

  @Test
  fun clickOnCloseButtonShouldInvokeListener() {
    val view = AndroidTestSuiteDetailsView(disposableRule.disposable, mockListener)

    view.closeButtonForTesting.doClick()

    verify(mockListener).onAndroidTestSuiteDetailsViewCloseButtonClicked()
  }

  private fun createTestResults(testCaseName: String, testCaseResult: AndroidTestCaseResult?): AndroidTestResults {
    return object: AndroidTestResults {
      override val testCaseName = testCaseName
      override fun getTestCaseResult(device: AndroidDevice) = testCaseResult
    }
  }
}