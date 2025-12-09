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
package com.android.screenshottest.listener

import com.android.screenshottest.ui.PreviewDetails
import com.android.screenshottest.ui.UpdateReferenceImagesDialog
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@RunsInEdt
class UpdateScreenshotTestResultsListenerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val edtRule = EdtRule()

  /**
   * Verifies that the listener correctly extracts all fields from the AndroidTestCase
   * when all relevant artifacts are present.
   */
  @Test
  fun testOnTestCaseFinished_extractsDataCorrectly() {
    val dialog = mock(UpdateReferenceImagesDialog::class.java)
    val listener = UpdateScreenshotTestResultsListener(dialog)

    val mockDevice = mock(AndroidDevice::class.java)
    val mockSuite = mock(AndroidTestSuite::class.java)

    val artifacts = mutableMapOf(
        "PreviewScreenshot.methodName" to "testMethod",
        "PreviewScreenshot.previewName" to "preview1",
        "PreviewScreenshot.refImagePath" to "/path/to/ref.png",
        "PreviewScreenshot.newImagePath" to "/path/to/new.png",
        "PreviewScreenshot.diffImagePath" to "/path/to/diff.png",
        "PreviewScreenshot.diffPercent" to "0.05"
    )

    val testCase = AndroidTestCase(
        id = "test1",
        methodName = "ignoredMethodName",
        className = "com.example.TestClass",
        packageName = "com.example",
        result = AndroidTestCaseResult.PASSED,
        additionalTestArtifacts = artifacts
    )

    listener.onTestCaseFinished(mockDevice, mockSuite, testCase)

    // Flush EDT to ensure invokeLater block runs
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Capture argument
    val captor = ArgumentCaptor.forClass(PreviewDetails::class.java)
    verify(dialog).updateDialogWithTestResult(capturePreviewDetails(captor), ArgumentMatchers.eq(true))

    val details = captor.value
    assertEquals("com.example.TestClass.testMethod.preview1", details.testId)
    assertEquals("com.example.TestClass", details.className)
    assertEquals("testMethod", details.methodName)
    assertEquals("preview1", details.previewName)
    assertEquals("/path/to/ref.png", details.destImagePath)
    assertEquals("/path/to/new.png", details.srcImagePath)
    assertEquals("/path/to/diff.png", details.diffImagePath)
    assertEquals("0.05", details.diffPercent)
  }

  /**
   * Verifies that the listener handles cases where the artifacts map is empty,
   * providing safe default values instead of crashing or returning nulls where strings are expected.
   */
  @Test
  fun testOnTestCaseFinished_handlesMissingArtifacts() {
    val dialog = mock(UpdateReferenceImagesDialog::class.java)
    val listener = UpdateScreenshotTestResultsListener(dialog)

    val mockDevice = mock(AndroidDevice::class.java)
    val mockSuite = mock(AndroidTestSuite::class.java)

    val testCase = AndroidTestCase(
        id = "test1",
        methodName = "ignoredMethodName",
        className = "com.example.TestClass",
        packageName = "com.example",
        result = AndroidTestCaseResult.FAILED,
        additionalTestArtifacts = mutableMapOf()
    )

    listener.onTestCaseFinished(mockDevice, mockSuite, testCase)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val captor = ArgumentCaptor.forClass(PreviewDetails::class.java)
    verify(dialog).updateDialogWithTestResult(capturePreviewDetails(captor), ArgumentMatchers.eq(true))

    val details = captor.value
    // Check default values
    assertEquals(" ", details.methodName)
    assertEquals(" ", details.previewName)
    assertEquals("com.example.TestClass. . ", details.testId)
    assertEquals(null, details.destImagePath)
  }

  /**
   * Verifies that the listener correctly extracts available data even when some artifacts are missing.
   * This ensures robustness against partial data.
   */
  @Test
  fun testOnTestCaseFinished_partialArtifacts() {
    val dialog = mock(UpdateReferenceImagesDialog::class.java)
    val listener = UpdateScreenshotTestResultsListener(dialog)
    val mockDevice = mock(AndroidDevice::class.java)
    val mockSuite = mock(AndroidTestSuite::class.java)

    // Only method name and one image path are present
    val artifacts = mutableMapOf(
        "PreviewScreenshot.methodName" to "partialMethod",
        "PreviewScreenshot.refImagePath" to "/path/to/ref.png"
    )

    val testCase = AndroidTestCase(
        id = "test_partial",
        methodName = "ignored",
        className = "com.example.PartialClass",
        packageName = "com.example",
        result = AndroidTestCaseResult.PASSED,
        additionalTestArtifacts = artifacts
    )

    listener.onTestCaseFinished(mockDevice, mockSuite, testCase)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val captor = ArgumentCaptor.forClass(PreviewDetails::class.java)
    verify(dialog).updateDialogWithTestResult(capturePreviewDetails(captor), ArgumentMatchers.eq(true))

    val details = captor.value
    assertEquals("partialMethod", details.methodName)
    assertEquals(" ", details.previewName) // Default
    assertEquals("/path/to/ref.png", details.destImagePath)
    assertEquals(null, details.srcImagePath) // Missing
    assertEquals("com.example.PartialClass.partialMethod. ", details.testId)
  }

  /**
   * Verifies that the test result status (PASSED, FAILED, etc.) is correctly propagated to the PreviewDetails.
   */
  @Test
  fun testOnTestCaseFinished_propagatesTestResult() {
    val dialog = mock(UpdateReferenceImagesDialog::class.java)
    val listener = UpdateScreenshotTestResultsListener(dialog)
    val mockDevice = mock(AndroidDevice::class.java)
    val mockSuite = mock(AndroidTestSuite::class.java)

    val testCase = AndroidTestCase(
        id = "test_result_check",
        methodName = "ignored",
        className = "com.example.ResultClass",
        packageName = "com.example",
        result = AndroidTestCaseResult.SKIPPED,
        additionalTestArtifacts = mutableMapOf()
    )

    listener.onTestCaseFinished(mockDevice, mockSuite, testCase)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val captor = ArgumentCaptor.forClass(PreviewDetails::class.java)
    verify(dialog).updateDialogWithTestResult(capturePreviewDetails(captor), ArgumentMatchers.eq(true))

    val details = captor.value
    assertEquals(AndroidTestCaseResult.SKIPPED, details.testResult)
  }

  /**
   * Verifies that onTestSuiteFinished is correctly delegated to the dialog.
   */
  @Test
  fun testOnTestSuiteFinished() {
    val dialog = mock(UpdateReferenceImagesDialog::class.java)
    val listener = UpdateScreenshotTestResultsListener(dialog)
    val mockDevice = mock(AndroidDevice::class.java)
    val mockSuite = mock(AndroidTestSuite::class.java)

    listener.onTestSuiteFinished(mockDevice, mockSuite)

    // Flush EDT to ensure invokeLater block from listener runs before verification.
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    verify(dialog).onTestSuiteFinished()
  }

  /**
   * Helper function to capture non-nullable arguments with Mockito in Kotlin.
   * Returns a dummy instance to satisfy Kotlin's null-safety during the stubbing phase,
   * while Mockito captures the actual value.
   */
  private fun capturePreviewDetails(captor: ArgumentCaptor<PreviewDetails>): PreviewDetails {
    captor.capture()
    return PreviewDetails(testId="", className="", methodName="", previewName="")
  }
}
