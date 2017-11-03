/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.visualtests

import com.android.tools.adtui.common.ColumnTreeBuilder
import com.android.tools.adtui.model.updater.Updatable
import com.android.tools.adtui.visualtests.VisualTest
import com.android.tools.profilers.ProfilerColors
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableColumnModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

class HoverColumnTreeVisualTest : VisualTest() {
  override fun getName(): String {
    return "HoverColumnTree"
  }

  override fun createModelList(): List<Updatable> {
    return listOf()
  }

  override fun populateUi(panel: JPanel) {
    panel.layout = BorderLayout()
    val tree = createTree()
    val builder = ColumnTreeBuilder(tree, DefaultTableColumnModel())
    builder.setHoverColor(ProfilerColors.DEFAULT_HOVER_COLOR)
    builder.addColumn(ColumnTreeBuilder.ColumnBuilder().setName("Class")
        .setRenderer(object : ColoredTreeCellRenderer() {
          override fun customizeCellRenderer(tree: JTree,
                                             value: Any,
                                             selected: Boolean,
                                             expanded: Boolean,
                                             leaf: Boolean,
                                             row: Int,
                                             hasFocus: Boolean) {
            if (value is Node) {
              val name = value.myName
              append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name)
            }
          }
        }))
    builder.addColumn(ColumnTreeBuilder.ColumnBuilder().setName("Size")
        .setRenderer(object : ColoredTreeCellRenderer() {
          override fun customizeCellRenderer(tree: JTree,
                                             value: Any,
                                             selected: Boolean,
                                             expanded: Boolean,
                                             leaf: Boolean,
                                             row: Int,
                                             hasFocus: Boolean) {
            if (value is Node) {
              val size = value.mySize
              append(size, SimpleTextAttributes.REGULAR_ATTRIBUTES, size)
            }
            setTextAlign(SwingConstants.RIGHT)
          }
        }))
    panel.add(builder.build(), BorderLayout.CENTER)
  }

  private fun createTree(): JTree {
    val root = Node("root", "0")
    root.add(Node("com.android.Fetcher", "100"))
    root.add(Node("com.example.Fetcher", "100"))
    val parent = Node("parent", "100")
    parent.add(Node("child", "200"))
    root.add(parent)
    val tree = Tree(root)
    tree.isRootVisible = false
    tree.showsRootHandles = true
    tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    return tree
  }

  private class Node internal constructor(internal var myName: String, internal var mySize: String) : DefaultMutableTreeNode(myName)
}
