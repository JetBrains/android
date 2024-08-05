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
package com.android.tools.idea.logcat.util

import com.android.tools.idea.logcat.message.LogLevel.INFO
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.DocumentAppender
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.LogcatColors
import com.android.tools.idea.logcat.messages.MessageFormatter
import com.android.tools.idea.logcat.messages.TextAccumulator
import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.android.tools.idea.logcat.util.FilterHint.AppName
import com.android.tools.idea.logcat.util.FilterHint.Level
import com.android.tools.idea.logcat.util.FilterHint.Tag
import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import java.time.ZoneId
import kotlin.test.fail
import org.junit.After
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class EditorUtilsTest {
  private val projectRule = ProjectRule()
  private val logcatEditorRule = LogcatEditorRule(projectRule)

  @get:Rule
  val rule = RuleChain(projectRule, WaitForIndexRule(projectRule), logcatEditorRule, EdtRule())

  private val editors = mutableListOf<Editor>()

  private val editor
    get() = logcatEditorRule.editor

  private val documentAppender
    get() = DocumentAppender(projectRule.project, editor.document, 1000000)

  private val formattingOptions = FormattingOptions()

  @After
  fun tearDown() {
    val editorFactory = EditorFactory.getInstance()
    editors.forEach { editorFactory.releaseEditor(it) }
  }

  @RunsInEdt
  @Test
  fun createLogcatEditor_correctSettings() {
    val editor = createLogcatEditor()
    assertThat(editor.gutterComponentEx.isPaintBackground).isFalse()

    assertThat(UndoUtil.isUndoDisabledFor(editor.document)).isTrue()

    val editorSettings = editor.settings
    assertThat(editorSettings.isAllowSingleLogicalLineFolding).isTrue()
    assertThat(editorSettings.isLineMarkerAreaShown).isFalse()
    assertThat(editorSettings.isIndentGuidesShown).isFalse()
    assertThat(editorSettings.isLineNumbersShown).isFalse()
    assertThat(editorSettings.isFoldingOutlineShown).isTrue()
    assertThat(editorSettings.isAdditionalPageAtBottom).isFalse()
    assertThat(editorSettings.additionalColumnsCount).isEqualTo(0)
    assertThat(editorSettings.additionalLinesCount).isEqualTo(0)
    assertThat(editorSettings.isRightMarginShown).isFalse()
    assertThat(editorSettings.isCaretRowShown).isFalse()
    assertThat(editorSettings.isShowingSpecialChars).isFalse()

    try {
      editor.document.insertString(0, "\r\n")
    } catch (e: AssertionError) {
      fail("Document should acceptSlashR")
    }
  }

  @Test
  fun getFilterHint_tag() {
    appendMessage(logcatMessage(tag = "tag"), formattingOptions)
    val width = formattingOptions.tagFormat.width()
    val expected = Tag("tag", width - 1)

    assertThat(editor.getFilterHint(editor.document.text.indexOf("tag"), formattingOptions))
      .isEqualTo(expected)
    assertThat(
        editor.getFilterHint(editor.document.text.indexOf("tag") + width - 1, formattingOptions)
      )
      .isEqualTo(expected)
    assertThat(editor.getFilterHint(editor.document.text.indexOf("tag") - 1, formattingOptions))
      .isNotEqualTo(expected)
    assertThat(editor.getFilterHint(editor.document.text.indexOf("tag") + width, formattingOptions))
      .isNotEqualTo(expected)
  }

  @Test
  fun getFilterHint_applicationId() {
    appendMessage(logcatMessage(appId = "package.name"), formattingOptions)
    val width = formattingOptions.appNameFormat.width()
    val expected = AppName("package.name", width - 1)

    assertThat(
        editor.getFilterHint(editor.document.text.indexOf("package.name"), formattingOptions)
      )
      .isEqualTo(expected)
    assertThat(
        editor.getFilterHint(
          editor.document.text.indexOf("package.name") + width - 1,
          formattingOptions,
        )
      )
      .isEqualTo(expected)
    assertThat(
        editor.getFilterHint(editor.document.text.indexOf("package.name") - 1, formattingOptions)
      )
      .isNotEqualTo(expected)
    assertThat(
        editor.getFilterHint(
          editor.document.text.indexOf("package.name") + width,
          formattingOptions,
        )
      )
      .isNotEqualTo(expected)
  }

  @Test
  fun getFilterHint_level() {
    appendMessage(logcatMessage(logLevel = INFO), formattingOptions)
    val width = formattingOptions.levelFormat.width()
    val expected = Level(INFO)

    assertThat(editor.getFilterHint(editor.document.text.indexOf(" I "), formattingOptions))
      .isEqualTo(expected)
    assertThat(
        editor.getFilterHint(editor.document.text.indexOf(" I ") + width - 1, formattingOptions)
      )
      .isEqualTo(expected)
    assertThat(editor.getFilterHint(editor.document.text.indexOf(" I ") - 1, formattingOptions))
      .isNotEqualTo(expected)
    assertThat(editor.getFilterHint(editor.document.text.indexOf(" I ") + width, formattingOptions))
      .isNotEqualTo(expected)
  }

  @Test
  fun scaledUi() {
    val uiSettings = UISettings.getInstance()
    try {
      val baseFontSize = createLogcatEditor().colorsScheme.consoleFontSize

      uiSettings.ideScale = 2.0f
      val scaledFontSize = createLogcatEditor().colorsScheme.consoleFontSize

      assertThat(scaledFontSize).isEqualTo(baseFontSize * 2)
    } finally {
      uiSettings.ideScale = 1.0f
    }
  }

  private fun appendMessage(logcatMessage: LogcatMessage, formattingOptions: FormattingOptions) {
    val messageFormatter = MessageFormatter(LogcatColors(), ZoneId.systemDefault())
    val textAccumulator = TextAccumulator()
    messageFormatter.formatMessages(formattingOptions, textAccumulator, listOf(logcatMessage))
    documentAppender.appendToDocument(textAccumulator)
  }

  private fun createLogcatEditor(): EditorEx {
    return createLogcatEditor(projectRule.project).also { editors.add(it) }
  }
}
