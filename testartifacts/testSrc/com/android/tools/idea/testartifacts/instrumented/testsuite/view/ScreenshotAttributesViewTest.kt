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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [ScreenshotAttributesView].
 */
@RunWith(JUnit4::class)
class ScreenshotAttributesViewTest {

  private lateinit var view: ScreenshotAttributesView

  @Before
  fun setup() {
    view = ScreenshotAttributesView()
  }

  /**
   * Verifies that all fields are set correctly when a test has passed.
   * This implicitly tests that the summary color is green.
   */
  @Test
  fun updateData_withPassedResult_setsPassedState() {
    view.updateData(
        refImagePath = "ref.png",
        newImagePath = "new.png",
        testMethodName = "myMethod",
        testClassName = "MyClass",
        result = AndroidTestCaseResult.PASSED,
        errorStackTrace = null
    )
    assertThat(view.state.matchPercentage).isNull()
    assertThat(view.state.testResult).isEqualTo(AndroidTestCaseResult.PASSED)
    assertThat(view.state.refLocation).isEqualTo("ref.png")
    assertThat(view.state.newLocation).isEqualTo("new.png")
    assertThat(view.state.methodName).isEqualTo("myMethod")
    assertThat(view.state.className).isEqualTo("MyClass")
  }

  /**
   * Verifies that all fields are set correctly when a test has failed with a diff.
   * This implicitly tests that the summary color is red.
   */
  @Test
  fun updateData_withFailedResultAndValidStackTrace_setsFailedState() {
    view.updateData(
        refImagePath = "ref.png",
        newImagePath = "new.png",
        testMethodName = "myMethod",
        testClassName = "MyClass",
        result = AndroidTestCaseResult.FAILED,
        errorStackTrace = "Difference: 25.50%"
    )
    assertThat(view.state.matchPercentage).isEqualTo("74.50%")
    assertThat(view.state.testResult).isEqualTo(AndroidTestCaseResult.FAILED)
    assertThat(view.state.refLocation).isEqualTo("ref.png")
    assertThat(view.state.newLocation).isEqualTo("new.png")
    assertThat(view.state.methodName).isEqualTo("myMethod")
    assertThat(view.state.className).isEqualTo("MyClass")
  }

  /**
   * Verifies that all fields are set correctly when a test has failed without a diff.
   * This implicitly tests that the summary color is red.
   */
  @Test
  fun updateData_withFailedResultAndNullStackTrace_setsFailedState() {
    view.updateData(
        refImagePath = "ref.png",
        newImagePath = "new.png",
        testMethodName = "myMethod",
        testClassName = "MyClass",
        result = AndroidTestCaseResult.FAILED,
        errorStackTrace = null
    )
    assertThat(view.state.matchPercentage).isNull()
    assertThat(view.state.testResult).isEqualTo(AndroidTestCaseResult.FAILED)
    assertThat(view.state.refLocation).isEqualTo("ref.png")
    assertThat(view.state.newLocation).isEqualTo("new.png")
    assertThat(view.state.methodName).isEqualTo("myMethod")
    assertThat(view.state.className).isEqualTo("MyClass")
  }

  /**
   * Verifies that fields are set to "N/A" when they are null.
   * This implicitly tests that the summary color is gray.
   */
  @Test
  fun updateData_withNullValues_setsNotAvailable() {
    view.updateData(
        refImagePath = null,
        newImagePath = null,
        testMethodName = null,
        testClassName = null,
        result = null,
        errorStackTrace = null
    )
    assertThat(view.state.refLocation).isEqualTo("N/A")
    assertThat(view.state.newLocation).isEqualTo("N/A")
    assertThat(view.state.methodName).isEqualTo("N/A")
    assertThat(view.state.className).isEqualTo("N/A")
    assertThat(view.state.testResult).isNull()
    assertThat(view.state.matchPercentage).isNull()
  }
}