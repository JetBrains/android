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

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.utils.HtmlBuilder
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.assertInstanceOf
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import javax.swing.tree.TreePath
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@Suppress("UnstableApiUsage")
class DesignerCommonIssuePanelTest {

  @JvmField @Rule val rule = AndroidProjectRule.inMemory().onEdt()

  @RunsInEdt
  @Test
  fun testViewOptionFilter() {
    val infoSeverityIssue = TestIssue(severity = HighlightSeverity.INFORMATION)
    val warningSeverityIssue = TestIssue(severity = HighlightSeverity.WARNING)
    val model = DesignerCommonIssueModel()
    Disposer.register(rule.testRootDisposable, model)
    val panel =
      DesignerCommonIssuePanel(
        rule.testRootDisposable,
        rule.project,
        false,
        "name",
        SHARED_ISSUE_PANEL_TAB_ID,
        { LayoutValidationNodeFactory },
        EmptyFilter,
        { "" },
      )
    // Make sure the Tree is added into DesignerCommonIssuePanel.
    IdeEventQueue.getInstance().flushQueue()
    val tree = UIUtil.findComponentOfType(panel.getComponent(), Tree::class.java)!!
    val treeModel = tree.model
    rule.project.messageBus
      .syncPublisher(IssueProviderListener.TOPIC)
      .issueUpdated(this, listOf(infoSeverityIssue, warningSeverityIssue))

    val root = (treeModel.root!! as DesignerCommonIssueRoot)
    root.setComparator(
      DesignerCommonIssueNodeComparator(sortedBySeverity = true, sortedByName = true)
    )

    val provider = panel.issueProvider
    val noFileNode = root.getChildren().single() as NoFileNode
    run {
      panel.setViewOptionFilter { true }
      provider.update()

      assertEquals(2, noFileNode.getChildren().size)
      assertEquals(warningSeverityIssue, (noFileNode.getChildren()[0].issue))
      assertEquals(infoSeverityIssue, (noFileNode.getChildren()[1].issue))
    }

    run {
      panel.setViewOptionFilter {
        !setOf(HighlightSeverity.INFORMATION.myVal).contains(it.severity.myVal)
      }
      provider.update()

      assertEquals(1, noFileNode.getChildren().size)
      assertEquals(warningSeverityIssue, (noFileNode.getChildren()[0].issue))
    }

    run {
      panel.setViewOptionFilter {
        !setOf(HighlightSeverity.WARNING.myVal).contains(it.severity.myVal)
      }
      provider.update()

      assertEquals(1, noFileNode.getChildren().size)
      assertEquals(infoSeverityIssue, (noFileNode.getChildren()[0].issue))
    }

    run {
      panel.setViewOptionFilter {
        !setOf(HighlightSeverity.INFORMATION.myVal, HighlightSeverity.WARNING.myVal)
          .contains(it.severity.myVal)
      }
      provider.update()

      // If there is no issue, then tree has no file node.
      assertEquals(0, root.getChildren().size)
    }
  }

  @RunsInEdt
  @Test
  fun testShowSidePanelWhenSelectIssueNode() {
    val model = DesignerCommonIssueModel()
    Disposer.register(rule.testRootDisposable, model)
    val panel =
      DesignerCommonIssuePanel(
        rule.testRootDisposable,
        rule.project,
        false,
        "name",
        SHARED_ISSUE_PANEL_TAB_ID,
        { LayoutValidationNodeFactory },
        EmptyFilter,
        { "" },
      )
    // Make sure the Tree is added into DesignerCommonIssuePanel.
    IdeEventQueue.getInstance().flushQueue()
    val tree = UIUtil.findComponentOfType(panel.getComponent(), Tree::class.java)!!
    rule.project.messageBus
      .syncPublisher(IssueProviderListener.TOPIC)
      .issueUpdated(this, listOf(TestIssue(description = "some description")))

    val root = (tree.model.root!! as DesignerCommonIssueRoot)
    root.setComparator(
      DesignerCommonIssueNodeComparator(sortedBySeverity = true, sortedByName = true)
    )
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

  @RunsInEdt
  @Test
  fun testContextData() {
    rule.projectRule.replaceService(DataManager::class.java, DataManagerImpl())

    val file = rule.fixture.addFileToProject("res/layout/my_layout.xml", "")

    val fileIssue =
      TestIssue(
        source = IssueSourceWithFile(file.virtualFile, "my_layout"),
        description = "layout issue",
      )
    val noFileIssue = TestIssue(description = "other issue")

    val composeFile = rule.fixture.addFileToProject("src/Compose.kt", "Compose file")
    val nlModel = Mockito.mock(NlModel::class.java)
    Mockito.`when`(nlModel.modelDisplayName).thenReturn(MutableStateFlow<String>(""))
    Mockito.`when`(nlModel.virtualFile).thenReturn(composeFile.virtualFile)
    val navigatable = OpenFileDescriptor(rule.project, composeFile.virtualFile)
    val component = NlComponent(nlModel, 651L).apply { setNavigatable(navigatable) }
    val visualLintIssue =
      VisualLintRenderIssue.builder()
        .summary("")
        .severity(HighlightSeverity.WARNING)
        .contentDescriptionProvider { HtmlBuilder() }
        .model(nlModel)
        .components(mutableListOf(component))
        .type(VisualLintErrorType.BOUNDS)
        .build()

    val model = DesignerCommonIssueModel()
    Disposer.register(rule.testRootDisposable, model)
    val panel =
      DesignerCommonIssuePanel(
        rule.testRootDisposable,
        rule.project,
        false,
        "name",
        SHARED_ISSUE_PANEL_TAB_ID,
        { LayoutValidationNodeFactory },
        EmptyFilter,
        { "" },
      )
    // Make sure the Tree is added into DesignerCommonIssuePanel.
    IdeEventQueue.getInstance().flushQueue()
    val tree = UIUtil.findComponentOfType(panel.getComponent(), Tree::class.java)!!

    rule.project.messageBus
      .syncPublisher(IssueProviderListener.TOPIC)
      .issueUpdated(this, listOf(fileIssue, noFileIssue, visualLintIssue))
    tree.isRootVisible = false
    tree.expandRow(1)
    tree.expandRow(3)
    tree.expandRow(0)

    // Now the tree structure becomes:
    // ------
    // IssuedFileNode: res/layout/my_layout.xml
    //   |- IssueNode: fileIssue
    // NoFileNode: Layout Validation
    //   |- IssueNode: noFileIssue
    //   |- VisualLintIssueNode: visualLintIssue
    //     |- NavigatableFileNode
    // --------

    run {
      // Test IssuedFileNode: res/layout/my_layout.xml
      tree.setSelectionRow(0)
      val context = DataManager.getInstance().getDataContext(tree)

      assertInstanceOf<IssuedFileNode>(context.getData(PlatformDataKeys.SELECTED_ITEM))
      assertEquals(file.virtualFile, context.getData(PlatformDataKeys.VIRTUAL_FILE))
      assertNull(context.getData(CommonDataKeys.NAVIGATABLE))
    }

    run {
      // Test IssueNode: fileIssue
      tree.setSelectionRow(1)
      val context = DataManager.getInstance().getDataContext(tree)

      assertInstanceOf<IssueNode>(context.getData(PlatformDataKeys.SELECTED_ITEM))
      assertEquals(file.virtualFile, context.getData(PlatformDataKeys.VIRTUAL_FILE))

      val navigatable = context.getData(CommonDataKeys.NAVIGATABLE)
      assertInstanceOf<OpenFileDescriptor>(navigatable)
      val descriptor = navigatable as OpenFileDescriptor
      assertEquals(rule.project, descriptor.project)
      assertEquals(file.virtualFile, descriptor.file)
    }

    run {
      // Test NoFileNode: Layout Validation
      tree.setSelectionRow(2)
      val context = DataManager.getInstance().getDataContext(tree)

      assertInstanceOf<NoFileNode>(context.getData(PlatformDataKeys.SELECTED_ITEM))
      assertEquals(null, context.getData(PlatformDataKeys.VIRTUAL_FILE))
      assertNull(context.getData(CommonDataKeys.NAVIGATABLE))
    }

    run {
      // Test IssueNode: noFileIssue
      tree.setSelectionRow(3)
      val context = DataManager.getInstance().getDataContext(tree)

      assertInstanceOf<IssueNode>(context.getData(PlatformDataKeys.SELECTED_ITEM))
      assertEquals(null, context.getData(PlatformDataKeys.VIRTUAL_FILE))
      assertNull(context.getData(CommonDataKeys.NAVIGATABLE))
    }

    run {
      // Test VisualLintIssueNode: visualLintIssue
      tree.setSelectionRow(4)
      val context = DataManager.getInstance().getDataContext(tree)

      assertInstanceOf<VisualLintIssueNode>(context.getData(PlatformDataKeys.SELECTED_ITEM))
      assertEquals(null, context.getData(PlatformDataKeys.VIRTUAL_FILE))
    }
  }
}
