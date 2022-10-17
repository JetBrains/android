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

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes
import junit.framework.Assert
import org.junit.Test

class NoFileNodeTest {

  @Test
  fun testPresentationWithoutIssue() {
    // no issue case
    val node = NoFileNode(null)
    node.update()

    val expected = PresentationData()
    expected.addText(NO_FILE_NODE_NAME, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    expected.addText("  There is no problem", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    expected.setIcon(AllIcons.FileTypes.Xml)

    Assert.assertEquals(expected, node.presentation)
  }

  @Test
  fun testPresentationWithSingleIssue() {
    // single issue case
    val root = DesignerCommonIssueRoot(null, DesignerCommonIssueTestProvider(listOf(TestIssue())))
    val node = NoFileNode(root)
    node.update()

    val expected = PresentationData()
    expected.addText(NO_FILE_NODE_NAME, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    expected.addText("  1 problem", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    expected.setIcon(AllIcons.FileTypes.Xml)

    Assert.assertEquals(expected, node.presentation)
  }

  @Test
  fun testPresentationWithMultipleIssues() {
    // multiple issues case
    val root = DesignerCommonIssueRoot(null, DesignerCommonIssueTestProvider(listOf(TestIssue("a"), TestIssue("b"))))
    val node = NoFileNode(root)
    node.update()

    val expected = PresentationData()
    expected.addText(NO_FILE_NODE_NAME, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    expected.addText("  2 problems", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    expected.setIcon(AllIcons.FileTypes.Xml)

    Assert.assertEquals(expected, node.presentation)
  }

  @Test
  fun testSameNode() {
    run {
      val node1 = NoFileNode(null)
      val node2 = NoFileNode(null)
      Assert.assertEquals(node1, node2)
    }

    run {
      val root = DesignerCommonIssueRoot(null, DesignerCommonIssueTestProvider(emptyList()))
      val node1 = NoFileNode(root)
      val node2 = NoFileNode(root)
      Assert.assertEquals(node1, node2)
    }
  }
}
