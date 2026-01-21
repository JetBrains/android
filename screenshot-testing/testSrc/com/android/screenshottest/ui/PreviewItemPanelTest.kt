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
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.ScreenshotViewType
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.awt.Container
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock

class PreviewItemPanelTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  var temporaryFolder = TemporaryFolder()

  @Test
  fun verifyInitialization() = runInEdtAndWait {
    val details = PreviewDetails(
      testId = "test.id",
      className = "TestClass",
      methodName = "testMethod",
      previewName = "preview",
      testResult = AndroidTestCaseResult.PASSED
    )
    val panel = PreviewItemPanel(details)
    assertEquals(details, panel.previewData)
  }

  @Test
  fun verifyDetailsPanelHidden() = runInEdtAndWait {
    val details = PreviewDetails(
      testId = "test.id",
      className = "TestClass",
      methodName = "testMethod",
      previewName = "preview",
      testResult = AndroidTestCaseResult.PASSED
    )
    val panel = PreviewItemPanel(details, showDetails = false)

    // Expect only 1 component (the image panel)
    assertEquals(1, panel.componentCount)
  }

  @Test
  fun verifyDetailsLabels() = runInEdtAndWait {
    val details = PreviewDetails(
      testId = "test.id",
      className = "TestClass",
      methodName = "testMethod",
      previewName = "MyPreview",
      testResult = AndroidTestCaseResult.FAILED,
      diffPercent = "0.01" // 99% match
    )
    val panel = PreviewItemPanel(details, showDetails = true)

    val labels = findAllLabels(panel)

    val matchTextLabel = labels.find { it.text == "Match: " }
    val percentageLabel = labels.find { it.text == "99.00%" }
    val nameLabel = labels.find { it.text == "MyPreview" }

    assertNotNull("Match prefix label 'Match: ' should exist", matchTextLabel)
    assertNotNull("Match percentage label '99.00%' should exist", percentageLabel)
    assertNotNull("Name label 'MyPreview' should exist", nameLabel)
  }

  @Test
  fun verifyPlaceholderForMissingNewImage() = runInEdtAndWait {
    val details = PreviewDetails(
      testId = "test.id",
      className = "TestClass",
      methodName = "testMethod",
      previewName = "preview",
      testResult = AndroidTestCaseResult.FAILED,
      srcImagePath = null
    )
    val panel = PreviewItemPanel(details)
    panel.showImageForView(ScreenshotViewType.NEW)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val label = findLabel(panel)
    assertEquals("No New Image", label?.text)
  }

  @Test
  fun verifyPlaceholderForPassedDiff() = runInEdtAndWait {
    val details = PreviewDetails(
      testId = "test.id",
      className = "TestClass",
      methodName = "testMethod",
      previewName = "preview",
      testResult = AndroidTestCaseResult.PASSED,
      diffImagePath = null
    )
    val panel = PreviewItemPanel(details)
    panel.showImageForView(ScreenshotViewType.DIFF)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val label = findLabel(panel)
    assertEquals("No Difference", label?.text)
  }

  @Test
  fun verifyPlaceholderForFailedDiff() = runInEdtAndWait {
    val details = PreviewDetails(
      testId = "test.id",
      className = "TestClass",
      methodName = "testMethod",
      previewName = "preview",
      testResult = AndroidTestCaseResult.FAILED,
      diffImagePath = null
    )
    val panel = PreviewItemPanel(details)
    panel.showImageForView(ScreenshotViewType.DIFF)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val label = findLabel(panel)
    assertEquals("No Diff Image", label?.text)
  }

  @Test
  fun verifyPlaceholderForReferenceImage() = runInEdtAndWait {
    val details = PreviewDetails(
      testId = "test.id",
      className = "TestClass",
      methodName = "testMethod",
      previewName = "preview",
      testResult = AndroidTestCaseResult.PASSED,
      destImagePath = null
    )
    val panel = PreviewItemPanel(details)
    panel.showImageForView(ScreenshotViewType.REFERENCE)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val label = findLabel(panel)
    assertEquals("No Reference Image", label?.text)
  }

  @Test
  fun verifyImageLoadIsCached() = runInEdtAndWait {
    var imageCreationCount = 0

    val srcPath = temporaryFolder.newFile("image.png").absolutePath
    val diffPath = temporaryFolder.newFile("diff.png").absolutePath
    val details = PreviewDetails(
      testId = "test.id",
      className = "TestClass",
      methodName = "testMethod",
      previewName = "preview",
      testResult = AndroidTestCaseResult.FAILED,
      srcImagePath = srcPath,
      diffImagePath = diffPath
    )

    val panel = PreviewItemPanel(
      previewData = details,
      appExecutorService = MoreExecutors.newDirectExecutorService(),
      createImageIcon = { _ ->
        imageCreationCount++
        mock()
      }
    )

    panel.showImageForView(ScreenshotViewType.NEW)

    assertEquals("First load should trigger image creation", 1, imageCreationCount)

    // We request the SAME path. The 'if (current == new)' check should prevent execution.
    panel.showImageForView(ScreenshotViewType.NEW)

    assertEquals(
      "Subsequent load for same path should be skipped (Cached)",
      1,
      imageCreationCount
    )

    // Switch to DIFF view (different path)
    panel.showImageForView(ScreenshotViewType.DIFF)

    assertEquals("Changing the path should trigger new load", 2, imageCreationCount)
  }

  @Test
  fun verifyImageReloadsAfterPlaceholder() = runInEdtAndWait {
    var imageCreationCount = 0
    val srcPath = temporaryFolder.newFile("image.png").absolutePath
    val details = PreviewDetails(
      testId = "test.id",
      className = "TestClass",
      methodName = "testMethod",
      previewName = "preview",
      testResult = AndroidTestCaseResult.PASSED,
      srcImagePath = srcPath,
      diffImagePath = null
    )

    val panel = PreviewItemPanel(
      previewData = details,
      showDetails = false, // Disable details to avoid finding the "Match: " label
      appExecutorService = MoreExecutors.newDirectExecutorService(),
      createImageIcon = { _ ->
        imageCreationCount++
        mock()
      }
    )

    // 1. Initial load
    panel.showImageForView(ScreenshotViewType.NEW)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertEquals("Should load image the first time", 1, imageCreationCount)
    assertTrue("Flag should be true after successful load", panel.isLoadedSuccessfully)
    assertNull("The placeholder label should be removed when an image is displayed", findLabel(panel))

    // 2. Switch to Diff (which shows a placeholder for PASSED tests)
    panel.showImageForView(ScreenshotViewType.DIFF)
    // CRUCIAL: Dispatch events so the invokeLater in showPlaceholder runs
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val label = findLabel(panel)
    assertEquals("No Difference", label?.text)

    // 3. Switch back to "New" image (same path as step 1)
    panel.showImageForView(ScreenshotViewType.NEW)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // This should now be 2 because showPlaceholder reset currentImagePath
    assertEquals("Should trigger a new load after a placeholder was shown", 2, imageCreationCount)
    assertTrue("Flag should still be true", panel.isLoadedSuccessfully)
  }

  private fun findLabel(container: Container): JBLabel? {
    for (component in container.components) {
      if (component is JBLabel) {
        return component
      }
      if (component is Container) {
        val label = findLabel(component)
        if (label != null) return label
      }
    }
    return null
  }

  private fun findAllLabels(container: Container): List<JBLabel> {
    val result = mutableListOf<JBLabel>()
    for (component in container.components) {
      if (component is JBLabel) {
        result.add(component)
      } else if (component is Container) {
        result.addAll(findAllLabels(component))
      }
    }
    return result
  }
}
