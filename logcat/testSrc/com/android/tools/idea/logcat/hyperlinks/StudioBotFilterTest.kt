package com.android.tools.idea.logcat.hyperlinks

import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.android.tools.idea.logcat.util.logcatMessage
import com.android.tools.idea.studiobot.ChatService
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.buildPrompt
import com.android.tools.idea.testing.ApplicationServiceRule
import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify

/** Tests for [com.android.tools.idea.logcat.hyperlinks.StudioBotFilter] */
@RunsInEdt
class StudioBotFilterTest {
  private val projectRule = ProjectRule()
  private val logcatEditorRule = LogcatEditorRule(projectRule)

  private val mockChatService = Mockito.spy(object : ChatService.StubChatService() {})

  private val mockStudioBot =
    object : StudioBot.StubStudioBot() {
      var contextAllowed = true

      override fun isAvailable() = true

      override fun isContextAllowed(project: Project) = contextAllowed

      override fun chat(project: Project) = mockChatService
    }

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      WaitForIndexRule(projectRule),
      logcatEditorRule,
      ApplicationServiceRule(StudioBot::class.java, mockStudioBot),
      EdtRule(),
    )

  private val project
    get() = projectRule.project

  private val editor
    get() = logcatEditorRule.editor

  @Test
  fun applyFilter_detectsLink() {
    val filter = StudioBotFilter(editor)
    val line = "before (Ask Gemini) after"

    val result = filter.applyFilter(line, line.length) ?: fail()

    assertThat(result.resultItems.map { it.getText(line) }).containsExactly("(Ask Gemini)")
  }

  @Test
  fun applyFilter_linkNavigate() {
    val filter = StudioBotFilter(editor)
    val message = logcatMessage(message = "Exception\n" + "\tat com.example(File.kt:1)")
    logcatEditorRule.putLogcatMessages(message, formatMessage = LogcatMessage::formatMessage)
    val line = editor.document.text.split("\n")[0]
    editor.caretModel.moveToOffset(editor.document.text.indexOf("StudioBot"))
    val result = filter.applyFilter(line, line.length) ?: fail()

    result.firstHyperlinkInfo?.navigate(project)

    val expectedPrompt =
      buildPrompt(project) {
        userMessage {
          text(
            """
      Explain: Exception
      at com.example(File.kt:1) with tag ExampleTag
      """
              .trimIndent(),
            emptyList(),
          )
        }
      }

    // With the context sharing setting enabled, the AiExcludeService should be invoked
    // to validate a query to be sent directly to the model
    verify(mockChatService).sendChatQuery(expectedPrompt, StudioBot.RequestSource.LOGCAT)
  }

  @Test
  fun applyFilter_stagesQueryWhenContextDisabled() {
    // Disable the context sharing setting
    mockStudioBot.contextAllowed = false
    val filter = StudioBotFilter(editor)
    val message = logcatMessage(message = "Exception\n" + "\tat com.example(File.kt:1)")
    logcatEditorRule.putLogcatMessages(message, formatMessage = LogcatMessage::formatMessage)
    val line = editor.document.text.split("\n")[0]
    editor.caretModel.moveToOffset(editor.document.text.indexOf("StudioBot"))
    val result = filter.applyFilter(line, line.length) ?: fail()

    result.firstHyperlinkInfo?.navigate(project)

    val expectedQuestion =
      """
      Explain: Exception
      at com.example(File.kt:1) with tag ExampleTag
      """
        .trimIndent()

    // With context sharing disabled, the query should be staged instead of sent
    verify(mockChatService).stageChatQuery(expectedQuestion, StudioBot.RequestSource.LOGCAT)
  }
}

private fun LogcatMessage.formatMessage(): String =
  message.replace("Exception", "Exception  (Ask Gemini)")

private fun ResultItem.getText(line: String) =
  line.substring(highlightStartOffset, highlightEndOffset)
