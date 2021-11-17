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
package com.android.tools.componenttree.treetable

import com.android.tools.componenttree.api.ComponentTreeSelectionModel
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.TreePath

/**
 * A [DefaultTreeSelectionModel] where a selection is treated as a list of nodes rather than tree paths.
 */
class TreeTableSelectionModelImpl(private val table: TreeTableImpl) : ComponentTreeSelectionModel {
  private val selectionListeners: MutableList<(List<Any>) -> Unit> = ContainerUtil.createConcurrentList()
  private val autoScrollListeners: MutableList<() -> Unit> = ContainerUtil.createConcurrentList()
  private var isUpdating = false

  init {
    table.selectionModel.addListSelectionListener {
      if (!isUpdating) {
        val newSelection = currentSelection
        selectionListeners.forEach { it.invoke(newSelection) }
      }
    }
  }

  override var currentSelection: List<Any>
    get() = table.selectionModel.selectedIndices.map { table.getValueAt(it, 0) }
    set(value) {
      val oldValue = currentSelection
      if (value != oldValue) {
        isUpdating = true
        try {
          // First expand the selected nodes in the tree
          val paths = value.map { createTreePath(it) }
          val parentPaths = paths.mapNotNull { it.parentPath }
          TreeUtil.restoreExpandedPaths(table.tree, parentPaths)

          // Then set the selection in the table
          table.selectionModel.clearSelection()
          paths.map { table.tree.getRowForPath(it) }.forEach { table.selectionModel.addSelectionInterval(it, it) }
          fireAutoScroll()
        }
        finally {
          isUpdating = false
        }
      }
    }

  fun keepSelectionDuring(operation: () -> Unit) {
    val oldSelection = table.selectionModel.selectedIndices.map { table.getValueAt(it, 0) }

    isUpdating = true
    try {
      operation()
    }
    finally {
      isUpdating = false
    }
    currentSelection = oldSelection
  }

  override fun addSelectionListener(listener: (List<Any>) -> Unit) {
    selectionListeners.add(listener)
  }

  override fun removeSelectionListener(listener: (List<Any>) -> Unit) {
    selectionListeners.remove(listener)
  }

  fun addAutoScrollListener(listener: () -> Unit) {
    autoScrollListeners.add(listener)
  }

  fun removeAutoScrollListener(listener: () -> Unit) {
    autoScrollListeners.remove(listener)
  }

  private fun fireAutoScroll() {
    autoScrollListeners.forEach { it.invoke() }
  }

  private fun createTreePath(node: Any): TreePath {
    val path = generateSequence(node) { table.tableModel.parent(it) }
    return TreePath(path.toList().asReversed().toTypedArray())
  }
}
