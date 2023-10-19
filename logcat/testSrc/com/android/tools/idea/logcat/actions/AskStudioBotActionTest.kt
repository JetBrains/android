/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.explainer.IssueExplainer
import com.android.tools.idea.explainer.IssueExplainer.RequestKind.LOGCAT
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.android.tools.idea.logcat.util.logcatMessage
import com.android.tools.idea.testing.ApplicationServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify

/** Tests for [AskStudioBotAction] */
@RunsInEdt
class AskStudioBotActionTest {
  private val projectRule = ProjectRule()
  private val logcatEditorRule = LogcatEditorRule(projectRule)

  private val mockIssueExplainer =
    spy(
      object : IssueExplainer() {
        override fun isAvailable() = true
      }
    )

  @get:Rule
  val rule =
    RuleChain(
      ApplicationRule(),
      ApplicationServiceRule(IssueExplainer::class.java, mockIssueExplainer),
      projectRule,
      logcatEditorRule,
      EdtRule(),
    )

  private val editor
    get() = logcatEditorRule.editor

  private val project
    get() = projectRule.project

  @Test
  fun update_noSelection() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(logcatMessage(tag = "MyTag", message = "Message 1"))
    editor.caretModel.moveToOffset(editor.document.textLength / 2)
    val action = AskStudioBotAction()

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo("Explain this log entry")
  }

  @Test
  fun update_noSelectionWithStackTrace() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(
      logcatMessage(tag = "MyTag", message = "Exception\n" + "\tat com.example(File.kt:1)")
    )
    editor.caretModel.moveToOffset(editor.document.textLength / 2)
    val action = AskStudioBotAction()

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo("Explain this crash")
  }

  @Test
  fun update_withSelection() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(
      logcatMessage(tag = "MyTag", message = "prefix <This is the selection> suffix")
    )
    editor.selectionModel.setSelection(
      editor.document.text.indexOf("<") + 1,
      editor.document.text.indexOf(">")
    )
    val action = AskStudioBotAction()

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo("Explain this selection")
  }

  @Test
  fun update_studioBotNotAvailable() {
    whenever(mockIssueExplainer.isAvailable()).thenReturn(false)
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(logcatMessage(tag = "MyTag", message = "Message 1"))
    editor.caretModel.moveToOffset(editor.document.textLength / 2)
    val action = AskStudioBotAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun actionPerformed_noSelection() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(logcatMessage(tag = "MyTag", message = "Message 1"))
    editor.caretModel.moveToOffset(editor.document.textLength / 2)
    val action = AskStudioBotAction()

    action.actionPerformed(event)

    verify(mockIssueExplainer).explain(project, "Message 1 with tag MyTag", LOGCAT)
  }

  @Test
  fun actionPerformed_noSelectionWithStackTrace() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(
      logcatMessage(tag = "MyTag", message = "Exception\n" + "\tat com.example(File.kt:1)")
    )
    editor.caretModel.moveToOffset(editor.document.textLength / 2)
    val action = AskStudioBotAction()

    action.actionPerformed(event)

    verify(mockIssueExplainer)
      .explain(
        project,
        """
        Exception
        at com.example(File.kt:1) with tag MyTag
      """
          .trimIndent(),
        LOGCAT
      )
  }

  @Test
  fun actionPerformed_withSelection() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(
      logcatMessage(tag = "MyTag", message = "prefix <This is the selection> suffix")
    )
    editor.selectionModel.setSelection(
      editor.document.text.indexOf("<") + 1,
      editor.document.text.indexOf(">")
    )
    val action = AskStudioBotAction()

    action.actionPerformed(event)

    verify(mockIssueExplainer).explain(project, "This is the selection", LOGCAT)
  }

  private fun testActionEvent(editor: EditorEx): AnActionEvent {
    return TestActionEvent.createTestEvent(
      MapDataContext().apply {
        put(LogcatPresenter.EDITOR, editor)
        put(CommonDataKeys.PROJECT, projectRule.project)
      }
    )
  }
}
