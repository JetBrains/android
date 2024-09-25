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
package com.android.tools.componenttree.treetable

import com.intellij.openapi.application.invokeLater
import com.intellij.ui.treeStructure.TreeBulkExpansionListener
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.ui.treeStructure.treetable.TreeTableModelAdapter
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.tree.TreePath

/** Adapter for delegating events from a TreeModel to this TableModel. */
class TreeTableModelAdapterImpl(
  treeTableModel: TreeTableModel,
  private val tree: JTree,
  private val table: TreeTableImpl,
) : TreeTableModelAdapter(treeTableModel, tree, table) {
  private val modificationCount = AtomicInteger()
  private var lastPathCollapsed: TreePath? = null

  init {
    tree.addTreeExpansionListener(
      object : TreeBulkExpansionListener {
        override fun treeCollapsed(event: TreeExpansionEvent) {
          // When a tree is collapsed, we missed a selection event. See b/360803450.
          lastPathCollapsed = event.path
        }

        override fun treeExpanded(event: TreeExpansionEvent) {}
      }
    )
  }

  /**
   * Invokes fireActualTableDataChangeEvent after all the pending events have been processed. The
   * [modificationCount] is used to collapse multiple data changed request to a single one.
   */
  override fun delayedFireTableDataChanged() {
    val stamp = modificationCount.incrementAndGet().toLong()
    invokeLater {
      if (stamp == modificationCount.get().toLong()) {
        fireActualTableDataChangeEvent()
      }
    }
  }

  /**
   * Note: This is called from the super class when a tree node is expanded/collapsed. Delay the
   * table update to avoid paint problems during tree node expansions and closures. The problem seem
   * to be caused by this being called from the selection update of the table.
   */
  override fun fireTableDataChanged() {
    delayedFireTableDataChanged()
  }

  private fun fireActualTableDataChangeEvent() {
    table.treeTableSelectionModel.update {
      if (lastPathCollapsed != null) {
        // Avoid the row selection in super.fireTableDataChanged()
        // Since that would cause the row selection below to not notify the TreeTableSelectionModel.
        table.clearSelection()
      }
      super.fireTableDataChanged()
    }
    if (lastPathCollapsed != null) {
      // When a tree is collapsed, we missed a selection event. Apply the selection here. See
      // b/360803450.
      val row = tree.getRowForPath(lastPathCollapsed)
      table.selectionModel.addSelectionInterval(row, row)
      lastPathCollapsed = null
    }
  }
}
