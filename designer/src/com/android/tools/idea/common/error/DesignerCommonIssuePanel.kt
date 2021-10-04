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

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The issue panel to load the issues from Layout Editor and Layout Validation Tool.
 */
class DesignerCommonIssuePanel(parentDisposable: Disposable, project: Project): Disposable {

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

  init {
    Disposer.register(parentDisposable, this)

    treeModel = DesignerCommonIssueModel(this)
    treeModel.root = DesignerCommonIssueRoot(project, EmptyIssueProvider)
    tree = Tree(AsyncTreeModel(treeModel, this))
    tree.isRootVisible = false

    EditSourceOnDoubleClickHandler.install(tree)
    EditSourceOnEnterKeyHandler.install(tree)

    // TODO: Add action toolbar

    rootPanel.add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)
  }

  fun getComponent(): JComponent = rootPanel

  fun setIssueProvider(issueProvider: DesignerCommonIssueProvider) {
    val root = treeModel.root as? DesignerCommonIssueRoot ?: return
    root.issueProvider = issueProvider
    treeModel.structureChanged(null)
  }

  fun updateTree(file: VirtualFile?, issueModel: IssueModel) {
    treeModel.structureChanged(null)
    if (file != null) {
      val filedData = IssuedFileData(file, issueModel)
      // Expand the new attached issue model.
      // TODO: Use different TreeVisitor for different node.
      TreeUtil.promiseExpand(tree, LayoutFileIssueFileFinder(filedData))
    }
  }

  private fun getSelectedNode(): DesignerCommonIssueNode? {
    return TreeUtil.getLastUserObject(DesignerCommonIssueNode::class.java, tree.selectionPath)
  }

  override fun dispose() = Unit
}
