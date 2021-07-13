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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.view

import com.android.tools.adtui.common.ColumnTreeBuilder
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskTreeModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntrySelectionModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.BackgroundTaskEntry
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.tree.TreeModelAdapter
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.event.TreeModelEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * A [JBScrollPane] that consists of a tree table with basic information of all background tasks.
 */
class BackgroundTaskTreeTableView(client: BackgroundTaskInspectorClient,
                                  selectionModel: EntrySelectionModel) {
  val component: JComponent

  init {
    val treeModel = BackgroundTaskTreeModel(client)
    val tree = JTree(treeModel)
    tree.isRootVisible = false
    tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

    treeModel.addTreeModelListener(object : TreeModelAdapter() {
      override fun treeNodesChanged(event: TreeModelEvent) {
        super.treeNodesChanged(event)
        tree.expandPath(event.treePath)
      }
    })

    tree.addTreeSelectionListener { event ->
      if (event.isAddedPath) {
        val node = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
        val entry = node.userObject as? BackgroundTaskEntry ?: return@addTreeSelectionListener
        selectionModel.selectedEntry = entry
      }
      else {
        selectionModel.selectedEntry = null
      }
    }

    selectionModel.registerWorkSelectionListener { entry ->
      if (entry == null) {
        with(tree.selectionPath) {
          tree.removeSelectionPath(this)
        }
      }
      else {
        val node = treeModel.getTreeNode(entry.id) ?: return@registerWorkSelectionListener
        tree.selectionModel.selectionPath = TreePath(node.path)
      }
    }

    val builder = ColumnTreeBuilder(tree)

    builder.addColumn(ColumnTreeBuilder.ColumnBuilder().setName("Class").setRenderer(object : ColoredTreeCellRenderer() {
      override fun customizeCellRenderer(tree: JTree,
                                         value: Any?,
                                         selected: Boolean,
                                         expanded: Boolean,
                                         leaf: Boolean,
                                         row: Int,
                                         hasFocus: Boolean) {
        when (val data = (value as DefaultMutableTreeNode).userObject) {
          is String -> {
            append(data)
          }
          is BackgroundTaskEntry -> {
            append(data.className)
          }
        }
      }
    }))
    builder.addColumn(ColumnTreeBuilder.ColumnBuilder().setName("Status").setRenderer(object : ColoredTreeCellRenderer() {
      override fun customizeCellRenderer(tree: JTree,
                                         value: Any?,
                                         selected: Boolean,
                                         expanded: Boolean,
                                         leaf: Boolean,
                                         row: Int,
                                         hasFocus: Boolean) {
        when (val data = (value as DefaultMutableTreeNode).userObject) {
          is BackgroundTaskEntry -> {
            append(data.status)
          }
        }
      }
    }))
    builder.addColumn(ColumnTreeBuilder.ColumnBuilder().setName("Start").setRenderer(object : ColoredTreeCellRenderer() {
      override fun customizeCellRenderer(tree: JTree,
                                         value: Any?,
                                         selected: Boolean,
                                         expanded: Boolean,
                                         leaf: Boolean,
                                         row: Int,
                                         hasFocus: Boolean) {
        when (val data = (value as DefaultMutableTreeNode).userObject) {
          is BackgroundTaskEntry -> {
            append(data.startTimeMs.toFormattedTimeString())
          }
        }
      }

    }))

    component = builder.build()
  }
}
