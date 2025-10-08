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
import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ConsoleFolding
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test

/** Tests for [EditorFoldingDetector] */
class EditorFoldingDetectorTest {
  private val projectRule = ProjectRule()
  private val logcatEditorRule = LogcatEditorRule(projectRule)

  @get:Rule val rule = RuleChain(projectRule, WaitForIndexRule(projectRule), logcatEditorRule)

  private val editor
    get() = logcatEditorRule.editor

  private val document
    get() = editor.document

  @Test
  fun detectFoldings_firstLines(): Unit = runBlocking {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo")))
    editor.setText(
      """
      foo
      foo
      bar
    """
        .trimIndent()
    )

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.getFoldInfos()).containsExactly(FoldInfo("foo|foo", "2 x foo"))
  }

  @Test
  fun detectFoldings_lastLines(): Unit = runBlocking {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo")))
    editor.setText(
      """
      bar
      foo
      foo
    """
        .trimIndent()
    )

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.getFoldInfos()).containsExactly(FoldInfo("foo|foo", "2 x foo"))
  }

  @Test
  fun detectFoldings_middleLines(): Unit = runBlocking {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo")))
    editor.setText(
      """
      bar
      foo
      foo
      bar
    """
        .trimIndent()
    )

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.getFoldInfos()).containsExactly(FoldInfo("foo|foo", "2 x foo"))
  }

  @Test
  fun detectFoldings_allLines(): Unit = runBlocking {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo")))
    editor.setText(
      """
      foo
      foo
    """
        .trimIndent()
    )

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.getFoldInfos()).containsExactly(FoldInfo("foo|foo", "2 x foo"))
  }

  @Test
  fun detectFoldings_shouldBeAttachedToPreviousLine_true(): Unit = runBlocking {
    val foldingDetector =
      foldingDetector(
        editor,
        listOf(TestConsoleFolding("foo", shouldBeAttachedToThePreviousLine = true)),
      )
    editor.setText(
      """
      bar
      foo
    """
        .trimIndent()
    )

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.getFoldInfos()).containsExactly(FoldInfo("|foo", "1 x foo"))
  }

  @Test
  fun detectFoldings_shouldBeAttachedToPreviousLine_true_firstLine(): Unit = runBlocking {
    val foldingDetector =
      foldingDetector(
        editor,
        listOf(TestConsoleFolding("foo", shouldBeAttachedToThePreviousLine = true)),
      )
    editor.setText(
      """
      foo
      bar
    """
        .trimIndent()
    )

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.getFoldInfos()).containsExactly(FoldInfo("foo", "1 x foo"))
  }

  @Test
  fun detectFoldings_shouldBeAttachedToPreviousLine_false(): Unit = runBlocking {
    val foldingDetector =
      foldingDetector(
        editor,
        listOf(TestConsoleFolding("foo", shouldBeAttachedToThePreviousLine = false)),
      )
    editor.setText(
      """
      bar
      foo
    """
        .trimIndent()
    )

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.getFoldInfos()).containsExactly(FoldInfo("foo", "1 x foo"))
  }

  @Test
  fun detectFoldings_multipleRegions(): Unit = runBlocking {
    val foldingDetector = foldingDetector(editor, listOf(TestConsoleFolding("foo")))
    editor.setText(
      """
      foo1
      bar
      foo2
      bar
      foo3
    """
        .trimIndent()
    )

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.getFoldInfos())
      .containsExactly(
        FoldInfo("foo1", "1 x foo"),
        FoldInfo("foo2", "1 x foo"),
        FoldInfo("foo3", "1 x foo"),
      )
  }

  @Test
  fun detectFoldings_multipleFoldings(): Unit = runBlocking {
    val foldingDetector =
      foldingDetector(editor, listOf(TestConsoleFolding("foo"), TestConsoleFolding("bar")))
    editor.setText(
      """
      foo1
      bar1
      foo2
      bar2
    """
        .trimIndent()
    )

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.getFoldInfos())
      .containsExactly(
        FoldInfo("foo1", "1 x foo"),
        FoldInfo("foo2", "1 x foo"),
        FoldInfo("bar1", "1 x bar"),
        FoldInfo("bar2", "1 x bar"),
      )
  }

  @Test
  fun detectFoldings_nestedFoldings(): Unit = runBlocking {
    val foldingDetector =
      foldingDetector(editor, listOf(TestConsoleFolding("foo|bar"), TestConsoleFolding("bar")))
    editor.setText(
      """
      foo1
      bar1
      foo2
      bar2
      foo3
    """
        .trimIndent()
    )

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.getFoldInfos())
      .containsExactly(
        FoldInfo("foo1|bar1|foo2|bar2|foo3", "5 x foo|bar"),
        FoldInfo("bar1", "1 x bar"),
        FoldInfo("bar2", "1 x bar"),
      )
  }

  @Test
  fun detectFoldings_nestedFoldings_reverseOrder(): Unit = runBlocking {
    val foldingDetector =
      foldingDetector(editor, listOf(TestConsoleFolding("bar"), TestConsoleFolding("foo|bar")))
    editor.setText(
      """
      foo1
      bar1
      foo2
      bar2
      foo3
    """
        .trimIndent()
    )

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.getFoldInfos())
      .containsExactly(
        FoldInfo("foo1|bar1|foo2|bar2|foo3", "5 x foo|bar"),
        FoldInfo("bar1", "1 x bar"),
        FoldInfo("bar2", "1 x bar"),
      )
  }

  @Test
  fun foldingDetector_disabled(): Unit = runBlocking {
    val foldingDetector =
      foldingDetector(editor, listOf(TestConsoleFolding("foo", isEnabled = false)))
    editor.setText(
      """
      foo
      bar
    """
        .trimIndent()
    )

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.getFoldInfos()).isEmpty()
  }

  @Test
  fun detectFoldings_defaultFoldings(): Unit = runBlocking {
    val foldingDetector = EditorFoldingDetector(projectRule.project, editor)
    editor.setText(
      """
      at java.lang.reflect.Method.invoke(Native Method)
    """
        .trimIndent()
    )

    foldingDetector.detectFoldings(0, editor.document.lineCount - 1)

    assertThat(editor.getFoldInfos())
      .containsExactly(
        FoldInfo("at java.lang.reflect.Method.invoke(Native Method)", " <1 internal line>")
      )
  }

  @Test
  fun detectFoldings_mergeRegions_shouldBeAttachedToThePreviousLine_true(): Unit = runBlocking {
    val foldingDetector =
      foldingDetector(
        editor,
        listOf(TestConsoleFolding("foo", shouldBeAttachedToThePreviousLine = true)),
      )
    foldingDetector.appendLineAndDetect("foo1")
    foldingDetector.appendLineAndDetect("foo2")
    foldingDetector.appendLineAndDetect("bar1")
    foldingDetector.appendLineAndDetect("foo3")
    foldingDetector.appendLineAndDetect("foo4")
    foldingDetector.appendLineAndDetect("bar2")
    foldingDetector.appendLineAndDetect("foo5")
    foldingDetector.appendLineAndDetect("foo6")

    assertThat(editor.getFoldInfos())
      .containsExactly(
        FoldInfo("foo1|foo2", "2 x foo"),
        FoldInfo("|foo3|foo4", "2 x foo"),
        FoldInfo("|foo5|foo6", "2 x foo"),
      )
  }

  @Test
  fun detectFoldings_mergeRegions_shouldBeAttachedToThePreviousLine_false(): Unit = runBlocking {
    val foldingDetector =
      foldingDetector(
        editor,
        listOf(TestConsoleFolding("foo", shouldBeAttachedToThePreviousLine = false)),
      )
    foldingDetector.appendLineAndDetect("foo1")
    foldingDetector.appendLineAndDetect("foo2")
    foldingDetector.appendLineAndDetect("bar1")
    foldingDetector.appendLineAndDetect("foo3")
    foldingDetector.appendLineAndDetect("foo4")
    foldingDetector.appendLineAndDetect("bar2")
    foldingDetector.appendLineAndDetect("foo5")
    foldingDetector.appendLineAndDetect("foo6")

    assertThat(editor.getFoldInfos())
      .containsExactly(
        FoldInfo("foo1|foo2", "2 x foo"),
        FoldInfo("foo3|foo4", "2 x foo"),
        FoldInfo("foo5|foo6", "2 x foo"),
      )
  }

  private fun EditorFoldingDetector.appendLineAndDetect(line: String) = runBlocking {
    val startLine = if (document.lineCount == 0) 0 else document.lineCount - 1
    withContext(Dispatchers.EDT) { document.insertString(document.textLength, "$line\n") }
    detectFoldings(startLine, document.lineCount - 1)
  }

  private fun foldingDetector(editor: Editor, consoleFoldings: List<ConsoleFolding>) =
    EditorFoldingDetector(projectRule.project, editor, consoleFoldings)
}

private fun FoldRegion.toFoldInfo(editor: Editor) =
  FoldInfo(
    editor.document.text.substring(startOffset, endOffset).replace('\n', '|'),
    placeholderText,
  )

private data class FoldInfo(val text: String, val placeholder: String)

private class TestConsoleFolding(
  private val stringToFold: String,
  private val shouldBeAttachedToThePreviousLine: Boolean = false,
  private val isEnabled: Boolean = true,
) : ConsoleFolding() {
  private val regex = Regex(stringToFold)

  override fun shouldFoldLine(project: Project, line: String) = regex.find(line) != null

  override fun getPlaceholderText(project: Project, lines: MutableList<String>): String =
    "${lines.size} x $stringToFold"

  override fun shouldBeAttachedToThePreviousLine() = shouldBeAttachedToThePreviousLine

  override fun isEnabledForConsole(consoleView: ConsoleView) = isEnabled
}

private suspend fun Editor.setText(text: String) {
  withContext(Dispatchers.EDT) { document.setText(text) }
}

private suspend fun Editor.getFoldInfos() =
  withContext(Dispatchers.EDT) {
    foldingModel.allFoldRegions.map { it.toFoldInfo(this@getFoldInfos) }
  }
