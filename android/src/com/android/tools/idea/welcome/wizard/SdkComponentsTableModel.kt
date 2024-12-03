/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard

import com.android.tools.idea.welcome.install.SdkComponentTreeNode
import com.google.common.collect.ImmutableList
import com.intellij.openapi.util.Pair
import javax.swing.table.AbstractTableModel

/**
 * The [AbstractTableModel] corresponding to a list of SDK components [SdkComponentTreeNode]. The
 * tree is traversed using depth-first search and each component is added to the table as a new row.
 * Only optional components, where `isEnabled=true` are editable.
 *
 * @param rootNode The children of this node are used to construct the table (the root is discarded)
 */
internal class SdkComponentsTableModel(rootNode: SdkComponentTreeNode) : AbstractTableModel() {
  private val components: List<Pair<SdkComponentTreeNode, Int>>

  init {
    val components = ImmutableList.builder<Pair<SdkComponentTreeNode, Int>>()
    // Note that root component is not present in the table model so the tree appears to have
    // multiple roots
    traverse(rootNode.immediateChildren, 0, components)
    this.components = components.build()
  }

  private fun traverse(
    children: Collection<SdkComponentTreeNode>,
    indent: Int,
    components: ImmutableList.Builder<Pair<SdkComponentTreeNode, Int>>,
  ) {
    for (child in children) {
      components.add(Pair.create(child, indent))
      traverse(child.immediateChildren, indent + 1, components)
    }
  }

  override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean =
    columnIndex == 0 && getInstallableComponent(rowIndex).isEnabled

  override fun getRowCount(): Int = components.size

  override fun getColumnCount(): Int = 1

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = components[rowIndex]

  private fun getInstallableComponent(rowIndex: Int): SdkComponentTreeNode =
    components[rowIndex].getFirst()

  override fun setValueAt(aValue: Any?, row: Int, column: Int) {
    val node = getInstallableComponent(row)
    node.toggle(aValue as Boolean)
    // We need to repaint as a change in a single row may affect the state of
    // our parent and/or children in other rows.
    // Note: Don't use fireTableDataChanged to avoid clearing the selection.
    fireTableRowsUpdated(0, rowCount)
  }

  fun getComponentDescription(index: Int): String = getInstallableComponent(index).description
}
