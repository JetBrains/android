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
import com.android.tools.idea.testing.onEdt
import com.intellij.ide.IdeEventQueue
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import javax.swing.tree.TreePath
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Suppress("UnstableApiUsage")
class DesignerCommonIssuePanelTest {

  @JvmField
  @Rule
  val rule = AndroidProjectRule.inMemory().onEdt()

  @RunsInEdt
  @Test
  fun testViewOptionFilter() {
    val infoSeverityIssue = TestIssue(severity = HighlightSeverity.INFORMATION)
    val warningSeverityIssue = TestIssue(severity = HighlightSeverity.WARNING)
    val provider = DesignerCommonIssueTestProvider(listOf(infoSeverityIssue, warningSeverityIssue))
    val model = DesignerCommonIssueModel()
    Disposer.register(rule.testRootDisposable, model)
    val panel = DesignerCommonIssuePanel(rule.testRootDisposable, rule.project, model, provider) { "" }
    // Make sure the Tree is added into DesignerCommonIssuePanel.
    IdeEventQueue.getInstance().flushQueue()
    val tree = UIUtil.findComponentOfType(panel.getComponent(), Tree::class.java)!!
    val treeModel = tree.model

    val root = (treeModel.root!! as DesignerCommonIssueRoot)
    root.setComparator(DesignerCommonIssueNodeComparator(sortedBySeverity = true, sortedByName = true))

    val noFileNode = root.getChildren().single() as NoFileNode
    run {
      panel.setViewOptionFilter { true }
      root.getNodeProvider().updateIssues(provider.getFilteredIssues())

      assertEquals(2, noFileNode.getChildren().size)
      assertEquals(warningSeverityIssue, (noFileNode.getChildren()[0].issue))
      assertEquals(infoSeverityIssue, (noFileNode.getChildren()[1].issue))
    }

    run {
      panel.setViewOptionFilter { !setOf(HighlightSeverity.INFORMATION.myVal).contains(it.severity.myVal) }
      root.getNodeProvider().updateIssues(provider.getFilteredIssues())

      assertEquals(1, noFileNode.getChildren().size)
      assertEquals(warningSeverityIssue, (noFileNode.getChildren()[0].issue))
    }

    run {
      panel.setViewOptionFilter { !setOf(HighlightSeverity.WARNING.myVal).contains(it.severity.myVal) }
      root.getNodeProvider().updateIssues(provider.getFilteredIssues())

      assertEquals(1, noFileNode.getChildren().size)
      assertEquals(infoSeverityIssue, (noFileNode.getChildren()[0].issue))
    }

    run {
      panel.setViewOptionFilter {
        !setOf(HighlightSeverity.INFORMATION.myVal, HighlightSeverity.WARNING.myVal).contains(it.severity.myVal)
      }
      root.getNodeProvider().updateIssues(provider.getFilteredIssues())

      // If there is no issue, then tree has no file node.
      assertEquals(0, root.getChildren().size)
    }
  }

  @RunsInEdt
  @Test
  fun testShowSidePanelWhenSelectIssueNode() {
    val provider = DesignerCommonIssueTestProvider(listOf(TestIssue(description = "some description")))
    val model = DesignerCommonIssueModel()
    Disposer.register(rule.testRootDisposable, model)
    val panel = DesignerCommonIssuePanel(rule.testRootDisposable, rule.project, model, provider) { "" }
    // Make sure the Tree is added into DesignerCommonIssuePanel.
    IdeEventQueue.getInstance().flushQueue()
    val tree = UIUtil.findComponentOfType(panel.getComponent(), Tree::class.java)!!

    val root = (tree.model.root!! as DesignerCommonIssueRoot)
    root.setComparator(DesignerCommonIssueNodeComparator(sortedBySeverity = true, sortedByName = true))
    val fileNode = root.getChildren().single() as NoFileNode
    val issueNode = fileNode.getChildren().single()
    val splitter = UIUtil.findComponentOfType(panel.getComponent(), OnePixelSplitter::class.java)!!

    tree.clearSelection()
    assertNull(splitter.secondComponent)

    // Show side panel when selecting issue node.
    tree.selectionPath = TreePath(issueNode)
    assertNotNull(splitter.secondComponent)

    // Selecting file node should not display side panel.
    tree.selectionPath = TreePath(fileNode)
    assertNull(splitter.secondComponent)
  }
}
