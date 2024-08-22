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

import com.android.annotations.concurrency.UiThread
import com.android.tools.componenttree.api.ComponentTreeSelectionModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.TreePath

/**
 * A [DefaultTreeSelectionModel] where a selection is treated as a list of nodes rather than tree
 * paths.
 */
class TreeTableSelectionModelImpl(private val table: TreeTableImpl) : ComponentTreeSelectionModel {
  private val selectionListeners: MutableList<(List<Any>) -> Unit> =
    ContainerUtil.createConcurrentList()
  private val autoScrollListeners: MutableList<() -> Unit> = ContainerUtil.createConcurrentList()
  private var isUpdating = false
  private var delayedSelectionUpdate = false

  init {
    table.selectionModel.addListSelectionListener {
      if (!it.valueIsAdjusting) {
        if (!isUpdating) {
          fireSelectionChange()
        } else {
          delayedSelectionUpdate = true
        }
      }
    }
  }

  override var currentSelection: List<Any>
    get() = table.selectionModel.selectedIndices.map { table.getValueAt(it, 0) }
    set(value) {
      val oldValue = currentSelection
      if (value != oldValue) {
        update(performDelayedSelectionUpdates = false) {
          // First expand the selected nodes in the tree
          val paths = value.map { createTreePath(it) }
          val parentPaths = paths.mapNotNull { it.parentPath }
          TreeUtil.restoreExpandedPaths(table.tree, parentPaths)

          // Then set the selection in the table
          table.selectionModel.clearSelection()
          paths
            .map { table.tree.getRowForPath(it) }
            .forEach { table.selectionModel.addSelectionInterval(it, it) }
          fireAutoScroll()
        }
      }
    }

  fun keepSelectionDuring(operation: () -> Unit) {
    val oldSelection =
      table.selectionModel.selectedIndices.map { table.getValueAt(it, 0) }.filterNotNull()
    update(operation = operation)

    // Tricky:
    // When the operation is initiated from a data update on the TreeTableImpl, there are several
    // operations that are executed with invokeLater (i.e. sent to the UI thread for execution).
    // We want to restore the selection after all these tasks have completed. By using invokeLater
    // the restore will be added to the UI queue after the subtasks thus giving us the wanted
    // result.
    invokeLater { currentSelection = oldSelection }
  }

  /**
   * Perform an update [operation] without firing a storm of selection events.
   *
   * Do this by not firing selection events during the [operation]. If any selection events were
   * blocked during the [operation], fire a single event after the [operation] has finished unless
   * [performDelayedSelectionUpdates] is false.
   *
   * Selection changes can be created by:
   * 1. The user selecting a new cell in the TreeTable
   * 2. An outside event initiated with a [currentSelection] change
   * 3. The TreeTable may create several selection changes as a result of the implementation in
   *    TreeTable.
   */
  @UiThread
  fun update(performDelayedSelectionUpdates: Boolean = true, operation: () -> Unit) {
    // Protect the "isUpdating" var against multi threading:
    ApplicationManager.getApplication().assertIsDispatchThread()

    val wasUpdating = isUpdating
    isUpdating = true
    try {
      operation()
    } finally {
      // Guard for recursive update calls. Here: isUpdating should remain false until all
      // invocations of update are done.
      isUpdating = wasUpdating
      if (!wasUpdating && delayedSelectionUpdate) {
        if (performDelayedSelectionUpdates) {
          fireSelectionChange()
        }
        delayedSelectionUpdate = false
      }
    }
  }

  override fun addSelectionListener(listener: (List<Any>) -> Unit) {
    selectionListeners.add(listener)
  }

  override fun removeSelectionListener(listener: (List<Any>) -> Unit) {
    selectionListeners.remove(listener)
  }

  private fun fireSelectionChange() {
    val newSelection = currentSelection
    selectionListeners.forEach { it.invoke(newSelection) }
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
