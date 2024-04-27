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

import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.android.tools.idea.logcat.util.logcatMessage
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test
import java.awt.datatransfer.DataFlavor

/** Tests for [CopyMessageTextAction] */
@RunsInEdt
class CopyMessageTextActionTest {
  private val projectRule = ProjectRule()
  private val logcatEditorRule = LogcatEditorRule(projectRule)

  @get:Rule val rule = RuleChain(projectRule, logcatEditorRule, EdtRule())

  private val editor
    get() = logcatEditorRule.editor

  @Test
  fun update_emptyDocument() {
    val event = testActionEvent(editor)
    val action = CopyMessageTextAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun update_noMessages() {
    val event = testActionEvent(editor)
    val action = CopyMessageTextAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun update_emptySelection() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(logcatMessage(message = "Message 1"))
    editor.caretModel.moveToOffset(editor.document.textLength / 2)
    val action = CopyMessageTextAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo("Copy Message Text")
  }

  @Test
  fun update_wholeLineSelection() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(
      logcatMessage(message = "Message 1"),
      logcatMessage(message = "Message 2"),
      logcatMessage(message = "Message 3"),
    )

    editor.selectionModel.setSelection(
      editor.document.getLineStartOffset(1),
      editor.document.getLineStartOffset(2)
    )
    val action = CopyMessageTextAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo("Copy Message Text")
  }

  @Test
  fun update_multiLineSelection() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(
      logcatMessage(message = "Message 1"),
      logcatMessage(message = "Message 2"),
      logcatMessage(message = "Message 3"),
    )

    editor.selectionModel.setSelection(
      editor.document.getLineStartOffset(1) - 1,
      editor.document.getLineStartOffset(2) + 1
    )
    val action = CopyMessageTextAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo("Copy Messages Text")
  }

  @Test
  fun actionPerformed_emptySelection() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(logcatMessage(message = "Message 1"))
    editor.caretModel.moveToOffset(editor.document.textLength / 2)
    val action = CopyMessageTextAction()

    action.actionPerformed(event)

    assertThat(getClipboardText()).isEqualTo("Message 1\n")
  }

  @Test
  fun actionPerformed_wholeLineSelection() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(
      logcatMessage(message = "Message 1"),
      logcatMessage(message = "Message 2"),
      logcatMessage(message = "Message 3"),
    )

    editor.selectionModel.setSelection(
      editor.document.getLineStartOffset(1),
      editor.document.getLineStartOffset(2)
    )
    val action = CopyMessageTextAction()

    action.actionPerformed(event)

    assertThat(getClipboardText()).isEqualTo("Message 2\n")
  }

  @Test
  fun actionPerformed_multiLineSelection() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(
      logcatMessage(message = "Message 1"),
      logcatMessage(message = "Message 2"),
      logcatMessage(message = "Message 3"),
    )

    editor.selectionModel.setSelection(
      editor.document.getLineStartOffset(1) - 1,
      editor.document.getLineStartOffset(2) + 1
    )
    val action = CopyMessageTextAction()

    action.actionPerformed(event)

    assertThat(getClipboardText())
      .isEqualTo(
        """
      Message 1
      Message 2
      Message 3

    """
          .trimIndent()
      )
  }
}

private fun testActionEvent(editor: EditorEx): AnActionEvent {
  return TestActionEvent.createTestEvent(
    MapDataContext().apply { put(LogcatPresenter.EDITOR, editor) }
  )
}

private fun getClipboardText() =
  CopyPasteManager.getInstance().contents?.getTransferData(DataFlavor.stringFlavor)
