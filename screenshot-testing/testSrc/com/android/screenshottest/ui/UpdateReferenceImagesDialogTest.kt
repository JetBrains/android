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

import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UpdateReferenceImagesDialogTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private var dialog: UpdateReferenceImagesDialog? = null

  @After
  fun tearDown() {
    runInEdtAndWait {
      dialog?.close(DialogWrapper.CANCEL_EXIT_CODE)
    }
  }

  @Test
  fun testTreePopulation() = runInEdtAndWait {
    dialog = UpdateReferenceImagesDialog(projectRule.project)

    val details = PreviewDetails(
      testId = "id",
      className = "com.example.TestClass",
      methodName = "testMethod",
      previewName = "preview1",
      testResult = AndroidTestCaseResult.PASSED,
      srcImagePath = "/tmp/fake.png"
    )

    dialog?.updateDialogWithTestResult(details, isChecked = true)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val tree = findTree(dialog!!)
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
    dialog = UpdateReferenceImagesDialog(projectRule.project)
    // Initially OK disabled
    assertFalse("OK button should be disabled initially", dialog!!.isOKActionEnabled)

    val details = PreviewDetails(
      testId = "id",
      className = "com.example.TestClass",
      methodName = "testMethod",
      previewName = "preview1",
      testResult = AndroidTestCaseResult.PASSED,
      srcImagePath = "/tmp/fake.png"
    )

    dialog?.updateDialogWithTestResult(details, isChecked = true)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Still disabled because suite not finished
    assertFalse("OK button should remain disabled until suite finishes", dialog!!.isOKActionEnabled)

    dialog?.onTestSuiteFinished()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Now enabled
    assertTrue("OK button should be enabled after suite finishes", dialog!!.isOKActionEnabled)
  }

  @Test
  fun testSelectionUpdatesOkButton() = runInEdtAndWait {
    dialog = UpdateReferenceImagesDialog(projectRule.project)
    val details = PreviewDetails(
      testId = "id",
      className = "TestClass",
      methodName = "testMethod",
      previewName = "preview1",
      testResult = AndroidTestCaseResult.PASSED,
      srcImagePath = "/tmp/fake.png"
    )

    dialog?.updateDialogWithTestResult(details, isChecked = true)
    dialog?.onTestSuiteFinished()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertTrue("OK enabled initially", dialog!!.isOKActionEnabled)

    // Uncheck the node
    val tree = findTree(dialog!!)
    val root = tree.model.root as CheckedTreeNode
    val classNode = root.firstChild as CheckedTreeNode
    val methodNode = classNode.firstChild as CheckedTreeNode
    val previewNode = methodNode.firstChild as CheckedTreeNode

    previewNode.isChecked = false

    // Manually trigger updateOkButtonState since we modify CheckedTreeNode directly
    callUpdateOkButtonState(dialog!!)

    assertFalse("OK disabled after uncheck", dialog!!.isOKActionEnabled)

    previewNode.isChecked = true
    callUpdateOkButtonState(dialog!!)

    assertTrue("OK enabled after re-check", dialog!!.isOKActionEnabled)
  }

  @Test
  fun testNoTestsDiscovered() = runInEdtAndWait {
    dialog = UpdateReferenceImagesDialog(projectRule.project)
    // No tests added

    // Handle expected error dialog
    TestDialogManager.setTestDialog(TestDialog.OK)

    try {
        dialog?.onTestSuiteFinished()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        // Verify dialog was closed (exit code should be CANCEL)
        assertEquals(DialogWrapper.CANCEL_EXIT_CODE, dialog!!.exitCode)
    } finally {
        TestDialogManager.setTestDialog(TestDialog.DEFAULT)
    }
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
}