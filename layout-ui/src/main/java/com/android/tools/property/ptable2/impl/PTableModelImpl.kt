/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.property.ptable2.impl

import com.google.common.annotations.VisibleForTesting
import com.android.tools.property.ptable2.PTableColumn
import com.android.tools.property.ptable2.PTableGroupItem
import com.android.tools.property.ptable2.PTableItem
import com.android.tools.property.ptable2.PTableModel
import com.android.tools.property.ptable2.PTableModelUpdateListener
import com.intellij.util.ThreeState
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.table.AbstractTableModel

/**
 * A table model implementation for a JTable.
 */
class PTableModelImpl(val tableModel: PTableModel) : AbstractTableModel() {
  private val items = mutableListOf<PTableItem>()
  private val parentItems = IdentityHashMap<PTableItem, PTableGroupItem>()
  private var hasEditableCells = ThreeState.UNSURE

  @VisibleForTesting
  val expandedItems: MutableSet<PTableGroupItem> = Collections.newSetFromMap(IdentityHashMap())

  init {
    items.addAll(tableModel.items)
    recomputeParents()

    tableModel.addListener(object : PTableModelUpdateListener {
      override fun itemsUpdated(modelChanged: Boolean, nextEditedItem: PTableItem?) {
        if (!modelChanged) {
          fireTableChanged(PTableModelRepaintEvent(this@PTableModelImpl))
        }
        else {
          hasEditableCells = ThreeState.UNSURE
          val previousExpandedPaths = computeExpandedPaths()
          items.clear()
          items.addAll(tableModel.items)
          recomputeParents()
          restoreExpanded(previousExpandedPaths)
          val index = if (nextEditedItem != null) indexOf(nextEditedItem) else -1
          fireTableChanged(PTableModelEvent(this@PTableModelImpl, index))
        }
      }
    })
  }

  override fun getRowCount() = items.count()

  override fun getColumnCount() = 2

  override fun getValueAt(rowIndex: Int, columnIndex: Int): PTableItem = items[rowIndex]

  override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
    return tableModel.isCellEditable(items[rowIndex], PTableColumn.fromColumn(columnIndex))
  }

  fun indexOf(item: PTableItem?): Int {
    return if (item != null) items.indexOfFirst { item === it } else -1
  }

  fun parentOf(item: PTableItem): PTableGroupItem? {
    return parentItems[item]
  }

  fun isGroupItem(item: PTableItem): Boolean {
    return item is PTableGroupItem
  }

  fun isExpanded(item: PTableGroupItem): Boolean {
    return expandedItems.contains(item)
  }

  fun toggle(item: PTableGroupItem): Int {
    val index = indexOf(item)
    if (index < 0) {
      return index
    }
    toggle(item, index)
    return index
  }

  fun toggle(index: Int) {
    val item = groupAt(index) ?: return
    toggle(item, index)
  }

  fun expand(index: Int) {
    val item = groupAt(index) ?: return
    expand(item, index)
  }

  fun collapse(index: Int) {
    val item = groupAt(index) ?: return
    collapse(item, index)
  }

  fun depth(item: PTableItem): Int {
    var parent = parentItems[item]
    var depth = 0
    while (parent != null) {
      depth++
      parent = parentItems[parent]
    }
    return depth
  }

  val isEditable: Boolean
    get() {
      var editable = hasEditableCells
      if (editable == ThreeState.UNSURE) {
        editable = ThreeState.fromBoolean(items.any {
          tableModel.isCellEditable(it, PTableColumn.VALUE) || tableModel.isCellEditable(it, PTableColumn.NAME) })
        hasEditableCells = editable
      }
      return editable.toBoolean()
    }

  private fun groupAt(index: Int): PTableGroupItem? {
    if (index < 0 || index >= items.size) {
      return null
    }
    val item = items[index]
    return if (isGroupItem(item)) item as PTableGroupItem else null
  }

  private fun computeExpandedPaths(): List<List<PTableGroupItem>> {
    expandedItems.retainAll { isGroupItem(it) }
    return expandedItems.map { node -> generateSequence(node) { parentOf(it) }.toList().reversed() }
  }

  private fun restoreExpanded(previousExpandedPaths: List<List<PTableGroupItem>>) {
    expandedItems.clear()
    for (path in previousExpandedPaths) {
      for (node in path) {
        val index = items.indexOf(node)
        expand(index)
      }
    }
  }

  private fun toggle(item: PTableGroupItem, index: Int) {
    if (expandedItems.contains(item)) {
      collapse(item, index)
    }
    else {
      expand(item, index)
    }
  }

  private fun expand(item: PTableGroupItem, index: Int) =
    item.expandWhenPossible { restructured ->
      if (item === groupAt(index)) {
        if (restructured) {
          computeParents(item)
        }
        doExpand(item, index)
      }
    }

  private fun doExpand(item: PTableGroupItem, index: Int) {
    if (expandedItems.add(item)) {
      val list = mutableListOf<PTableItem>()
      computeExpanded(item, expandedItems, list)
      items.addAll(index + 1, list)
      fireTableDataChanged()
    }
  }

  private fun computeExpanded(item: PTableGroupItem, expanded: Set<PTableGroupItem>, list: MutableList<PTableItem>) {
    item.children.forEach {
      list.add(it)
      if (it is PTableGroupItem && expanded.contains(it)) {
        computeExpanded(it, expanded, list)
      }
    }
  }

  private fun collapse(item: PTableGroupItem, row: Int) {
    val rowsToRemove = expandedRowCount(item)
    if (expandedItems.remove(item)) {
      items.subList(row + 1, row + 1 + rowsToRemove).clear()
      fireTableDataChanged()
    }
  }

  private fun expandedRowCount(group: PTableGroupItem): Int {
    return group.children.sumBy { if (it is PTableGroupItem && expandedItems.contains(it)) 1 + expandedRowCount(it) else 1 }
  }

  private fun recomputeParents() {
    parentItems.clear()
    items.forEach {
      if (it is PTableGroupItem) {
        computeParents(it)
      }
    }
  }

  private fun computeParents(group: PTableGroupItem) {
    group.children.forEach {
      parentItems[it] = group
      if (it is PTableGroupItem) {
        computeParents(it)
      }
    }
  }
}
