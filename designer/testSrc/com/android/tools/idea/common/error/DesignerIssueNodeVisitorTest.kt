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

import com.android.SdkConstants
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertInstanceOf
import com.intellij.ui.tree.TreePathUtil
import com.intellij.ui.tree.TreeVisitor
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class DesignerIssueNodeVisitorTest {

  @JvmField
  @Rule
  val rule = EdtAndroidProjectRule(AndroidProjectRule.inMemory())

  @RunsInEdt
  @Test
  fun testContinueVisitingNodesWhenIssueSummaryIsDifferent() {
    val errorType = VisualLintErrorType.WEAR_MARGIN
    val model = NlModelBuilderUtil.model(
      rule.projectRule,
      "layout",
      "layout.xml",
      ComponentDescriptor(SdkConstants.FRAME_LAYOUT)
        .withBounds(0, 0, 1000, 1000)
        .matchParentWidth()
        .matchParentHeight()
        .children(ComponentDescriptor(SdkConstants.TEXT_VIEW)
                    .width("100dp")
                    .height("20dp")
                    .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_IGNORE, errorType.ignoredAttributeValue)
        )
    ).build()

    val issue1 = createTestVisualLintRenderIssue(errorType, model.components.first().children, "summaryA")
    val issue2 = createTestVisualLintRenderIssue(errorType, model.components.first().children, "summaryB")

    val node1 = TestIssueNode(issue1)
    val node2 = TestIssueNode(issue2)

    val visitor = DesignerIssueNodeVisitor(node1)
    assertEquals(TreeVisitor.Action.CONTINUE, visitor.visit (TreePathUtil.pathToCustomNode(node2) { null }))
  }

  @Test
  fun testVisitIssuedFileNode() {
    val mockedFile = mock<VirtualFile>()
    val comparedNode = IssuedFileNode(mockedFile, emptyList(), null)
    val visitor = DesignerIssueNodeVisitor(comparedNode)

    run {
      val testNode = IssuedFileNode(mockedFile, emptyList(), null)
      val path = TreePathUtil.pathToCustomNode(testNode) { null }
      assertEquals(TreeVisitor.Action.INTERRUPT, visitor.visit(path))
    }

    run {
      // IssuedFileNode only cares if the file is same or not.
      val testNode = IssuedFileNode(mockedFile, listOf(TestIssue()), null)
      val path = TreePathUtil.pathToCustomNode(testNode) { null }
      assertEquals(TreeVisitor.Action.INTERRUPT, visitor.visit(path))
    }

    run {
      // Test node with different file
      val testNode = IssuedFileNode(mock(), listOf(TestIssue()), null)
      val path = TreePathUtil.pathToCustomNode(testNode) { null }
      assertEquals(TreeVisitor.Action.CONTINUE, visitor.visit(path))
    }

    run {
      // Test node with different parent
      val testNode = IssuedFileNode(mock(), listOf(TestIssue()), TestNode())
      val path = TreePathUtil.pathToCustomNode(testNode) { null }
      assertEquals(TreeVisitor.Action.CONTINUE, visitor.visit(path))
    }
  }

  @Test
  fun testVisitNoFileNode() {
    val comparedNode = NoFileNode(emptyList(), null)
    val visitor = DesignerIssueNodeVisitor(comparedNode)

    run {
      val testNode = NoFileNode(emptyList(), null)
      val path = TreePathUtil.pathToCustomNode(testNode) { null }
      assertEquals(TreeVisitor.Action.INTERRUPT, visitor.visit(path))
    }

    run {
      // Test node with has different parent
      val testNode = NoFileNode(listOf(TestIssue()), null)
      val path = TreePathUtil.pathToCustomNode(testNode) { null }
      assertEquals(TreeVisitor.Action.INTERRUPT, visitor.visit(path))
    }

    run {
      val testNode = IssuedFileNode(mock(), listOf(TestIssue()), null)
      val path = TreePathUtil.pathToCustomNode(testNode) { null }
      assertEquals(TreeVisitor.Action.CONTINUE, visitor.visit(path))
    }

    run {
      // Test node with different parent
      val testNode = NoFileNode(listOf(TestIssue()), TestNode())
      val path = TreePathUtil.pathToCustomNode(testNode) { null }
      assertEquals(TreeVisitor.Action.CONTINUE, visitor.visit(path))
    }
  }

  @Test
  fun testVisitIssueNode() {
    val comparedNode = IssueNode(null, TestIssue(), null)
    val visitor = DesignerIssueNodeVisitor(comparedNode)

    run {
      val testNode = IssueNode(null, TestIssue(), null)
      val path = TreePathUtil.pathToCustomNode(testNode) { null }
      assertEquals(TreeVisitor.Action.INTERRUPT, visitor.visit(path))
    }

    run {
      val testNode = IssueNode(null, TestIssue(summary = "Test"), null)
      val path = TreePathUtil.pathToCustomNode(testNode) { null }
      assertEquals(TreeVisitor.Action.CONTINUE, visitor.visit(path))
    }

    run {
      val testNode = IssueNode(null, TestIssue(), TestNode(parentDescriptor = null))
      val path = TreePathUtil.pathToCustomNode(testNode) { null }
      assertEquals(TreeVisitor.Action.CONTINUE, visitor.visit(path))
    }

    run {
      val testNode = NoFileNode(emptyList(), null)
      val path = TreePathUtil.pathToCustomNode(testNode) { null }
      assertEquals(TreeVisitor.Action.CONTINUE, visitor.visit(path))
    }

    // TODO: Test VisualLintRenderIssue case.
  }

  @Test
  fun testVisitDifferentNodeTypes() {
    val mockedFile = mock<VirtualFile>()
    val node1 = IssuedFileNode(mockedFile, emptyList(), null)
    val visitor = DesignerIssueNodeVisitor(node1)
    val node2 = NoFileNode(emptyList(), null)

    val path = TreePathUtil.pathToCustomNode(node2) { null }
    assertEquals(TreeVisitor.Action.CONTINUE, visitor.visit(path))
  }

  @Test
  fun testVisitOtherNodeTypes() {
    val node1 = NoFileNode(emptyList(), null)
    val visitor = DesignerIssueNodeVisitor(node1)
    val node2: DesignerCommonIssueNode = mock()
    val path = TreePathUtil.pathToCustomNode(node2) { null }
    assertEquals(TreeVisitor.Action.CONTINUE, visitor.visit(path))
  }
}