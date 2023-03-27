/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.folding

import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ConsoleFolding
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [EditorFoldingDetector]
 */
@RunsInEdt
class EditorFoldingDetectorTest {
  private val projectRule = ProjectRule()
  private val logcatEditorRule = LogcatEditorRule(projectRule)

  @get:Rule
  val rule = RuleChain(projectRule, logcatEditorRule, EdtRule())

  private val editor get() = logcatEditorRule.editor

  @Test
  fun detectFoldings_firstLines() {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo")))
    editor.document.setText("""
      foo
      foo
      bar
    """.trimIndent())

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.foldingModel.allFoldRegions.map { it.toFoldInfo(editor) }).containsExactly(FoldInfo("foo\nfoo", "2 x foo"))
  }

  @Test
  fun detectFoldings_lastLines() {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo")))
    editor.document.setText("""
      bar
      foo
      foo
    """.trimIndent())

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.foldingModel.allFoldRegions.map { it.toFoldInfo(editor) }).containsExactly(FoldInfo("foo\nfoo", "2 x foo"))
  }

  @Test
  fun detectFoldings_middleLines() {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo")))
    editor.document.setText("""
      bar
      foo
      foo
      bar
    """.trimIndent())

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.foldingModel.allFoldRegions.map { it.toFoldInfo(editor) }).containsExactly(FoldInfo("foo\nfoo", "2 x foo"))
  }

  @Test
  fun detectFoldings_allLines() {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo")))
    editor.document.setText("""
      foo
      foo
    """.trimIndent())

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.foldingModel.allFoldRegions.map { it.toFoldInfo(editor) }).containsExactly(FoldInfo("foo\nfoo", "2 x foo"))
  }

  @Test
  fun detectFoldings_shouldBeAttachedToPreviousLine_true() {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo", shouldBeAttachedToThePreviousLine = true)))
    editor.document.setText("""
      bar
      foo
    """.trimIndent())

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.foldingModel.allFoldRegions.map { it.toFoldInfo(editor) }).containsExactly(FoldInfo("\nfoo", "1 x foo"))
  }

  @Test
  fun detectFoldings_shouldBeAttachedToPreviousLine_true_firstLine() {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo", shouldBeAttachedToThePreviousLine = true)))
    editor.document.setText("""
      foo
      bar
    """.trimIndent())

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.foldingModel.allFoldRegions.map { it.toFoldInfo(editor) }).containsExactly(FoldInfo("foo", "1 x foo"))
  }

  @Test
  fun detectFoldings_shouldBeAttachedToPreviousLine_false() {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo", shouldBeAttachedToThePreviousLine = false)))
    editor.document.setText("""
      bar
      foo
    """.trimIndent())

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.foldingModel.allFoldRegions.map { it.toFoldInfo(editor) }).containsExactly(FoldInfo("foo", "1 x foo"))
  }

  @Test
  fun detectFoldings_multipleRegions() {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo")))
    editor.document.setText("""
      foo1
      bar
      foo2
      bar
      foo3
    """.trimIndent())

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.foldingModel.allFoldRegions.map { it.toFoldInfo(editor) })
      .containsExactly(
        FoldInfo("foo1", "1 x foo"),
        FoldInfo("foo2", "1 x foo"),
        FoldInfo("foo3", "1 x foo"),
      )
  }

  @Test
  fun detectFoldings_multipleFoldings() {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo"), TestConsoleFolding("bar")))
    editor.document.setText("""
      foo1
      bar1
      foo2
      bar2
    """.trimIndent())

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.foldingModel.allFoldRegions.map { it.toFoldInfo(editor) })
      .containsExactly(
        FoldInfo("foo1", "1 x foo"),
        FoldInfo("foo2", "1 x foo"),
        FoldInfo("bar1", "1 x bar"),
        FoldInfo("bar2", "1 x bar"),
      )
  }

  @Test
  fun detectFoldings_nestedFoldings() {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo|bar"), TestConsoleFolding("bar")))
    editor.document.setText("""
      foo1
      bar1
      foo2
      bar2
      foo3
    """.trimIndent())

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.foldingModel.allFoldRegions.map { it.toFoldInfo(editor) })
      .containsExactly(
        FoldInfo("foo1\nbar1\nfoo2\nbar2\nfoo3", "5 x foo|bar"),
        FoldInfo("bar1", "1 x bar"),
        FoldInfo("bar2", "1 x bar"),
      )
  }

  @Test
  fun detectFoldings_nestedFoldings_reverseOrder() {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("bar"), TestConsoleFolding("foo|bar")))
    editor.document.setText("""
      foo1
      bar1
      foo2
      bar2
      foo3
    """.trimIndent())

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.foldingModel.allFoldRegions.map { it.toFoldInfo(editor) })
      .containsExactly(
        FoldInfo("foo1\nbar1\nfoo2\nbar2\nfoo3", "5 x foo|bar"),
        FoldInfo("bar1", "1 x bar"),
        FoldInfo("bar2", "1 x bar"),
      )
  }

  @Test
  fun foldingDetector_disabled() {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo", isEnabled = false)))
    editor.document.setText("""
      foo
      bar
    """.trimIndent())

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.foldingModel.allFoldRegions.map { it.toFoldInfo(editor) }).isEmpty()
  }

  @Test
  fun detectFoldings_defaultFoldings() {
    val foldingDetector = EditorFoldingDetector(projectRule.project, editor)
    editor.document.setText("""
      at java.lang.reflect.Method.invoke(Native Method)
    """.trimIndent())

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.foldingModel.allFoldRegions.map { it.toFoldInfo(editor) })
      .containsExactly(FoldInfo("at java.lang.reflect.Method.invoke(Native Method)", " <1 internal line>"))
  }

  private fun foldingDetector(editor: Editor, consoleFoldings: List<ConsoleFolding>) =
    EditorFoldingDetector(projectRule.project, editor, consoleFoldings)
}

private fun FoldRegion.toFoldInfo(editor: Editor) = FoldInfo(editor.document.text.substring(startOffset, endOffset), placeholderText)

private data class FoldInfo(val text: String, val placeholder: String)

private class TestConsoleFolding(
  private val stringToFold: String,
  private val shouldBeAttachedToThePreviousLine: Boolean = false,
  private val isEnabled: Boolean = true,
) : ConsoleFolding() {
  private val regex = Regex(stringToFold)

  override fun shouldFoldLine(project: Project, line: String) = regex.find(line) != null

  override fun getPlaceholderText(project: Project, lines: MutableList<String>): String = "${lines.size} x $stringToFold"

  override fun shouldBeAttachedToThePreviousLine() = shouldBeAttachedToThePreviousLine

  override fun isEnabledForConsole(consoleView: ConsoleView) = isEnabled
}
