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

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import javax.swing.event.HyperlinkListener
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesignerCommonIssueSidePanelTest {

  @JvmField
  @Rule
  val rule = AndroidProjectRule.inMemory()

  @Test
  fun testHyperlinkListener() {
    val listener = HyperlinkListener { }
    val issue = TestIssue(hyperlinkListener = listener)
    val panel = DesignerCommonIssueSidePanel(rule.project, rule.testRootDisposable)
    panel.loadIssueNode(TestIssueNode(issue))

    val descriptionPane = UIUtil.findComponentOfType(panel, DescriptionEditorPane::class.java)
    assertEquals(listener, descriptionPane!!.hyperlinkListeners.first())
  }

  @Test
  fun testLoadIssue() {
    val panel = DesignerCommonIssueSidePanel(rule.project, rule.testRootDisposable)
    assertFalse { panel.loadIssueNode(null) }
    assertFalse(panel.hasFirstComponent())
    assertFalse(panel.hasSecondComponent())

    assertFalse { panel.loadIssueNode(TestNode()) }
    assertFalse(panel.hasFirstComponent())
    assertFalse(panel.hasSecondComponent())

    assertTrue { panel.loadIssueNode(TestIssueNode(TestIssue())) }
    assertTrue(panel.hasFirstComponent())
    assertFalse(panel.hasSecondComponent())

    val hasContent = runInEdtAndGet {
      val file = rule.fixture.addFileToProject("path/to/file.xml", "")
      panel.loadIssueNode(IssueNode(file.virtualFile, TestIssue(), null))
    }
    assertTrue(hasContent)
    assertTrue(panel.hasFirstComponent())
    assertTrue(panel.hasSecondComponent())
  }
}
