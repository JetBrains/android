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
package com.android.tools.idea.logcat.actions

import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [LogcatFoldLinesLikeThisAction]
 */
@RunsInEdt
class LogcatFoldLinesLikeThisActionTest {
  private val projectRule = ProjectRule()
  private val logcatEditorRule = LogcatEditorRule(projectRule)

  @get:Rule
  val rule = RuleChain(projectRule, logcatEditorRule, EdtRule())

  private val editor get() = logcatEditorRule.editor

  @Test
  fun update_noSelection_visibleAndEnabled() {
    val action = LogcatFoldLinesLikeThisAction(editor)
    editor.setText(
      """
      foo
      bar
      """.trimIndent(),
      caret = 0
    )
    val event = TestActionEvent()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun update_singleLineSelection_visibleAndEnabled() {
    val action = LogcatFoldLinesLikeThisAction(editor)
    editor.setText(
      """
      foo
      bar
      """.trimIndent(),
      caret = 0,
      selectionEnd = 2
    )
    val event = TestActionEvent()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun update_multiLineSelection_invisible() {
    val action = LogcatFoldLinesLikeThisAction(editor)
    editor.setText(
      """
      foo
      bar
      """.trimIndent(),
      caret = 0,
      selectionEnd = 5
    )
    val event = TestActionEvent()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
  }
}

private fun Editor.setText(text: String, caret: Int, selectionEnd: Int = caret) {
  document.setText(text)
  caretModel.moveToOffset(caret)
  if (selectionEnd > caret) {
    selectionModel.setSelection(caret, selectionEnd)
  }
}