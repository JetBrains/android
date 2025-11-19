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
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.ScreenshotViewType
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBScrollPane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import javax.swing.JPanel

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
    val hasSplitter = activePanel.components.any { it is OnePixelSplitter }
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
}
