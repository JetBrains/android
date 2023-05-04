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

import com.android.tools.idea.explainer.IssueExplainer
import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.android.tools.idea.logcat.util.logcatMessage
import com.android.tools.idea.testing.ApplicationServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test

/** Tests for [ExplainLogcatCrashAction] */
@RunsInEdt
class ExplainLogcatCrashActionTest {

  val projectRule = ProjectRule()

  private val logcatEditorRule = LogcatEditorRule(projectRule)

  private val testIssueExplainer =
    object : IssueExplainer() {

      override fun isAvailable(): Boolean = true

      val requests = mutableListOf<String>()

      override fun explain(
        project: Project,
        request: String,
        requestKind: RequestKind,
        extraDocumentation: String?,
        extraUrls: List<String>
      ) {
        if (requestKind == RequestKind.LOGCAT) {
          requests += request
        }
      }
    }

  @get:Rule
  val rule1 = RuleChain(projectRule, logcatEditorRule, EdtRule())

  @get:Rule
  val rule2 =
    RuleChain(
      ApplicationRule(),
      ApplicationServiceRule(IssueExplainer::class.java, testIssueExplainer)
    )

  private val editor
    get() = logcatEditorRule.editor

  @Test
  fun testActionPerformedEmptySelection() {
    testIssueExplainer.requests.clear()
    val event = testActionEvent(editor)
    logcatEditorRule.putLogcatMessages(logcatMessage(message = "Message 1"))
    editor.caretModel.moveToOffset(editor.document.textLength / 2)
    val action = ExplainLogcatCrashAction()

    action.actionPerformed(event)

    assertThat(testIssueExplainer.requests[0]).isEqualTo("Message 1 with tag ExampleTag\n")
  }

  private fun testActionEvent(editor: EditorEx): AnActionEvent {
    return TestActionEvent.createTestEvent(
      MapDataContext().apply {
        put(CommonDataKeys.EDITOR, editor)
        put(CommonDataKeys.PROJECT, projectRule.project)
      }
    )
  }
}
