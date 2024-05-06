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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.ide.IdeEventQueue
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`

class SceneViewIssueNodeVisitorTest {

  @JvmField @Rule val rule = AndroidProjectRule.inMemory().onEdt()

  @RunsInEdt
  @Test
  fun testFindNodeAtTheFirst() {
    val file1 = runInEdtAndGet { rule.fixture.addFileToProject("src/File1", "").virtualFile }
    val file2 = runInEdtAndGet { rule.fixture.addFileToProject("src/File2", "").virtualFile }
    val file3 = runInEdtAndGet { rule.fixture.addFileToProject("src/File3", "").virtualFile }

    val sceneView: SceneView = mock()
    val manager = mock<SceneManager>()
    val nlModel = mock<NlModel>()
    `when`(sceneView.sceneManager).thenReturn(manager)
    `when`(manager.model).thenReturn(nlModel)
    `when`(nlModel.virtualFile).thenReturn(file1)

    val visitor = SceneViewIssueNodeVisitor(sceneView)

    val issue1 = TestIssue(source = IssueSourceWithFile(file1))
    val issue2 = TestIssue(source = IssueSourceWithFile(file2))
    val issue3 = TestIssue(source = IssueSourceWithFile(file3))

    val provider = DesignerCommonIssueTestProvider(listOf(issue1, issue2, issue3))
    val model = DesignerCommonIssueModel()
    val panel =
      DesignerCommonIssuePanel(
        rule.testRootDisposable,
        rule.project,
        model,
        { LayoutValidationNodeFactory },
        provider,
        { "" }
      )
    IdeEventQueue.getInstance().flushQueue()
    val tree = UIUtil.findComponentOfType(panel.getComponent(), Tree::class.java)!!

    panel.setSelectedNode(visitor)
    IdeEventQueue.getInstance().flushQueue()

    assertEquals(issue1, (tree.selectionPath!!.lastPathComponent as IssueNode).issue)
  }

  @RunsInEdt
  @Test
  fun testFindNodeInTheMiddle() {
    val file1 = runInEdtAndGet { rule.fixture.addFileToProject("src/File1", "").virtualFile }
    val file2 = runInEdtAndGet { rule.fixture.addFileToProject("src/File2", "").virtualFile }
    val file3 = runInEdtAndGet { rule.fixture.addFileToProject("src/File3", "").virtualFile }

    val sceneView: SceneView = mock()
    val manager = mock<SceneManager>()
    val nlModel = mock<NlModel>()
    `when`(sceneView.sceneManager).thenReturn(manager)
    `when`(manager.model).thenReturn(nlModel)
    `when`(nlModel.virtualFile).thenReturn(file2)

    val visitor = SceneViewIssueNodeVisitor(sceneView)

    val issue1 = TestIssue(source = IssueSourceWithFile(file1))
    val issue2 = TestIssue(source = IssueSourceWithFile(file2))
    val issue3 = TestIssue(source = IssueSourceWithFile(file3))

    val provider = DesignerCommonIssueTestProvider(listOf(issue1, issue2, issue3))
    val model = DesignerCommonIssueModel()
    val panel =
      DesignerCommonIssuePanel(
        rule.testRootDisposable,
        rule.project,
        model,
        { LayoutValidationNodeFactory },
        provider,
        { "" }
      )
    IdeEventQueue.getInstance().flushQueue()
    val tree = UIUtil.findComponentOfType(panel.getComponent(), Tree::class.java)!!

    panel.setSelectedNode(visitor)
    IdeEventQueue.getInstance().flushQueue()

    assertEquals(issue2, (tree.selectionPath!!.lastPathComponent as IssueNode).issue)
  }

  @RunsInEdt
  @Test
  fun testFindNodeAtTheEnd() {
    val file1 = runInEdtAndGet { rule.fixture.addFileToProject("src/File1", "").virtualFile }
    val file2 = runInEdtAndGet { rule.fixture.addFileToProject("src/File2", "").virtualFile }
    val file3 = runInEdtAndGet { rule.fixture.addFileToProject("src/File3", "").virtualFile }

    val sceneView: SceneView = mock()
    val manager = mock<SceneManager>()
    val nlModel = mock<NlModel>()
    `when`(sceneView.sceneManager).thenReturn(manager)
    `when`(manager.model).thenReturn(nlModel)
    `when`(nlModel.virtualFile).thenReturn(file3)

    val visitor = SceneViewIssueNodeVisitor(sceneView)

    val issue1 = TestIssue(source = IssueSourceWithFile(file1))
    val issue2 = TestIssue(source = IssueSourceWithFile(file2))
    val issue3 = TestIssue(source = IssueSourceWithFile(file3))

    val provider = DesignerCommonIssueTestProvider(listOf(issue1, issue2, issue3))
    val model = DesignerCommonIssueModel()
    val panel =
      DesignerCommonIssuePanel(
        rule.testRootDisposable,
        rule.project,
        model,
        { LayoutValidationNodeFactory },
        provider,
        { "" }
      )
    IdeEventQueue.getInstance().flushQueue()
    val tree = UIUtil.findComponentOfType(panel.getComponent(), Tree::class.java)!!

    panel.setSelectedNode(visitor)
    IdeEventQueue.getInstance().flushQueue()

    assertEquals(issue3, (tree.selectionPath!!.lastPathComponent as IssueNode).issue)
  }

  @RunsInEdt
  @Test
  fun testNoNodeFound() {
    val file1 = runInEdtAndGet { rule.fixture.addFileToProject("src/File1", "").virtualFile }
    val file2 = runInEdtAndGet { rule.fixture.addFileToProject("src/File2", "").virtualFile }
    val file3 = runInEdtAndGet { rule.fixture.addFileToProject("src/File3", "").virtualFile }
    val otherFile = runInEdtAndGet { rule.fixture.addFileToProject("src/Other", "").virtualFile }

    val sceneView: SceneView = mock()
    val manager = mock<SceneManager>()
    val nlModel = mock<NlModel>()
    `when`(sceneView.sceneManager).thenReturn(manager)
    `when`(manager.model).thenReturn(nlModel)
    `when`(nlModel.virtualFile).thenReturn(otherFile)

    val visitor = SceneViewIssueNodeVisitor(sceneView)

    val issue1 = TestIssue(source = IssueSourceWithFile(file1))
    val issue2 = TestIssue(source = IssueSourceWithFile(file2))
    val issue3 = TestIssue(source = IssueSourceWithFile(file3))

    val provider = DesignerCommonIssueTestProvider(listOf(issue1, issue2, issue3))
    val model = DesignerCommonIssueModel()
    val panel =
      DesignerCommonIssuePanel(
        rule.testRootDisposable,
        rule.project,
        model,
        { LayoutValidationNodeFactory },
        provider,
        { "" }
      )
    IdeEventQueue.getInstance().flushQueue()
    val tree = UIUtil.findComponentOfType(panel.getComponent(), Tree::class.java)!!

    panel.setSelectedNode(visitor)
    IdeEventQueue.getInstance().flushQueue()

    assertNull(tree.selectionPath)
  }
}
