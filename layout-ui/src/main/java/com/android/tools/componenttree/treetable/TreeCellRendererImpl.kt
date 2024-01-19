/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.componenttree.treetable

import java.awt.Component
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer

/**
 * [TreeCellRenderer] for a [TreeTableImpl].
 *
 * This renderer facilitates the delegation to the proper node type renderer.
 */
class TreeCellRendererImpl(private val treeTable: TreeTableImpl) : TreeCellRenderer {

  override fun getTreeCellRendererComponent(
    tree: JTree,
    value: Any,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ): Component {
    val renderer = treeTable.tableModel.rendererOf(value)
    return renderer.getTreeCellRendererComponent(
      tree,
      value,
      selected,
      expanded,
      leaf,
      row,
      treeTable.hasFocus(),
    )
  }
}
