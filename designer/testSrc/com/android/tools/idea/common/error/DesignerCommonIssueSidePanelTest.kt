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
package com.android.tools.idea.common.error

import com.android.tools.adtui.swing.findDescendant
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.tools.visuallint.VisualLintErrorType
import com.android.utils.HtmlBuilder
import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.tree.LeafState
import com.intellij.util.ui.UIUtil
import javax.swing.event.HyperlinkListener
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class DesignerCommonIssueSidePanelTest {

  @JvmField @Rule val rule = AndroidProjectRule.inMemory()

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_UI_CHECK_FIX_WITH_AI.clearOverride()
    StudioFlags.COMPOSE_RENDER_ERROR_FIX_WITH_AI.clearOverride()
  }

  @Test
  fun testHyperlinkListener() {
    val listener = HyperlinkListener {}
    val issue = TestIssue(hyperlinkListener = listener)
    val panel = DesignerCommonIssueSidePanel(rule.project, rule.testRootDisposable) { null }
    panel.loadIssueNode(TestIssueNode(issue))

    val descriptionPane = UIUtil.findComponentOfType(panel, DescriptionEditorPane::class.java)
    assertEquals(listener, descriptionPane!!.hyperlinkListeners.first())
  }

  @Test
  fun testLoadIssue() {
    val panel = DesignerCommonIssueSidePanel(rule.project, rule.testRootDisposable) { null }
    assertFalse(panel.loadIssueNode(null))
    assertFalse(panel.hasFirstComponent())

    assertFalse(panel.loadIssueNode(TestNode()))
    assertFalse(panel.hasFirstComponent())

    assertTrue(panel.loadIssueNode(TestIssueNode(TestIssue())))
    assertTrue(panel.hasFirstComponent())

    val hasContent = runInEdtAndGet {
      val file = rule.fixture.addFileToProject("path/to/file.xml", "")
      panel.loadIssueNode(IssueNode(file.virtualFile, TestIssue(), null))
    }
    assertTrue(hasContent)
    assertTrue(panel.hasFirstComponent())
  }

  @Test
  fun testFixWithAiButtonWhenFlagEnabled() {
    assertFixWithAiButton(fixWithAiFlagEnabled = true)
  }

  @Test
  fun testFixWithAiButtonNotVisibleWhenFlagDisabled() {
    assertFixWithAiButton(fixWithAiFlagEnabled = false)
  }

  @Test
  fun testFixWithAiButtonNotVisibleWhenComposeRenderErrorFlagIsOff() {
    StudioFlags.COMPOSE_RENDER_ERROR_FIX_WITH_AI.override(false)

    val issue = TestIssue()
    val panel =
      DesignerCommonIssueSidePanel(rule.project, rule.testRootDisposable) {
        object : AnAction("Fix with AI") {
          override fun actionPerformed(e: AnActionEvent) {}
        }
      }
    panel.loadIssueNode(TestIssueNode(issue))

    val actionToolbar = panel.findDescendant(ActionToolbar::class.java)
    assertNull(actionToolbar)
  }

  private fun assertFixWithAiButton(fixWithAiFlagEnabled: Boolean) {
    StudioFlags.COMPOSE_UI_CHECK_FIX_WITH_AI.override(fixWithAiFlagEnabled)

    val expectedVisualLintRenderIssue =
      VisualLintRenderIssue.builder()
        .summary("This is the summary of a visual lint render issue")
        .severity(HighlightSeverity.WARNING)
        .contentDescriptionProvider { HtmlBuilder() }
        .model(mock())
        .components(mutableListOf())
        .type(VisualLintErrorType.BOUNDS)
        .build()

    val expectedActionText = "FixWithAiButton"
    var currentVisualLintIssue: VisualLintRenderIssue? = null
    var currentActionEvent: AnActionEvent? = null
    val panel =
      DesignerCommonIssueSidePanel(rule.project, rule.testRootDisposable) { issue ->
        object : AnAction(expectedActionText) {
          override fun actionPerformed(e: AnActionEvent) {
            currentActionEvent = e
            assertTrue(
              "The provided issue is not of type VisualLintRenderIssue. Issue: $issue",
              issue is VisualLintRenderIssue
            )
            currentVisualLintIssue = issue as VisualLintRenderIssue
          }
        }
      }
    panel.loadIssueNode(createIssueNode(expectedVisualLintRenderIssue))

    val actionToolbar = panel.findDescendant(ActionToolbar::class.java)
    if (fixWithAiFlagEnabled) {
      assertNotNull(actionToolbar) { "ActionToolbar not found!" }

      // Validate only 1 button is added to the toolbar
      val actions = actionToolbar.actionGroup.getChildren(null)
      assertEquals(1, actions.size)

      // Check the button has been created (not null).
      val fixWithAiActionButton = actions.first()
      assertEquals(expectedActionText, fixWithAiActionButton.templateText)

      // Simulate a click and verify the action handler receives the expected issue.
      val expectedActionEvent = TestActionEvent.createTestEvent(fixWithAiActionButton)
      fixWithAiActionButton.actionPerformed(expectedActionEvent)
      assertEquals(expectedVisualLintRenderIssue, currentVisualLintIssue)
      assertEquals(expectedActionEvent, currentActionEvent)
    } else {
      assertNull(actionToolbar)
    }
  }

  private fun createIssueNode(issue: VisualLintRenderIssue): IssueNode =
    IssueNode(
      file = null,
      issue = issue,
      parent =
        object : DesignerCommonIssueNode(rule.project, null) {
          override fun updatePresentation(presentation: PresentationData) {
            /* do nothing */
          }

          override fun getName() = "node name"

          override fun getChildren(): List<DesignerCommonIssueNode> = emptyList()

          override fun getLeafState() = LeafState.DEFAULT
        },
    )
}
