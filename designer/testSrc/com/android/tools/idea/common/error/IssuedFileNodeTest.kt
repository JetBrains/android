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
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes
import junit.framework.Assert
import org.junit.Rule
import org.junit.Test

@Suppress("DialogTitleCapitalization")
class IssuedFileNodeTest {
  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testPresentationWithoutIssue() {
    val file = projectRule.fixture.addFileToProject("path/to/fileName", "content").virtualFile
    // no issue case
    val node = IssuedFileNode(file, null)
    node.update()

    val expected = PresentationData()
    expected.addText("fileName", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    expected.addText("  /src/path/to", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    expected.addText("  There is no problem", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    expected.setIcon(AllIcons.FileTypes.Any_type)

    Assert.assertEquals(expected, node.presentation)
  }

  @Test
  fun testPresentationWithSingleIssue() {
    // single issue case
    val file = projectRule.fixture.addFileToProject("path/to/fileName", "content").virtualFile
    val root = DesignerCommonIssueRoot(null, DesignerCommonIssueTestProvider(listOf(TestIssue(source = IssueSourceWithFile(file)))))
    val node = IssuedFileNode(file, root)
    node.update()

    val expected = PresentationData()
    expected.addText("fileName", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    expected.addText("  /src/path/to", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    expected.addText("  1 problem", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    expected.setIcon(AllIcons.FileTypes.Any_type)

    Assert.assertEquals(expected, node.presentation)
  }

  @Test
  fun testPresentationWithMultipleIssues() {
    // multiple issues case
    val file = projectRule.fixture.addFileToProject("path/to/fileName", "content").virtualFile
    val root = DesignerCommonIssueRoot(null, DesignerCommonIssueTestProvider(
      listOf(TestIssue(source = IssueSourceWithFile(file)), TestIssue(source = IssueSourceWithFile(file))))
    )
    val node = IssuedFileNode(file, root)
    node.update()

    val expected = PresentationData()
    expected.addText("fileName", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    expected.addText("  /src/path/to", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    expected.addText("  2 problems", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    expected.setIcon(AllIcons.FileTypes.Any_type)

    Assert.assertEquals(expected, node.presentation)
  }

  @Test
  fun testSameNode() {
    val file = projectRule.fixture.addFileToProject("path/to/file", "content").virtualFile

    val node1 = IssuedFileNode(file, null)
    val node2 = IssuedFileNode(file, null)
    Assert.assertEquals(node1, node2)
  }
}
