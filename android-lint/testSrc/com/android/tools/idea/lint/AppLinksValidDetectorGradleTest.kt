/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.lint

import com.android.tools.idea.lint.inspections.AndroidLintIntersectingDeepLinksInspection
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_APP_LINKS
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile

private const val ANDROID_MANIFEST_PATH = "app/src/main/AndroidManifest.xml"
private const val JUMP_ACTION_TEXT = "Go to activity with intersecting deep link"

class AppLinksValidDetectorGradleTest : AndroidGradleTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.allowTreeAccessForAllFiles()
  }

  fun test_primaryLocation_hasJumpFix() {
    loadProject(TEST_ARTIFACTS_APP_LINKS)
    val file = myFixture.loadFile(ANDROID_MANIFEST_PATH)
    myFixture.checkLint(
      file,
      AndroidLintIntersectingDeepLinksInspection(),
      // Note: indentation below is important
      """
    <activity|
        android:name=".Intersection1"
      """,
      """
      Warning: Deep link URI intersection URIs(schemes=[`intersection`]) with activity google.testartifacts.Intersection2
          <activity
           ~~~~~~~~
          Fix: Go to activity with intersecting deep link
          Fix: Suppress: Add tools:ignore="IntersectingDeepLinks" attribute
      """,
    )
    val quickFix = getQuickFix(file, JUMP_ACTION_TEXT)
    quickFix?.invoke(project, myFixture.editor, file)
    // The above function actually applies the fixes, so we should already have jumped to the other
    // activity.
    assertThat(myFixture.caretOffset)
      .isEqualTo(
        file.findCaretOffset(
          // Note: indentation below is important
          """
    <|activity
        android:name=".Intersection2"
      """
        )
      )
  }

  fun test_secondaryLocation_hasJumpFix() {
    loadProject(TEST_ARTIFACTS_APP_LINKS)
    val file = myFixture.loadFile(ANDROID_MANIFEST_PATH)
    myFixture.checkLint(
      file,
      AndroidLintIntersectingDeepLinksInspection(),
      // Note: indentation below is important
      """
    <activity|
        android:name=".Intersection2"
      """,
      """
      Warning: Deep link URI intersection URIs(schemes=[`intersection`]) with activity google.testartifacts.Intersection1
          <activity
           ~~~~~~~~
          Fix: Go to activity with intersecting deep link
          Fix: Suppress: Add tools:ignore="IntersectingDeepLinks" attribute
      """,
    )
    val quickFix = getQuickFix(file, JUMP_ACTION_TEXT)
    quickFix?.invoke(project, myFixture.editor, file)
    // The above function actually applies the fixes, so we should already have jumped to the other
    // activity.
    assertThat(myFixture.caretOffset)
      .isEqualTo(
        file.findCaretOffset(
          // Note: indentation below is important
          """
    <|activity
        android:name=".Intersection1"
      """
        )
      )
  }

  fun test_toolsIgnore() {
    loadProject(TEST_ARTIFACTS_APP_LINKS)
    val file = myFixture.loadFile(ANDROID_MANIFEST_PATH)
    myFixture.checkLint(
      file,
      AndroidLintIntersectingDeepLinksInspection(),
      // Note: indentation below is important
      """
    <activity|
        android:name=".MainActivity"
      """,
      "No warnings.",
    )
  }

  private fun getQuickFix(file: PsiFile, text: String): IntentionAction? {
    val highlights =
      myFixture.doHighlighting(HighlightSeverity.WARNING).asSequence().sortedBy { it.startOffset }
    return highlights
      .mapNotNull {
        val startIndex = it.startOffset
        val endOffset = it.endOffset
        val target = myFixture.caretOffset
        if (target < startIndex || target > endOffset) {
          return@mapNotNull null
        }
        it.findRegisteredQuickFix { desc, _ ->
          val action = desc.action
          if (action.isAvailable(project, myFixture.editor, file) && action.text == text) {
            return@findRegisteredQuickFix action
          }
          null
        }
      }
      .firstOrNull()
  }
}
