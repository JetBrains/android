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

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

@RunsInEdt
class EditorUtilsTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  @RunsInEdt
  @Test
  fun createLogcatEditor() {
    val editor = createLogcatEditor(projectRule.project)
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
    finally {
      EditorFactory.getInstance().releaseEditor(editor)
    }
  }
}
