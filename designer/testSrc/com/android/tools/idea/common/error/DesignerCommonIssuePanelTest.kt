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
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import javax.swing.tree.TreePath
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesignerCommonIssuePanelTest {

  @JvmField
  @Rule
  val rule = EdtAndroidProjectRule(AndroidProjectRule.inMemory())

  @Test
  fun testHideSeverity() {
    val infoSeverityIssue = TestIssue(severity = HighlightSeverity.INFORMATION)
    val warningSeverityIssue = TestIssue(severity = HighlightSeverity.WARNING)
    val provider = DesignerCommonIssueTestProvider(listOf(infoSeverityIssue, warningSeverityIssue))
    val model = DesignerCommonIssueModel()
    Disposer.register(rule.testRootDisposable, model)
    val panel = DesignerCommonIssuePanel(rule.testRootDisposable, rule.project, model, provider)
    val tree = UIUtil.findComponentOfType(panel.getComponent(), Tree::class.java)!!
    val treeModel = tree.model

    val root = (treeModel.root!! as DesignerCommonIssueRoot)

    run {
      panel.setHiddenSeverities(emptySet())
      val parentNode = root.getChildren().single() as NoFileNode
      assertEquals(2, parentNode.issues.size)
      assertTrue(parentNode.issues.contains(infoSeverityIssue))
      assertTrue(parentNode.issues.contains(warningSeverityIssue))
    }

    run {
      panel.setHiddenSeverities(setOf(HighlightSeverity.INFORMATION.myVal))
      val parentNode = root.getChildren().single() as NoFileNode
      assertEquals(1, parentNode.issues.size)
      assertTrue(parentNode.issues.contains(warningSeverityIssue))
    }

    run {
      panel.setHiddenSeverities(setOf(HighlightSeverity.WARNING.myVal))
      val parentNode = root.getChildren().single() as NoFileNode
      assertEquals(1, parentNode.issues.size)
      assertTrue(parentNode.issues.contains(infoSeverityIssue))
    }

    run {
      panel.setHiddenSeverities(setOf(HighlightSeverity.INFORMATION.myVal, HighlightSeverity.WARNING.myVal))
      // If there is no issue, then tree has no file node.
      assertEquals(0, root.getChildren().size)
    }
  }

  @Test
  fun testShowSidePanelWhenSelectIssueNode() {
    val provider = DesignerCommonIssueTestProvider(listOf(TestIssue(description = "some description")))
    val model = DesignerCommonIssueModel()
    Disposer.register(rule.testRootDisposable, model)
    val panel = DesignerCommonIssuePanel(rule.testRootDisposable, rule.project, model, provider)
    val tree = UIUtil.findComponentOfType(panel.getComponent(), Tree::class.java)!!

    val root = (tree.model.root!! as DesignerCommonIssueRoot)
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
