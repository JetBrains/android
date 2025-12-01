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

import androidx.compose.ui.awt.ComposePanel
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.ImageWithToolbarPanel
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.ScreenshotViewType
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.awt.Component
import java.awt.Container
import javax.swing.JPanel
import javax.swing.JScrollPane

class PreviewDetailsPanelTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testSwitchToSinglePreview() = runInEdtAndWait {
    val panel = PreviewDetailsPanel()
    val details = PreviewDetails(
      testId = "test1",
      className = "Class",
      methodName = "method",
      previewName = "preview",
      testResult = AndroidTestCaseResult.PASSED
    )
    val toolbar = ComposePanel()

    panel.displayPreviews(
      listOf(details),
      emptyMap(),
      ScreenshotViewType.NEW,
      toolbar
    )

    val visibleComponents = panel.components.filter { it.isVisible }
    assertEquals(1, visibleComponents.size)

    val activePanel = visibleComponents[0] as JPanel
    // Check for structure specific to single preview (OnePixelSplitter)
    val hasSplitter = activePanel.components.any { OnePixelSplitter::class.java.isInstance(it) }
    assertTrue("Single preview should contain a OnePixelSplitter", hasSplitter)
  }

  @Test
  fun testSwitchToMultiplePreviews() = runInEdtAndWait {
    val panel = PreviewDetailsPanel()
    val details1 = PreviewDetails(
      testId = "test1",
      className = "Class",
      methodName = "method",
      previewName = "preview1",
      testResult = AndroidTestCaseResult.PASSED
    )
    val details2 = PreviewDetails(
      testId = "test2",
      className = "Class",
      methodName = "method",
      previewName = "preview2",
      testResult = AndroidTestCaseResult.PASSED
    )

    val toolbar = ComposePanel()

    panel.displayPreviews(
      listOf(details1, details2),
      emptyMap(),
      ScreenshotViewType.NEW,
      toolbar
    )

    val visibleComponents = panel.components.filter { it.isVisible }
    assertEquals(1, visibleComponents.size)

    val activePanel = visibleComponents[0] as JPanel
    // Check for structure specific to multiple preview (JBScrollPane)
    val hasScrollPane = activePanel.components.any { it is JBScrollPane }
    assertTrue("Multiple preview should contain a JBScrollPane", hasScrollPane)
  }

  @Test
  fun testMultiplePreviewsWithSingleItemAndNoToolbar() = runInEdtAndWait {
    val panel = PreviewDetailsPanel()
    val details = PreviewDetails(
      testId = "test1",
      className = "Class",
      methodName = "method",
      previewName = "preview",
      testResult = AndroidTestCaseResult.PASSED
    )

    panel.displayPreviews(
      listOf(details),
      emptyMap(),
      ScreenshotViewType.NEW,
      null
    )

    val visibleComponents = panel.components.filter { it.isVisible }
    assertEquals(1, visibleComponents.size)

    val activePanel = visibleComponents[0] as JPanel
    // Should be multiple view (JBScrollPane)
    val hasScrollPane = activePanel.components.any { it is JBScrollPane }
    assertTrue("Should fallback to multiple preview (JBScrollPane) when no toolbar", hasScrollPane)
  }

  @Test
  fun testPlaceholdersForNullPaths() = runInEdtAndWait {
    val panel = PreviewDetailsPanel()
    val details = PreviewDetails(
      testId = "test1",
      className = "Class",
      methodName = "method",
      previewName = "preview",
      testResult = AndroidTestCaseResult.PASSED,
      srcImagePath = null,
      diffImagePath = null,
      destImagePath = null
    )
    val toolbar = ComposePanel()

    panel.displayPreviews(
      listOf(details),
      emptyMap(),
      ScreenshotViewType.ALL, // Use ALL to check all 3 panels
      toolbar
    )

    // Find the ImageWithToolbarPanels
    val newPanel = findImagePanel(panel, ScreenshotViewType.NEW)
    val diffPanel = findImagePanel(panel, ScreenshotViewType.DIFF)
    val refPanel = findImagePanel(panel, ScreenshotViewType.REFERENCE)

    assertNotNull("New Image Panel should exist", newPanel)
    assertNotNull("Diff Image Panel should exist", diffPanel)
    assertNotNull("Reference Image Panel should exist", refPanel)

    assertEquals("No New Image", getPlaceholderText(newPanel!!))
    assertEquals("No Difference", getPlaceholderText(diffPanel!!)) // Passed test
    assertEquals("No Reference Image", getPlaceholderText(refPanel!!))
  }

  @Test
  fun testPlaceholdersForFailedTest() = runInEdtAndWait {
    val panel = PreviewDetailsPanel()
    val details = PreviewDetails(
      testId = "test1",
      className = "Class",
      methodName = "method",
      previewName = "preview",
      testResult = AndroidTestCaseResult.FAILED,
      srcImagePath = null,
      diffImagePath = null, // Missing diff image for failed test
      destImagePath = null
    )
    val toolbar = ComposePanel()

    panel.displayPreviews(
      listOf(details),
      emptyMap(),
      ScreenshotViewType.ALL,
      toolbar
    )

    val diffPanel = findImagePanel(panel, ScreenshotViewType.DIFF)
    assertNotNull("Diff Image Panel should exist", diffPanel)

    assertEquals("No Diff Image", getPlaceholderText(diffPanel!!))
  }

  @Test
  fun testToolbarActionsCreated() = runInEdtAndWait {
    val panel = PreviewDetailsPanel()
    val details = PreviewDetails(
      testId = "test1",
      className = "Class",
      methodName = "method",
      previewName = "preview",
      testResult = AndroidTestCaseResult.PASSED
    )
    val toolbar = ComposePanel()

    panel.displayPreviews(
      listOf(details),
      emptyMap(),
      ScreenshotViewType.ALL,
      toolbar
    )

    val actionToolbar = findActionToolbar(panel)
    assertNotNull("ActionToolbar should be created", actionToolbar)

    val actions = actionToolbar!!.actionGroup.getChildren(null)
    assertTrue("Toolbar should contain actions", actions.isNotEmpty())

    // Verify expected actions are present (by checking some names or types if possible,
    // or just count. The standard actions are: Chessboard, Grid, Separator, ZoomOut, ZoomIn, 1:1, Fit)
    // We expect at least 6 items (including separator).
    assertTrue("Toolbar should have multiple actions", actions.size >= 6)
  }

  private fun findImagePanel(container: Container, type: ScreenshotViewType): ImageWithToolbarPanel? {
    if (container is ImageWithToolbarPanel && container.title == type) {
      return container
    }
    for (component in container.components) {
      if (component is Container) {
        val found = findImagePanel(component, type)
        if (found != null) return found
      }
    }
    return null
  }

  private fun findActionToolbar(container: Container): ActionToolbar? {
    if (container is ActionToolbar) {
      return container
    }
    for (component in container.components) {
      if (component is Container) {
        val found = findActionToolbar(component)
        if (found != null) return found
      }
    }
    return null
  }

  private fun getPlaceholderText(panel: ImageWithToolbarPanel): String? {
    val scrollPane = panel.components.find { it is JScrollPane } as? JScrollPane
    val view = scrollPane?.viewport?.view
    return (view as? JBLabel)?.text
  }
}
