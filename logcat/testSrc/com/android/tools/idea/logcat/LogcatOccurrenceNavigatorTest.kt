/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat

import com.android.tools.idea.logcat.LogcatOccurrenceNavigator.Companion.FOLLOWED_HYPERLINK_ATTRIBUTES
import com.android.tools.idea.logcat.hyperlinks.EditorHyperlinkDetector
import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.android.tools.idea.logcat.util.FakePsiShortNamesCache
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.ide.OccurenceNavigator
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val CARET = "^"

private val PROJECT_FILES = listOf(
  "MainActivity.java",
  "Activity.java",
)

/**
 * Tests for [LogcatOccurrenceNavigator]
 */
@RunsInEdt
class LogcatOccurrenceNavigatorTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val logcatEditorRule = LogcatEditorRule(projectRule)

  @get:Rule
  val rule = RuleChain(projectRule, logcatEditorRule, EdtRule(), disposableRule)

  private val editor get() = logcatEditorRule.editor
  private val editorHyperlinkDetector by lazy { EditorHyperlinkDetector(projectRule.project, editor) }
  private val editorHyperlinkSupport by lazy { EditorHyperlinkSupport.get(editor) }

  @Before
  fun setUp() {
    projectRule.project.replaceService(
      PsiShortNamesCache::class.java, FakePsiShortNamesCache(projectRule.project, PROJECT_FILES), disposableRule.disposable)
  }

  @Test
  fun hasOccurrence() {
    val navigator = logcatOccurrenceNavigator(editor)
    setEditorText(editor, " at com.example.myapplication.MainActivity.onCreate(MainActivity.java:19)")

    // Regardless of the caret, we should have both next & prev occurrences
    for (line in 0 until editor.document.lineCount) {
      editor.caretModel.moveToOffset(editor.document.getLineStartOffset(line))
      assertThat(navigator.hasNextOccurence()).isTrue()
      assertThat(navigator.hasPreviousOccurence()).isTrue()
    }
  }

  @Test
  fun hasOccurrence_ignoresNonStackFrame() {
    val navigator = logcatOccurrenceNavigator(editor)
    setEditorText(editor, "http://google.com")

    assertThat(navigator.hasNextOccurence()).isFalse()
    assertThat(navigator.hasPreviousOccurence()).isFalse()
  }

  @Test
  fun goNextOccurrence_caretBefore() {
    val navigator = logcatOccurrenceNavigator(editor)
    setEditorText(
      editor,
      """
        $CARET
          at com.example.myapplication.MainActivity.onCreate(MainActivity.java:19)
          at android.app.Activity.performCreate(Activity.java:8000)
      """.trimIndent())

    val occurrenceInfo = navigator.goNextOccurence()

    assertThat(occurrenceInfo?.occurenceNumber).isEqualTo(1)
    assertThat(occurrenceInfo?.occurencesCount).isEqualTo(2)
    val adapter = occurrenceInfo?.navigateable as LogcatNavigatableAdapter
    assertThat(adapter).isNotNull()
    assertThat(adapter.occurrenceRange.getText()).isEqualTo("MainActivity.java:19")
    assertThat(adapter.allRanges.map(RangeHighlighter::getText)).containsExactly(
      "MainActivity.java:19",
      "Activity.java:8000",
    ).inOrder()
  }

  @Test
  fun goNextOccurrence_caretAfter() {
    val navigator = logcatOccurrenceNavigator(editor)
    setEditorText(
      editor,
      """
          at com.example.myapplication.MainActivity.onCreate(MainActivity.java:19)
          at android.app.Activity.performCreate(Activity.java:8000)
        $CARET
      """.trimIndent())

    val occurrenceInfo = navigator.goNextOccurence()

    assertThat(occurrenceInfo?.occurenceNumber).isEqualTo(1)
    assertThat(occurrenceInfo?.occurencesCount).isEqualTo(2)
    val adapter = occurrenceInfo?.navigateable as LogcatNavigatableAdapter
    assertThat(adapter).isNotNull()
    assertThat(adapter.occurrenceRange.getText()).isEqualTo("MainActivity.java:19")
    assertThat(adapter.allRanges.map(RangeHighlighter::getText)).containsExactly(
      "MainActivity.java:19",
      "Activity.java:8000",
    ).inOrder()
  }

  @Test
  fun goNextOccurrence_caretBetween() {
    val navigator = logcatOccurrenceNavigator(editor)
    setEditorText(
      editor,
      """
          at com.example.myapplication.MainActivity.onCreate(MainActivity.java:19)
        $CARET
          at android.app.Activity.performCreate(Activity.java:8000)
      """.trimIndent())

    val occurrenceInfo = navigator.goNextOccurence()

    assertThat(occurrenceInfo?.occurenceNumber).isEqualTo(2)
    assertThat(occurrenceInfo?.occurencesCount).isEqualTo(2)
    val adapter = occurrenceInfo?.navigateable as LogcatNavigatableAdapter
    assertThat(adapter).isNotNull()
    assertThat(adapter.occurrenceRange.getText()).isEqualTo("Activity.java:8000")
    assertThat(adapter.allRanges.map(RangeHighlighter::getText)).containsExactly(
      "MainActivity.java:19",
      "Activity.java:8000",
    ).inOrder()
  }

  @Test
  fun goNextOccurrence_insideHyperlink() {
    val navigator = logcatOccurrenceNavigator(editor)
    setEditorText(
      editor,
      """
          at com.example.myapplication.MainActivity.onCreate(MainActivity.java$CARET:19)
          at android.app.Activity.performCreate(Activity.java:8000)
      """.trimIndent())

    val occurrenceInfo = navigator.goNextOccurence()

    assertThat(occurrenceInfo?.occurenceNumber).isEqualTo(2)
    assertThat(occurrenceInfo?.occurencesCount).isEqualTo(2)
    val adapter = occurrenceInfo?.navigateable as LogcatNavigatableAdapter
    assertThat(adapter).isNotNull()
    assertThat(adapter.occurrenceRange.getText()).isEqualTo("Activity.java:8000")
    assertThat(adapter.allRanges.map(RangeHighlighter::getText)).containsExactly(
      "MainActivity.java:19",
      "Activity.java:8000",
    ).inOrder()
  }

  @Test
  fun goPreviousOccurrence_caretBefore() {
    val navigator = logcatOccurrenceNavigator(editor)
    setEditorText(
      editor,
      """
        $CARET
          at com.example.myapplication.MainActivity.onCreate(MainActivity.java:19)
          at android.app.Activity.performCreate(Activity.java:8000)
      """.trimIndent())

    val occurrenceInfo = navigator.goPreviousOccurence()

    assertThat(occurrenceInfo?.occurenceNumber).isEqualTo(2)
    assertThat(occurrenceInfo?.occurencesCount).isEqualTo(2)
    val adapter = occurrenceInfo?.navigateable as LogcatNavigatableAdapter
    assertThat(adapter).isNotNull()
    assertThat(adapter.occurrenceRange.getText()).isEqualTo("Activity.java:8000")
    assertThat(adapter.allRanges.map(RangeHighlighter::getText)).containsExactly(
      "MainActivity.java:19",
      "Activity.java:8000",
    ).inOrder()
  }

  @Test
  fun goPreviousOccurrence_caretAfter() {
    val navigator = logcatOccurrenceNavigator(editor)
    setEditorText(
      editor,
      """
          at com.example.myapplication.MainActivity.onCreate(MainActivity.java:19)
          at android.app.Activity.performCreate(Activity.java:8000)
        $CARET
      """.trimIndent())

    val occurrenceInfo = navigator.goPreviousOccurence()

    assertThat(occurrenceInfo?.occurenceNumber).isEqualTo(2)
    assertThat(occurrenceInfo?.occurencesCount).isEqualTo(2)
    val adapter = occurrenceInfo?.navigateable as LogcatNavigatableAdapter
    assertThat(adapter).isNotNull()
    assertThat(adapter.occurrenceRange.getText()).isEqualTo("Activity.java:8000")
    assertThat(adapter.allRanges.map(RangeHighlighter::getText)).containsExactly(
      "MainActivity.java:19",
      "Activity.java:8000",
    ).inOrder()
  }

  @Test
  fun goPreviousOccurrence_caretBetween() {
    val navigator = logcatOccurrenceNavigator(editor)
    setEditorText(
      editor,
      """
          at com.example.myapplication.MainActivity.onCreate(MainActivity.java:19)
        $CARET
          at android.app.Activity.performCreate(Activity.java:8000)
      """.trimIndent())

    val occurrenceInfo = navigator.goPreviousOccurence()

    assertThat(occurrenceInfo?.occurenceNumber).isEqualTo(1)
    assertThat(occurrenceInfo?.occurencesCount).isEqualTo(2)
    val adapter = occurrenceInfo?.navigateable as LogcatNavigatableAdapter
    assertThat(adapter).isNotNull()
    assertThat(adapter.occurrenceRange.getText()).isEqualTo("MainActivity.java:19")
    assertThat(adapter.allRanges.map(RangeHighlighter::getText)).containsExactly(
      "MainActivity.java:19",
      "Activity.java:8000",
    ).inOrder()
  }

  @Test
  fun goPreviousOccurrence_insideHyperlink() {
    val navigator = logcatOccurrenceNavigator(editor)
    setEditorText(
      editor,
      """
          at com.example.myapplication.MainActivity.onCreate(MainActivity.java$CARET:19)
          at android.app.Activity.performCreate(Activity.java:8000)
      """.trimIndent())

    val occurrenceInfo = navigator.goPreviousOccurence()

    assertThat(occurrenceInfo?.occurenceNumber).isEqualTo(2)
    assertThat(occurrenceInfo?.occurencesCount).isEqualTo(2)
    val adapter = occurrenceInfo?.navigateable as LogcatNavigatableAdapter
    assertThat(adapter).isNotNull()
    assertThat(adapter.occurrenceRange.getText()).isEqualTo("Activity.java:8000")
    assertThat(adapter.allRanges.map(RangeHighlighter::getText)).containsExactly(
      "MainActivity.java:19",
      "Activity.java:8000",
    ).inOrder()
  }

  /**
   * This test navigates all the stack frames in order including wrapping around the document.
   *
   * For each iteration, it verifies that the caret was moved to the proper location and that the current link only is highlighted with
   * FOLLOWED_HYPERLINK_ATTRIBUTES.
   *
   * It's enough to test goNextOccurence() only because the tested functionality doesn't depend on direction.
   */
  @Test
  fun goOccurrence_navigate() {
    val navigator = logcatOccurrenceNavigator(editor)
    setEditorText(
      editor,
      """
          at com.example.myapplication.MainActivity.onCreate(MainActivity.java:1)
          at com.example.myapplication.MainActivity.onCreate(MainActivity.java:2)
          at android.app.Activity.performCreate(Activity.java:3)
          at android.app.Activity.performCreate(Activity.java:4)
          $CARET
      """.trimIndent())

    for (i in 0 until editor.document.textLength * 2) {
      val occurrenceInfo = navigator.goNextOccurence()

      occurrenceInfo?.navigateable?.navigate(false)

      val adapter = occurrenceInfo?.navigateable as LogcatNavigatableAdapter
      val offset = editor.caretModel.offset
      assertThat(offset).named("line=$i").isEqualTo(adapter.occurrenceRange.startOffset)
      assertThat(editor.document.getLineNumber(offset)).named("i=$i").isEqualTo(i % 4)
      for (range in adapter.allRanges) {
        val textAttributes = range.getTextAttributes(editor.colorsScheme)
        if (range === adapter.occurrenceRange) {
          // The range we navigate to is highlighted as a followed link
          assertThat(textAttributes).named("i=$i").isEqualTo(FOLLOWED_HYPERLINK_ATTRIBUTES)
        }
        else {
          // All other ranges are restored to their original highlight
          assertThat(textAttributes).named("i=$i range=${range.getText()}").isNotNull()
          assertThat(textAttributes).named("i=$i range=${range.getText()}").isNotEqualTo(FOLLOWED_HYPERLINK_ATTRIBUTES)
        }
      }
    }
  }

  @Test
  fun goOccurrence_navigateDownThenUp() {
    val navigator = logcatOccurrenceNavigator(editor)
    setEditorText(
      editor,
      """
          at com.example.myapplication.MainActivity.onCreate(MainActivity.java:1)
          at com.example.myapplication.MainActivity.onCreate(MainActivity.java:2)
          at android.app.Activity.performCreate(Activity.java:3)
          at android.app.Activity.performCreate(Activity.java:4)
          $CARET
      """.trimIndent())


    assertThat(navigator.navigateNext()?.occurenceNumber).isEqualTo(1)
    assertThat(navigator.navigateNext()?.occurenceNumber).isEqualTo(2)
    assertThat(navigator.navigateNext()?.occurenceNumber).isEqualTo(3)
    assertThat(navigator.navigatePrevious()?.occurenceNumber).isEqualTo(2)
    assertThat(navigator.navigatePrevious()?.occurenceNumber).isEqualTo(1)
    assertThat(navigator.navigatePrevious()?.occurenceNumber).isEqualTo(4)
  }


  private fun logcatOccurrenceNavigator(editor: Editor) = LogcatOccurrenceNavigator(projectRule.project, editor)

  private fun setEditorText(editor: Editor, text: String) {
    editor.document.setText(text.replace(CARET, ""))
    editorHyperlinkDetector.detectHyperlinks(0, editor.document.lineCount - 1, sdk = null)
    editorHyperlinkSupport.waitForPendingFilters(/* timeoutMs */ 5000)
    val caret = text.indexOf(CARET)
    if (caret >= 0) {
      editor.caretModel.moveToOffset(caret)
    }
  }
}

private fun RangeHighlighter.getText(): String = document.immutableCharSequence.subSequence(startOffset, endOffset).toString()

private fun LogcatOccurrenceNavigator.navigateNext(): OccurenceNavigator.OccurenceInfo? {
  val occurrenceInfo = goNextOccurence()
  occurrenceInfo?.navigateable?.navigate(false)
  return occurrenceInfo
}

private fun LogcatOccurrenceNavigator.navigatePrevious(): OccurenceNavigator.OccurenceInfo? {
  val occurrenceInfo = goPreviousOccurence()
  occurrenceInfo?.navigateable?.navigate(false)
  return occurrenceInfo
}
