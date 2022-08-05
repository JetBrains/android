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
import junit.framework.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DesignerCommonIssueRootTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testCreateNoFileNode() {
    val provider = DesignerCommonIssueTestProvider(listOf(TestIssue(), TestIssue(), TestIssue()))
    val root = DesignerCommonIssueRoot(null, provider)

    // The issues have no source file results NoFileNode
    val expected = listOf(NoFileNode(listOf(TestIssue(), TestIssue(), TestIssue()), root))
    assertEquals(expected, root.getChildren())
  }

  @Test
  fun testCreateIssuedFileNode() {
    val file = projectRule.fixture.addFileToProject("path/to/file", "").virtualFile
    val issues = listOf(TestIssue(summary = "issue1", source = (IssueSourceWithFile(file))),
                        TestIssue(summary = "issue2", source = (IssueSourceWithFile(file))))

    val provider = DesignerCommonIssueTestProvider(issues)
    val root = DesignerCommonIssueRoot(null, provider)

    val expected = listOf(IssuedFileNode(file, issues, root))

    assertEquals(expected, root.getChildren())
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

    // The order of node is sorted by the file name alphabetically.
    // And the NoFileNode will always be the last one.
    val expected = listOf(IssuedFileNode(file1, listOf(file1Issue), root),
                          IssuedFileNode(file2, listOf(file2Issue), root),
                          NoFileNode(listOf(noFileIssue), root))
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

    // The order of issues in same child node is same as the given order from IssueProvider
    val expected = listOf(IssuedFileNode(file, listOf(issue3, issue1, issue2), root))
    assertEquals(expected, root.getChildren())
  }

  @Test
  fun testCreateChildrenInComplicateCase() {
    val file1 = projectRule.fixture.addFileToProject("path/to/file1", "").virtualFile
    val file2 = projectRule.fixture.addFileToProject("path/to/file2", "").virtualFile

    val file1IssueA = TestIssue(summary = "issueA", source = (IssueSourceWithFile(file1)))
    val file1IssueB = TestIssue(summary = "issueB", source = (IssueSourceWithFile(file1)))
    val file1IssueC = TestIssue(summary = "issueC", source = (IssueSourceWithFile(file1)))

    val file2IssueC = TestIssue(summary = "issueC", source = (IssueSourceWithFile(file2)))
    val file2IssueB = TestIssue(summary = "issueB", source = (IssueSourceWithFile(file2)))
    val file2IssueA = TestIssue(summary = "issueA", source = (IssueSourceWithFile(file2)))

    val noFileIssueA = TestIssue(summary = "issueA")
    val noFileIssueB = TestIssue(summary = "issueB")
    val noFileIssueC = TestIssue(summary = "issueC")

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

    val expected = listOf(IssuedFileNode(file1, listOf(file1IssueA, file1IssueB, file1IssueC), root),
                          IssuedFileNode(file2, listOf(file2IssueC, file2IssueA, file2IssueB), root),
                          NoFileNode(listOf(noFileIssueA, noFileIssueC, noFileIssueB), root))
    assertEquals(expected, root.getChildren())
  }
}
