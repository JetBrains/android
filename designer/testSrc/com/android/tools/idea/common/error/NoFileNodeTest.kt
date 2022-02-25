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
    val node = NoFileNode(emptyList(), null)
    node.update()

    val expected = PresentationData()
    expected.addText("Others", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    expected.addText("  There is no issue", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    expected.setIcon(AllIcons.Nodes.Folder)

    Assert.assertEquals(expected, node.presentation)
  }

  @Test
  fun testPresentationWithSingleIssue() {
    // single issue case
    val node = NoFileNode(listOf(TestIssue()), null)
    node.update()

    val expected = PresentationData()
    expected.addText("Others", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    expected.addText("  Has 1 issue", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    expected.setIcon(AllIcons.Nodes.Folder)

    Assert.assertEquals(expected, node.presentation)
  }

  @Test
  fun testPresentationWithMultipleIssues() {
    // multiple issues case
    val node = NoFileNode(listOf(TestIssue(), TestIssue()), null)
    node.update()

    val expected = PresentationData()
    expected.addText("Others", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    expected.addText("  Has 2 issues", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    expected.setIcon(AllIcons.Nodes.Folder)

    Assert.assertEquals(expected, node.presentation)
  }

  @Test
  fun testSameNode() {
    val node1 = NoFileNode(listOf(TestIssue(), TestIssue()), null)
    val node2 = NoFileNode(listOf(TestIssue(), TestIssue()), null)
    Assert.assertEquals(node1, node2)
  }
}
