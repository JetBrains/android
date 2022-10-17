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
import com.intellij.ide.projectView.PresentationData
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.tree.LeafState
import junit.framework.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NodeProviderTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testCreateFileNode() {
    val root = TestRootNode()
    val nodeProvider = NodeProviderImpl(root)
    root.setNodeProvider(nodeProvider)

    val file = runInEdtAndGet { projectRule.fixture.addFileToProject("path/to/file", "").virtualFile }
    val issue1 = TestIssue(summary = "issue1", source = (IssueSourceWithFile(file)))
    val issue2 = TestIssue(summary = "issue2", source = (IssueSourceWithFile(file)))

    val issue3 = TestIssue("issue3")
    val issue4 = TestIssue("issue4")
    val issue5 = TestIssue("issue5")

    nodeProvider.updateIssues(listOf(issue1, issue2, issue3, issue4, issue5))

    val fileNode = nodeProvider.getFileNodes()
    assertEquals(2, fileNode.size)

    fileNode[0].let {
      assertInstanceOf<IssuedFileNode>(it)
      assertEquals(root, it.parentDescriptor)

      assertEquals(2, it.getChildren().size)
      assertEquals(issue1, (it.getChildren()[0] as IssueNode).issue)
      assertEquals(issue2, (it.getChildren()[1] as IssueNode).issue)
    }
    fileNode[1].let {
      assertInstanceOf<NoFileNode>(fileNode[1])
      assertEquals(root, it.parentDescriptor)

      assertEquals(3, it.getChildren().size)
      assertEquals(issue3, (it.getChildren()[0] as IssueNode).issue)
      assertEquals(issue4, (it.getChildren()[1] as IssueNode).issue)
      assertEquals(issue5, (it.getChildren()[2] as IssueNode).issue)
    }
  }
}

private class TestRootNode : DesignerCommonIssueNode(null, null) {

  private lateinit var _nodeProvider: NodeProvider

  override fun updatePresentation(presentation: PresentationData) = Unit

  override fun getName(): String = "Root"

  override fun getChildren(): List<DesignerCommonIssueNode> = getNodeProvider().getFileNodes()

  override fun getLeafState(): LeafState = LeafState.NEVER

  override fun getNodeProvider(): NodeProvider = _nodeProvider

  fun setNodeProvider(provider: NodeProvider) {
    _nodeProvider = provider
  }
}
