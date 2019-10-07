/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals

/**
 * Helper class do test change tracking and asserting on specific types of changes.
 */
private class ChangeTracker() {
  private var refreshCounter = 0
  private var sourceCodeChanges = 0

  private fun reset() {
    refreshCounter = 0
    sourceCodeChanges = 0
  }

  /**
   * Called when a non-code change happens an a refresh would be required.
   */
  fun onRefresh() {
    refreshCounter++
  }

  /**
   * Called for a source code change that would require the user to rebuild.
   */
  fun onSourceCodeChange() {
    sourceCodeChanges++
  }

  private fun assertWithCounters(refresh: Int, sourceCodeChanges: Int, runnable: () -> Unit) {
    reset()
    runnable()
    assertEquals(refresh, refreshCounter)
    assertEquals(sourceCodeChanges, this.sourceCodeChanges)
  }

  /**
   * Asserts that the given [runnable] triggers only a code change notification.
   */
  fun assertCodeChange(runnable: () -> Unit) = assertWithCounters(refresh = 0, sourceCodeChanges = 1, runnable = runnable)

  /**
   * Asserts that the given [runnable] triggers only a non-code change notification.
   */
  fun assertNoCodeChange(runnable: () -> Unit) = assertWithCounters(refresh = 1, sourceCodeChanges = 0, runnable = runnable)
}

class ChangeManagerTest : ComposeLightJavaCodeInsightFixtureTestCase() {
  fun testSingleFileChangeTests() {
    @Language("kotlin")
    val startFileContent = """
      import androidx.ui.tooling.preview.Preview
      import androidx.compose.Composable

      @Composable
      @Preview
      fun Preview1() {
      }

      @Composable
      @Preview(name = "preview2", apiLevel = 12)
      fun Preview2() {
        NoComposablePreview("hello")
      }

      @Composable
      @Preview(name = "preview3", widthDp = 1, heightDp = 2, fontScale = 0.2f)
      fun Preview3() {
          NoComposablePreview("Preview3")
          NoComposablePreview("Preview3 line 2")
      }

      @Composable
      fun NoPreviewComposable() {

      }

      @Preview
      fun NoComposablePreview(label: String) {

      }
    """.trimIndent()

    val composeTest = myFixture.addFileToProject("src/Test.kt", startFileContent)

    val tracker = ChangeTracker()
    setupChangeListener(project,
                        composeTest,
                        { AnnotationPreviewElementFinder.findPreviewMethods(project, composeTest.virtualFile) },
                        tracker::onRefresh,
                        tracker::onSourceCodeChange,
                        project)

    // No code changes
    tracker.assertNoCodeChange {
      composeTest.replaceStringOnce("name = \"preview2\"", "name = \"preview2B\"")
    }
    tracker.assertNoCodeChange {
      composeTest.replaceStringOnce("heightDp = 2", "heightDp = 50")
    }

    // Code changes
    tracker.assertCodeChange {
      composeTest.replaceStringOnce("NoComposablePreview(\"hello\")", "NoComposablePreview(\"bye\")")
    }
    tracker.assertCodeChange {
      composeTest.replaceStringOnce("NoComposablePreview(\"bye\")", "NoPreviewComposable()")
    }
    tracker.assertCodeChange {
      // This currently triggers a code change although we should be able to ignore it
      composeTest.runOnDocument { _, document ->
        document.insertString(0, "// Just a comment\n")
      }
    }
  }
}