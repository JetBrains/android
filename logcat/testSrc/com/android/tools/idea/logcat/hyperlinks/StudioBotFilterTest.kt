package com.android.tools.idea.logcat.hyperlinks

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.explainer.IssueExplainer
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.android.tools.idea.logcat.util.logcatMessage
import com.android.tools.idea.testing.ApplicationServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

/** Tests for [com.android.tools.idea.logcat.hyperlinks.StudioBotFilter] */
@RunsInEdt
class StudioBotFilterTest {
  private val projectRule = ProjectRule()
  private val logcatEditorRule = LogcatEditorRule(projectRule)

  private val mockIssueExplainer = mock<IssueExplainer>()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      logcatEditorRule,
      ApplicationServiceRule(IssueExplainer::class.java, mockIssueExplainer),
      EdtRule()
    )

  private val project
    get() = projectRule.project

  private val editor
    get() = logcatEditorRule.editor

  @Test
  fun applyFilter_detectsLink() {
    val filter = StudioBotFilter(editor)
    val line = "before (Ask Studio Bot) after"

    val result = filter.applyFilter(line, line.length) ?: fail()

    assertThat(result.resultItems.map { it.getText(line) }).containsExactly("(Ask Studio Bot)")
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

    val expectedQuestion =
      """
      Exception
      at com.example(File.kt:1) with tag ExampleTag
      """
        .trimIndent()
    verify(mockIssueExplainer).explain(project, expectedQuestion, IssueExplainer.RequestKind.LOGCAT)
  }
}

private fun LogcatMessage.formatMessage(): String =
  message.toString().replace("Exception", "Exception  (Ask Studio Bot)")

private fun ResultItem.getText(line: String) =
  line.substring(highlightStartOffset, highlightEndOffset)
