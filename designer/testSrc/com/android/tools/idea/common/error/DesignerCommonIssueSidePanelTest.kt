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
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.tree.LeafState
import com.intellij.util.ui.UIUtil
import javax.swing.JButton
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
  }

  @Test
  fun testHyperlinkListener() {
    val listener = HyperlinkListener {}
    val issue = TestIssue(hyperlinkListener = listener)
    val panel = DesignerCommonIssueSidePanel(rule.project, rule.testRootDisposable) {}
    panel.loadIssueNode(TestIssueNode(issue))

    val descriptionPane = UIUtil.findComponentOfType(panel, DescriptionEditorPane::class.java)
    assertEquals(listener, descriptionPane!!.hyperlinkListeners.first())
  }

  @Test
  fun testLoadIssue() {
    val panel = DesignerCommonIssueSidePanel(rule.project, rule.testRootDisposable) {}
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

    var actualVisualLintIssue: VisualLintRenderIssue? = null
    val panel =
      DesignerCommonIssueSidePanel(rule.project, rule.testRootDisposable) {
        actualVisualLintIssue = it
      }
    panel.loadIssueNode(createIssueNode(expectedVisualLintRenderIssue))

    val fixWithAiButton = panel.findDescendant<JButton> { it.name == FIX_WITH_AI_BUTTON_NAME }

    if (fixWithAiFlagEnabled) {
      // Check the button has been created (not null).
      assertNotNull(fixWithAiButton)

      // Simulate a click and verify the action handler receives the expected issue.
      fixWithAiButton.doClick()
      assertEquals(expectedVisualLintRenderIssue, actualVisualLintIssue)
    } else {
      // If the button is not visible is not created, thus is null.
      assertNull(fixWithAiButton)
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
