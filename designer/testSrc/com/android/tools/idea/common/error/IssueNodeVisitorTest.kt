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
package com.android.tools.idea.common.error

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.ide.IdeEventQueue
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class IssueNodeVisitorTest {

  @JvmField
  @Rule
  val rule = AndroidProjectRule.inMemory().onEdt()

  @RunsInEdt
  @Test
  fun testFindNode() {
    val issue1 = TestIssue("Issue 1")
    val issue2 = TestIssue("Issue 2")
    val issue3 = TestIssue("Issue 3")
    val issues = listOf(issue1, issue2, issue3)

    val provider = DesignerCommonIssueTestProvider(issues)
    val model = DesignerCommonIssueModel()
    val panel = DesignerCommonIssuePanel(rule.testRootDisposable, rule.project, model, provider) { "" }
    IdeEventQueue.getInstance().flushQueue()
    val tree = UIUtil.findComponentOfType(panel.getComponent(), Tree::class.java)!!

    val unknownIssueVisitor = IssueNodeVisitor(TestIssue("Unknown Issue"))
    panel.setSelectedNode(unknownIssueVisitor)
    IdeEventQueue.getInstance().flushQueue()
    // Cannot find the node with the given issue, nothing is selected.
    assertNull(tree.selectionPath)

    for (issue in issues) {
      val visitor = IssueNodeVisitor(issue)
      panel.setSelectedNode(visitor)
      IdeEventQueue.getInstance().flushQueue()
      assertEquals(issue, (tree.selectionPath!!.lastPathComponent as IssueNode).issue)
    }

    panel.setSelectedNode(unknownIssueVisitor)
    IdeEventQueue.getInstance().flushQueue()
    // The selection is not changed when the issue is not found.
    assertEquals(issue3, (tree.selectionPath!!.lastPathComponent as IssueNode).issue)
  }
}
