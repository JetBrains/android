/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.error.IssuePanelService.Companion.SELECTED_ISSUES
import com.android.tools.idea.uibuilder.visual.VisualizationToolWindowFactory
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jdesktop.swingx.calendar.DateSelectionModel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

private const val TOOLBAR_ACTIONS_ID = "Android.Designer.IssuePanel.ToolbarActions"
/**
 * The id of pop action group which is shown when right-clicking a tree node.
 */
private const val POPUP_HANDLER_ACTION_ID = "Android.Designer.IssuePanel.TreePopup"
private val KEY_DETAIL_VISIBLE = DesignerCommonIssuePanel::class.java.name + "_detail_visibility"

/**
 * The issue panel to load the issues from Layout Editor and Layout Validation Tool.
 */
class DesignerCommonIssuePanel(parentDisposable: Disposable, private val project: Project,
                               val issueProvider: DesignerCommonIssueProvider<Any?>) : Disposable {

  var sidePanelVisible = PropertiesComponent.getInstance(project).getBoolean(KEY_DETAIL_VISIBLE, true)
    set(value) {
      field = value
      setSidePanelVisibility(value)
      PropertiesComponent.getInstance(project).setValue(KEY_DETAIL_VISIBLE, value)
    }

  private val rootPanel = object : JPanel(BorderLayout()), DataProvider {
    override fun getData(dataId: String): Any? {
      val node = getSelectedNode() ?: return null
      if (CommonDataKeys.NAVIGATABLE.`is`(dataId)) {
        return node.getNavigatable()
      }
      if (PlatformDataKeys.SELECTED_ITEM.`is`(dataId)) {
        return node
      }
      if (PlatformDataKeys.VIRTUAL_FILE.`is`(dataId)) {
        return node.getVirtualFile()
      }
      if (SELECTED_ISSUES.`is`(dataId)) {
        return when (node) {
          is IssuedFileNode -> node.issues
          is NoFileNode -> node.issues
          is IssueNode -> listOf(node.issue)
          else -> emptyList()
        }
      }
      return null
    }
  }

  private val tree: Tree
  private val treeModel: DesignerCommonIssueModel

  private val splitter: OnePixelSplitter

  init {
    Disposer.register(parentDisposable, this)

    treeModel = DesignerCommonIssueModel(this)
    treeModel.root = DesignerCommonIssueRoot(project, issueProvider)
    issueProvider.registerUpdateListener {
      updateTree()
    }
    tree = Tree(AsyncTreeModel(treeModel, this))
    tree.emptyText.text = "No design issue is found"
    PopupHandler.installPopupMenu(tree, POPUP_HANDLER_ACTION_ID, "Android.Designer.IssuePanel.TreePopup")

    tree.isRootVisible = false
    tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

    EditSourceOnDoubleClickHandler.install(tree)
    EditSourceOnEnterKeyHandler.install(tree)

    val toolbarActionGroup = ActionManager.getInstance().getAction(TOOLBAR_ACTIONS_ID) as ActionGroup
    val toolbar = ActionManager.getInstance().createActionToolbar(javaClass.name, toolbarActionGroup, false)
    toolbar.targetComponent = rootPanel
    UIUtil.addBorder(toolbar.component, CustomLineBorder(JBUI.insetsRight(1)))
    rootPanel.add(toolbar.component, BorderLayout.WEST)

    splitter = OnePixelSplitter(false, 0.5f, 0.3f, 0.7f)
    splitter.proportion = 0.5f
    splitter.firstComponent = ScrollPaneFactory.createScrollPane(tree, true)
    splitter.secondComponent = null
    splitter.setResizeEnabled(true)
    rootPanel.add(splitter, BorderLayout.CENTER)

    tree.addTreeSelectionListener {
      val selectedNode = it?.newLeadSelectionPath?.lastPathComponent as? DesignerCommonIssueNode ?: return@addTreeSelectionListener
      if (sidePanelVisible) {
        val sidePanel = createSidePanel(selectedNode)
        splitter.secondComponent = sidePanel
        splitter.revalidate()
      }
      // TODO(b/222110455): Can we have better way to trigger the refreshing of Layout Validation or other design tools?
      //                    Refactor to remove the dependency of VisualizationToolWindowFactory.
      val window = ToolWindowManager.getInstance(project).getToolWindow(VisualizationToolWindowFactory.TOOL_WINDOW_ID)
      if (window != null) {
        DataManager.getInstance().getDataContext(window.component).getData(DESIGN_SURFACE)?.let { surface ->
          (selectedNode as? IssueNode)?.issue?.let { issue ->
            if (issue.source is VisualLintIssueProvider.VisualLintIssueSource) {
              surface.issueListener.onIssueSelected(issue)
            }
          }
          surface.revalidateScrollArea()
          surface.repaint()
        }
      }
    }
  }

  fun getComponent(): JComponent = rootPanel

  private fun updateTree() {
    treeModel.structureChanged(null)
    val promise = TreeUtil.promiseExpand(tree, IssueNodeFileFinder())
    if (sidePanelVisible) {
      promise.onSuccess {
        splitter.secondComponent = createSidePanel(it.lastPathComponent as? DesignerCommonIssueNode)
        splitter.revalidate()
      }
    }
  }

  fun setHiddenSeverities(hiddenSeverities: Set<Int>) {
    val wasEmpty = treeModel.root?.getChildren()?.isEmpty() ?: true
    issueProvider.filter = { issue ->
      !hiddenSeverities.contains(issue.severity.myVal)
    }
    treeModel.structureChanged(null)
    if (wasEmpty) {
      TreeUtil.promiseExpandAll(tree)
    }
  }

  private fun getSelectedNode(): DesignerCommonIssueNode? {
    return TreeUtil.getLastUserObject(DesignerCommonIssueNode::class.java, tree.selectionPath)
  }

  private fun setSidePanelVisibility(visible: Boolean) {
    if (!visible) {
      splitter.secondComponent = null
    }
    else {
      splitter.secondComponent = createSidePanel(tree.lastSelectedPathComponent as? DesignerCommonIssueNode)
    }
    splitter.revalidate()
  }

  private fun createSidePanel(node: DesignerCommonIssueNode?): JComponent? {
    val issueNode = node as? IssueNode ?: return null

    val sidePanel = DesignerCommonIssueSidePanel(project, issueNode.issue, issueNode.getVirtualFile(), this)
    val previewEditor = sidePanel.editor
    val navigable = issueNode.getNavigatable()
    if (previewEditor != null && navigable != null) {
      navigable.navigateIn(previewEditor)
    }

    return sidePanel
  }

  override fun dispose() = Unit
}

/**
 * Used to find the target [IssuedFileNode] in the [com.intellij.ui.treeStructure.Tree].
 */
class IssueNodeFileFinder : TreeVisitor {
  override fun visit(path: TreePath) = when (TreeUtil.getLastUserObject(path)) {
    is DesignerCommonIssueRoot -> TreeVisitor.Action.CONTINUE
    is IssuedFileNode, is NoFileNode -> TreeVisitor.Action.CONTINUE
    is IssueNode -> TreeVisitor.Action.SKIP_CHILDREN
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }
}
