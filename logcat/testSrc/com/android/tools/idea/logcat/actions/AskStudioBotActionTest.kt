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

import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.android.tools.idea.logcat.util.logcatMessage
import com.android.tools.idea.studiobot.ChatService
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.buildPrompt
import com.android.tools.idea.testing.ApplicationServiceRule
import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
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

  private class MockStudioBot : StudioBot.StubStudioBot() {
    var available = true
    var contextAllowed = true

    override fun isAvailable() = available

    override fun isContextAllowed(project: Project) = contextAllowed

    private val _chatService = spy(object : ChatService.StubChatService() {})

    override fun chat(project: Project): ChatService = _chatService
  }

  @get:Rule
  val rule =
    RuleChain(
      ApplicationRule(),
      ApplicationServiceRule(StudioBot::class.java, MockStudioBot()),
      projectRule,
      WaitForIndexRule(projectRule),
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
    assertThat(event.presentation.text).isEqualTo("Explain This Log Entry")
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
    assertThat(event.presentation.text).isEqualTo("Explain This Crash")
  }

  @Test
  fun update_withSelection() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(
      logcatMessage(tag = "MyTag", message = "prefix <This is the selection> suffix")
    )
    editor.selectionModel.setSelection(
      editor.document.text.indexOf("<") + 1,
      editor.document.text.indexOf(">"),
    )
    val action = AskStudioBotAction()

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo("Explain This Selection")
  }

  @Test
  fun update_studioBotNotAvailable() {
    (StudioBot.getInstance() as MockStudioBot).available = false
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(logcatMessage(tag = "MyTag", message = "Message 1"))
    editor.caretModel.moveToOffset(editor.document.textLength / 2)
    val action = AskStudioBotAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun update_contextNotAllowed() {
    // The action *should* be available when the context sharing setting is
    // disabled, but its behavior may change.
    (StudioBot.getInstance() as MockStudioBot).contextAllowed = false
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(logcatMessage(tag = "MyTag", message = "Message 1"))
    editor.caretModel.moveToOffset(editor.document.textLength / 2)
    val action = AskStudioBotAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
  }

  @Test
  fun actionPerformed_noSelection() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(logcatMessage(tag = "MyTag", message = "Message 1"))
    editor.caretModel.moveToOffset(editor.document.textLength / 2)
    val action = AskStudioBotAction()

    action.actionPerformed(event)

    val prompt =
      buildPrompt(project) {
        userMessage { text("Explain this log entry: Message 1 with tag MyTag", emptyList()) }
      }

    verify(StudioBot.getInstance().chat(project))
      .sendChatQuery(prompt, StudioBot.RequestSource.LOGCAT)
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

    val prompt =
      buildPrompt(project) {
        userMessage {
          text(
            """
        Explain this crash: Exception
        at com.example(File.kt:1) with tag MyTag
      """
              .trimIndent(),
            emptyList(),
          )
        }
      }

    verify(StudioBot.getInstance().chat(project))
      .sendChatQuery(prompt, StudioBot.RequestSource.LOGCAT)
  }

  @Test
  fun actionPerformed_withSelection() {
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(
      logcatMessage(tag = "MyTag", message = "prefix <This is the selection> suffix")
    )
    editor.selectionModel.setSelection(
      editor.document.text.indexOf("<") + 1,
      editor.document.text.indexOf(">"),
    )
    val action = AskStudioBotAction()

    action.actionPerformed(event)

    val prompt =
      buildPrompt(project) {
        userMessage { text("Explain this selection: This is the selection", emptyList()) }
      }

    verify(StudioBot.getInstance().chat(project))
      .sendChatQuery(prompt, StudioBot.RequestSource.LOGCAT)
  }

  @Test
  fun actionPerformed_contextNotAllowed() {
    (StudioBot.getInstance() as MockStudioBot).contextAllowed = false
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(logcatMessage(tag = "MyTag", message = "Message 1"))
    editor.caretModel.moveToOffset(editor.document.textLength / 2)
    val action = AskStudioBotAction()

    action.actionPerformed(event)

    verify(StudioBot.getInstance().chat(project))
      .stageChatQuery(
        "Explain this log entry: Message 1 with tag MyTag",
        StudioBot.RequestSource.LOGCAT,
      )
  }

  private fun testActionEvent(editor: EditorEx): AnActionEvent {
    TestActionEvent.createTestEvent {}
    return TestActionEvent.createTestEvent(
      SimpleDataContext.builder()
        .add(LogcatPresenter.EDITOR, editor)
        .add(CommonDataKeys.PROJECT, projectRule.project)
        .build()
    )
  }
}
