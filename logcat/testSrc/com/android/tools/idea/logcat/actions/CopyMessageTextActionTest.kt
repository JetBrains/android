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

import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.LOGCAT_MESSAGE_KEY
import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.android.tools.idea.logcat.util.logcatMessage
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.RangeMarker
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

/**
 * Tests for [CopyMessageTextAction]
 */
@RunsInEdt
class CopyMessageTextActionTest {
  private val projectRule = ProjectRule()
  private val logcatEditorRule = LogcatEditorRule(projectRule)

  @get:Rule
  val rule = RuleChain(projectRule, logcatEditorRule, EdtRule())

  private val editor get() = logcatEditorRule.editor

  /**
   * RangeMarker's are kept in the Document as weak reference (see IntervalTreeImpl#createGetter) so we need to keep them alive as long as
   * they are valid.
   */
  private val markers = mutableListOf<RangeMarker>()

  @Test
  fun update_emptyDocument() {
    val event = testActionEvent(editor)
    val action = CopyMessageTextAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun update_noSelection() {
    val event = testActionEvent(editor)
    editor.putLogcatMessages(logcatMessage(message = "Message 1"))
    editor.selectionModel.removeSelection()
    val action = CopyMessageTextAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun update_emptySelection() {
    val event = testActionEvent(editor)
    editor.putLogcatMessages(logcatMessage(message = "Message 1"))
    editor.caretModel.moveToOffset(editor.document.textLength / 2)
    val action = CopyMessageTextAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo("Copy Message Text")
  }

  @Test
  fun update_wholeLineSelection() {
    val event = testActionEvent(editor)
    editor.putLogcatMessages(
      logcatMessage(message = "Message 1"),
      logcatMessage(message = "Message 2"),
      logcatMessage(message = "Message 3"),
    )

    editor.selectionModel.setSelection(editor.document.getLineStartOffset(1), editor.document.getLineStartOffset(2))
    val action = CopyMessageTextAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo("Copy Message Text")
  }

  @Test
  fun update_multiLineSelection() {
    val event = testActionEvent(editor)
    editor.putLogcatMessages(
      logcatMessage(message = "Message 1"),
      logcatMessage(message = "Message 2"),
      logcatMessage(message = "Message 3"),
    )

    editor.selectionModel.setSelection(editor.document.getLineStartOffset(1) - 1, editor.document.getLineStartOffset(2) + 1)
    val action = CopyMessageTextAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo("Copy Messages Text")
  }

  @Test
  fun actionPerformed_emptySelection() {
    val event = testActionEvent(editor)
    editor.putLogcatMessages(logcatMessage(message = "Message 1"))
    editor.caretModel.moveToOffset(editor.document.textLength / 2)
    val action = CopyMessageTextAction()

    action.actionPerformed(event)

    assertThat(getClipboardText()).isEqualTo("Message 1\n")
  }

  @Test
  fun actionPerformed_wholeLineSelection() {
    val event = testActionEvent(editor)
    editor.putLogcatMessages(
      logcatMessage(message = "Message 1"),
      logcatMessage(message = "Message 2"),
      logcatMessage(message = "Message 3"),
    )

    editor.selectionModel.setSelection(editor.document.getLineStartOffset(1), editor.document.getLineStartOffset(2))
    val action = CopyMessageTextAction()

    action.actionPerformed(event)

    assertThat(getClipboardText()).isEqualTo("Message 2\n")
  }

  @Test
  fun actionPerformed_multiLineSelection() {
    val event = testActionEvent(editor)
    editor.putLogcatMessages(
      logcatMessage(message = "Message 1"),
      logcatMessage(message = "Message 2"),
      logcatMessage(message = "Message 3"),
    )

    editor.selectionModel.setSelection(editor.document.getLineStartOffset(1) - 1, editor.document.getLineStartOffset(2) + 1)
    val action = CopyMessageTextAction()

    action.actionPerformed(event)

    assertThat(getClipboardText()).isEqualTo("""
      Message 1
      Message 2
      Message 3

    """.trimIndent())
  }

  private fun EditorEx.putLogcatMessages(vararg messages: LogcatMessage) {
    messages.forEach {
      val start = document.textLength
      val text = it.toString()
      document.insertString(start, "$text\n")
      document.createRangeMarker(start, start + text.length).apply {
        putUserData(LOGCAT_MESSAGE_KEY, it)
        markers.add(this)
      }
    }
  }
}

private fun testActionEvent(editor: EditorEx): TestActionEvent {
  return TestActionEvent(MapDataContext().apply {
    put(CommonDataKeys.EDITOR, editor)
  })
}

private fun getClipboardText() = CopyPasteManager.getInstance().contents?.getTransferData(DataFlavor.stringFlavor)
