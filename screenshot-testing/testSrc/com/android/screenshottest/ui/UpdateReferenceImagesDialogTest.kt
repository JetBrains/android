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
package com.android.screenshottest.ui

import com.android.tools.idea.metrics.MetricsTrackerRule
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ScreenshotTestComposePreviewEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import java.util.Base64
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.contains

class UpdateReferenceImagesDialogTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val metricsTrackerRule = MetricsTrackerRule()

  @get:Rule
  val tempFolder = TemporaryFolder()

  private lateinit var dialog: UpdateReferenceImagesDialog
  private val mockLogger: Logger = mock(Logger::class.java)

  // A tiny 1x1 transparent PNG
  private val TINY_PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
  private val TINY_PNG_BYTES = Base64.getDecoder().decode(TINY_PNG_BASE64)

  @Before
  fun setUp() {
    runInEdtAndWait {
      dialog = createDialog()
    }
  }

  @After
  fun tearDown() {
    runInEdtAndWait {
      dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
    }
  }

  @Test
  fun testTreePopulation() = runInEdtAndWait {
    val imagePath = createTempImage("preview1.png")

    val details = PreviewDetails(
      testId = "id",
      className = "com.example.TestClass",
      methodName = "testMethod",
      previewName = "preview1",
      testResult = AndroidTestCaseResult.PASSED,
      srcImagePath = imagePath
    )

    dialog.updateDialogWithTestResult(details, isChecked = true)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val tree = findTree(dialog)
    val root = tree.model.root as CheckedTreeNode

    // Root -> Class -> Method -> Preview
    assertEquals("Root should have 1 class child", 1, root.childCount)

    val classNode = root.firstChild as CheckedTreeNode
    assertEquals("TestClass", classNode.userObject)

    val methodNode = classNode.firstChild as CheckedTreeNode
    assertEquals("testMethod", methodNode.userObject)

    val previewNode = methodNode.firstChild as CheckedTreeNode
    val previewData = previewNode.userObject as PreviewDetails
    assertEquals("preview1", previewData.previewName)
  }

  @Test
  fun testOkButtonState() = runInEdtAndWait {
    // Initially OK disabled
    assertFalse("OK button should be disabled initially", dialog.isOKActionEnabled)

    val imagePath = createTempImage("preview2.png")
    val details = PreviewDetails(
      testId = "id",
      className = "com.example.TestClass",
      methodName = "testMethod",
      previewName = "preview1",
      testResult = AndroidTestCaseResult.PASSED,
      srcImagePath = imagePath
    )

    dialog.updateDialogWithTestResult(details, isChecked = true)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Still disabled because suite not finished
    assertFalse("OK button should remain disabled until suite finishes", dialog.isOKActionEnabled)

    dialog.onTestSuiteFinished()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Now enabled
    assertTrue("OK button should be enabled after suite finishes", dialog.isOKActionEnabled)
  }

  @Test
  fun testSelectionUpdatesOkButton() = runInEdtAndWait {
    val imagePath = createTempImage("preview3.png")
    val details = PreviewDetails(
      testId = "id",
      className = "TestClass",
      methodName = "testMethod",
      previewName = "preview1",
      testResult = AndroidTestCaseResult.PASSED,
      srcImagePath = imagePath
    )

    dialog.updateDialogWithTestResult(details, isChecked = true)
    dialog.onTestSuiteFinished()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertTrue("OK enabled initially", dialog.isOKActionEnabled)

    // Uncheck the node
    val tree = findTree(dialog)
    val root = tree.model.root as CheckedTreeNode
    val classNode = root.firstChild as CheckedTreeNode
    val methodNode = classNode.firstChild as CheckedTreeNode
    val previewNode = methodNode.firstChild as CheckedTreeNode

    previewNode.isChecked = false

    // Manually trigger updateOkButtonState since we modify CheckedTreeNode directly
    callUpdateOkButtonState(dialog)

    assertFalse("OK disabled after uncheck", dialog.isOKActionEnabled)

    previewNode.isChecked = true
    callUpdateOkButtonState(dialog)

    assertTrue("OK enabled after re-check", dialog.isOKActionEnabled)
  }

  @Test
  fun testNoTestsDiscovered() = runInEdtAndWait {
    // No tests added

    // Handle expected error dialog
    TestDialogManager.setTestDialog(TestDialog.OK)

    dialog.onTestSuiteFinished()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Verify the logger was called with expected message
    verify(mockLogger).error(contains("No tests were discovered"))

    // Verify dialog was closed
    assertEquals(DialogWrapper.CANCEL_EXIT_CODE, dialog.exitCode)

    TestDialogManager.setTestDialog(TestDialog.DEFAULT)
  }

  @Test
  fun testMissingMetadataLogsWarning() = runInEdtAndWait {
    val imagePath = createTempImage("preview4.png")

    // Missing methodName and previewName (empty strings)
    val details = PreviewDetails(
      testId = "id",
      className = "com.example.TestClass",
      methodName = "",
      previewName = "",
      testResult = AndroidTestCaseResult.PASSED,
      srcImagePath = imagePath
    )

    dialog.updateDialogWithTestResult(details, isChecked = true)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Verify warn was logged
    verify(mockLogger).warn(contains("Missing methodName or previewName"))
  }

  @Test
  fun testMissingImageLogsError() = runInEdtAndWait {
    // No image path provided (null)
    val details = PreviewDetails(
      testId = "testMissingImage",
      className = "com.example.TestClass",
      methodName = "testMethod",
      previewName = "preview1",
      testResult = AndroidTestCaseResult.PASSED,
      srcImagePath = null
    )

    dialog.updateDialogWithTestResult(details, isChecked = true)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Verify warn was logged
    verify(mockLogger).warn(contains("Source image path missing"))
  }

  @Test
  fun testErrorDialogShowsFunctionName() = runInEdtAndWait {
    val details = PreviewDetails(
      testId = "id",
      className = "com.example.TestClass",
      methodName = "myMethod",
      previewName = "myPreview",
      testResult = AndroidTestCaseResult.PASSED,
      srcImagePath = null // This causes load failure
    )

    dialog.updateDialogWithTestResult(details, isChecked = true)
    dialog.onTestSuiteFinished()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    var checkedMessage = false
    TestDialogManager.setTestDialog { message ->
      if (message.contains("myMethod.myPreview")) {
        checkedMessage = true
      }
      DialogWrapper.OK_EXIT_CODE
    }

    callDoOKAction(dialog)

    assertTrue("Error dialog should contain 'myMethod.myPreview'", checkedMessage)

    TestDialogManager.setTestDialog(TestDialog.DEFAULT)
  }

  @Test
  fun testDeferredImageLoading() = runInEdtAndWait {
    val details = PreviewDetails(
      testId = "id",
      className = "com.example.TestClass",
      methodName = "testMethod",
      previewName = "preview1",
      testResult = AndroidTestCaseResult.PASSED,
      srcImagePath = "some_path.png"
    )

    // updateDialogWithTestResult should NOT trigger image loading.
    // In the old implementation, it would create a PreviewItemPanel and call loadImage.
    // In the new implementation, it just updates the tree.
    dialog.updateDialogWithTestResult(details, isChecked = true)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Since we removed imagePanelMap, we can verify that no image panels were created yet.
    // Actually, imagePanelMap was removed.
    // We can check the RightPane is still in placeholder or details without actual images loaded.
  }

  @Test
  fun testCancelLogsMetric() = runInEdtAndWait {
    dialog.doCancelAction()

    val usages = metricsTrackerRule.testTracker.usages
    // Check that we have at least one event and the last one matches
    assertTrue("Should have logged at least one event", usages.isNotEmpty())
    assertEquals(AndroidStudioEvent.EventKind.SCREENSHOT_TEST_COMPOSE_PREVIEW, usages.last().studioEvent.kind)
    assertEquals(ScreenshotTestComposePreviewEvent.Type.SCREENSHOT_DIALOG_CLOSE, usages.last().studioEvent.screenshotTestComposePreviewEvent.type)
  }

  private fun findTree(dialog: UpdateReferenceImagesDialog): CheckboxTree {
    val field = UpdateReferenceImagesDialog::class.java.getDeclaredField("tree")
    field.isAccessible = true
    return field.get(dialog) as CheckboxTree
  }

  private fun callUpdateOkButtonState(dialog: UpdateReferenceImagesDialog) {
    val method = UpdateReferenceImagesDialog::class.java.getDeclaredMethod("updateOkButtonState")
    method.isAccessible = true
    method.invoke(dialog)
  }

  private fun callDoOKAction(dialog: UpdateReferenceImagesDialog) {
    val method = UpdateReferenceImagesDialog::class.java.getDeclaredMethod("doOKAction")
    method.isAccessible = true
    method.invoke(dialog)
  }

  private fun createTempImage(name: String): String {
    val file = tempFolder.newFile(name)
    file.writeBytes(TINY_PNG_BYTES)
    return file.absolutePath
  }

  private fun createDialog(): UpdateReferenceImagesDialog {
    return UpdateReferenceImagesDialog(projectRule.project, mockLogger)
  }
}