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

import com.android.tools.idea.common.BackedTestFile
import com.android.tools.idea.testing.AndroidProjectRule
import junit.framework.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DesignerCommonIssueRootTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testCreateNoFileNode() {
    val issue1 = TestIssue("a")
    val issue2 = TestIssue("b")
    val issue3 = TestIssue("c")
    val issues = listOf(issue1, issue2, issue3)
    val provider = DesignerCommonIssueTestProvider(listOf(issue1, issue2, issue3))
    val root = DesignerCommonIssueRoot(null, provider)
    root.setComparator(DesignerCommonIssueNodeComparator(sortedBySeverity = true, sortedByName = true))

    // The issues have no source file results NoFileNode
    val noFileNode = NoFileNode(root)
    val issueNodes = issues.map { IssueNode(null, it, noFileNode) }.toList()

    assertEquals(listOf(noFileNode), root.getChildren())
    assertEquals(issueNodes, noFileNode.getChildren())
  }

  @Test
  fun testCreateIssuedFileNode() {
    val file = projectRule.fixture.addFileToProject("path/to/file", "").virtualFile
    val issue1 = TestIssue(summary = "issue1", source = (IssueSourceWithFile(file)))
    val issue2 = TestIssue(summary = "issue2", source = (IssueSourceWithFile(file)))
    val issues = listOf(issue1, issue2)

    val provider = DesignerCommonIssueTestProvider(issues)
    val root = DesignerCommonIssueRoot(null, provider)

    val issuedFileNode = IssuedFileNode(file, root)
    val child1 = IssueNode(file, issue1, issuedFileNode)
    val child2 = IssueNode(file, issue2, issuedFileNode)

    assertEquals(listOf(issuedFileNode), root.getChildren())
    assertEquals(listOf(child1, child2), issuedFileNode.getChildren())
  }

  @Test
  fun testChildrenOrder() {
    val file1 = projectRule.fixture.addFileToProject("path/to/file1", "").virtualFile
    val file2 = projectRule.fixture.addFileToProject("path/to/file2", "").virtualFile

    val file1Issue = TestIssue(source = (IssueSourceWithFile(file1)))
    val file2Issue = TestIssue(source = (IssueSourceWithFile(file2)))
    val noFileIssue = TestIssue()

    val issues = listOf(file2Issue, noFileIssue, file1Issue)

    val provider = DesignerCommonIssueTestProvider(issues)

    val root = DesignerCommonIssueRoot(null, provider)
    root.setComparator(DesignerCommonIssueNodeComparator(sortedBySeverity = true, sortedByName = true))

    // The order of node is sorted by the file name alphabetically.
    // And the NoFileNode will always be the last one.
    val expected = listOf(IssuedFileNode(file1, root), IssuedFileNode(file2, root), NoFileNode(root))
    assertEquals(expected, root.getChildren())
  }

  @Test
  fun testChildNodeIssueOrder() {
    val file = projectRule.fixture.addFileToProject("path/to/file", "").virtualFile

    val issue1 = TestIssue(source = (IssueSourceWithFile(file)))
    val issue2 = TestIssue(source = (IssueSourceWithFile(file)))
    val issue3 = TestIssue(source = (IssueSourceWithFile(file)))

    val issues = listOf(issue3, issue1, issue2)

    val provider = DesignerCommonIssueTestProvider(issues)
    val root = DesignerCommonIssueRoot(null, provider)
    root.setComparator(DesignerCommonIssueNodeComparator(sortedBySeverity = true, sortedByName = true))

    // The order of issues in same child node is same as the given order from IssueProvider
    val fileNode = IssuedFileNode(file, root)
    assertEquals(listOf(fileNode), root.getChildren())
    val children = issues.map { IssueNode(file, it, fileNode) }.toList()
    assertEquals(children, fileNode.getChildren())
  }

  @Test
  fun testCreateChildrenInComplicateCase() {
    val file1 = projectRule.fixture.addFileToProject("path/to/file1", "").virtualFile
    val file2 = projectRule.fixture.addFileToProject("path/to/file2", "").virtualFile

    val file1IssueA = TestIssue(summary = "file1 issueA", source = (IssueSourceWithFile(file1)))
    val file1IssueB = TestIssue(summary = "file1 issueB", source = (IssueSourceWithFile(file1)))
    val file1IssueC = TestIssue(summary = "file1 issueC", source = (IssueSourceWithFile(file1)))

    val file2IssueC = TestIssue(summary = "file2 issueC", source = (IssueSourceWithFile(file2)))
    val file2IssueB = TestIssue(summary = "file2 issueB", source = (IssueSourceWithFile(file2)))
    val file2IssueA = TestIssue(summary = "file2 issueA", source = (IssueSourceWithFile(file2)))

    val noFileIssueA = TestIssue(summary = "no file issueA")
    val noFileIssueB = TestIssue(summary = "no file issueB")
    val noFileIssueC = TestIssue(summary = "no file issueC")

    val allIssues = listOf(
      file1IssueA,
      file2IssueC,
      file1IssueB,
      noFileIssueA,
      file2IssueA,
      file1IssueC,
      file2IssueB,
      noFileIssueC,
      noFileIssueB,
    )

    val provider = DesignerCommonIssueTestProvider(allIssues)
    val root = DesignerCommonIssueRoot(null, provider)

    val fileNode1 = IssuedFileNode(file1, root)
    val fileNode1Children = listOf(file1IssueA, file1IssueB, file1IssueC).map { IssueNode(file1, it, fileNode1) }.toList()

    val fileNode2 = IssuedFileNode(file2, root)
    val fileNode2Children = listOf(file2IssueC, file2IssueA, file2IssueB).map { IssueNode(file2, it, fileNode2) }.toList()

    val noFileNode = NoFileNode(root)
    val noFileNodeChildren = listOf(noFileIssueA, noFileIssueC, noFileIssueB).map { IssueNode(null, it, noFileNode) }.toList()

    assertEquals(listOf(fileNode1, fileNode2, noFileNode), root.getChildren())
    assertEquals(fileNode1Children, fileNode1.getChildren())
    assertEquals(fileNode2Children, fileNode2.getChildren())
    assertEquals(noFileNodeChildren, noFileNode.getChildren())
  }

  @Test
  fun testCreateFileNodeWithBackedFile() {
    val file = projectRule.fixture.addFileToProject("path/to/file", "").virtualFile

    val backedFile1 = BackedTestFile("path/to/backed/file1", file)
    val backedFile2 = BackedTestFile("path/to/backed/file2", file)

    val file1Issue = TestIssue(summary = "issue1", source = (IssueSourceWithFile(backedFile1, "")))
    val file2Issue = TestIssue(summary = "issue2", source = (IssueSourceWithFile(backedFile2, "")))

    val provider = DesignerCommonIssueTestProvider(listOf(file1Issue, file2Issue))
    val root = DesignerCommonIssueRoot(null, provider)

    // File from same backed file should belong to same parent node.
    assertEquals(1, root.getChildren().size)
    val childrenNodes = root.getChildren().single().getChildren().toList()
    assertEquals(2, childrenNodes.size)
    assertEquals("issue1", (childrenNodes[0] as IssueNode).issue.summary)
    assertEquals("issue2", (childrenNodes[1] as IssueNode).issue.summary)
  }
}
