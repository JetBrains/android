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

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

private const val TOOLBAR_ACTIONS_ID = "Android.Designer.IssuePanel.ToolbarActions"
private val KEY_DETAIL_VISIBLE = DesignerCommonIssuePanel::class.java.name + "_detail_visibility"

/**
 * The issue panel to load the issues from Layout Editor and Layout Validation Tool.
 */
class DesignerCommonIssuePanel(parentDisposable: Disposable, private val project: Project) : Disposable {

  var sidePanelVisible = PropertiesComponent.getInstance(project).getBoolean(KEY_DETAIL_VISIBLE)
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
      if (CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId)) {
        return arrayOf(node.getNavigatable())
      }
      if (PlatformDataKeys.SELECTED_ITEM.`is`(dataId)) {
        return node
      }
      if (PlatformDataKeys.VIRTUAL_FILE.`is`(dataId)) {
        return node.getVirtualFile()
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
    treeModel.root = DesignerCommonIssueRoot(project)
    tree = Tree(AsyncTreeModel(treeModel, this))
    tree.isRootVisible = false

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
      if (sidePanelVisible) {
        val sidePanel = createSidePanel(it?.newLeadSelectionPath?.lastPathComponent as? DesignerCommonIssueNode)
        splitter.secondComponent = sidePanel
        splitter.revalidate()
      }
    }
  }

  fun getComponent(): JComponent = rootPanel

  fun getIssueProvider(): DesignerCommonIssueProvider<out Any?>? {
    val root = treeModel.root as? DesignerCommonIssueRoot ?: return null
    return root.issueProvider
  }

  fun setIssueProvider(issueProvider: DesignerCommonIssueProvider<out Any?>) {
    val root = treeModel.root as? DesignerCommonIssueRoot ?: return
    if (root.issueProvider == issueProvider) {
      return
    }
    val oldProvider = root.issueProvider
    val filter = if (oldProvider != null) {
      oldProvider.onRemoved()
      oldProvider.filter
    }
    else {
      { true }
    }
    root.issueProvider = issueProvider
    issueProvider.filter = filter
    treeModel.structureChanged(null)
    if (sidePanelVisible) {
      splitter.secondComponent = createSidePanel(tree.lastSelectedPathComponent as? DesignerCommonIssueNode)
    }
  }

  fun updateTree(file: VirtualFile?, issueModel: IssueModel) {
    treeModel.structureChanged(null)
    if (file != null) {
      val filedData = IssuedFileData(file, issueModel)
      // Expand the new attached issue model.
      // TODO: Use different TreeVisitor for different node.
      val promise = TreeUtil.promiseExpand(tree, LayoutFileIssueFileFinder(filedData))
      if (sidePanelVisible) {
        promise.onSuccess {
          splitter.secondComponent = createSidePanel(it.lastPathComponent as? DesignerCommonIssueNode)
          splitter.revalidate()
        }
      }
    }
  }

  fun setHiddenSeverities(hiddenSeverities: Set<Int>) {
    val wasEmpty = treeModel.root?.getChildren()?.isEmpty() ?: true
    getIssueProvider()?.filter = { issue ->
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
    val issueNode = node as? LayoutFileIssueNode ?: return null

    val sidePanel = DesignerCommonIssueSidePanel(project, issueNode.issue, issueNode.getVirtualFile())
    val previewEditor = sidePanel.editor
    val navigable = issueNode.getNavigatable()
    if (previewEditor != null && navigable != null) {
      navigable.navigateIn(previewEditor)
    }

    return sidePanel
  }

  override fun dispose() = Unit
}
