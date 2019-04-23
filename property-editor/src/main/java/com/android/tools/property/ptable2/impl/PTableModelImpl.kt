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
import javax.swing.table.AbstractTableModel

/**
 * A table model implementation for a JTable.
 */
class PTableModelImpl(val tableModel: PTableModel) : AbstractTableModel() {
  private val items = mutableListOf<PTableItem>()
  private val parentItems = mutableMapOf<PTableItem, PTableGroupItem>()
  private var hasEditableCells = ThreeState.UNSURE

  @VisibleForTesting
  val expandedItems = mutableSetOf<PTableGroupItem>()

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
          items.clear()
          items.addAll(tableModel.items)
          recomputeParents()
          expandedItems.retainAll { isGroupItem(it) }
          val previousExpandedItems = HashSet(expandedItems)
          expandedItems.clear()
          restoreExpanded(previousExpandedItems)
          val index = if (nextEditedItem != null) items.indexOf(nextEditedItem) else -1
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

  fun isGroupItem(item: PTableItem): Boolean {
    // This allows an implementation to mutate an item from/to an item that is considered a group.
    // It is currently used in fabricated new items where the state is unknown up front.
    return item is PTableGroupItem && item.children.isNotEmpty()
  }

  fun isExpanded(item: PTableGroupItem): Boolean {
    return expandedItems.contains(item)
  }

  fun toggle(index: Int) {
    val item = groupAt(index) ?: return
    if (expandedItems.contains(item)) {
      collapse(item, index)
    }
    else {
      expand(item, index)
    }
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

  private fun restoreExpanded(previousExpandedItems: Set<PTableGroupItem>) {
    previousExpandedItems.forEach { restoreExpandedInnerGroup(it) }
    previousExpandedItems.forEach { restoreExpandedOuterGroup(it) }
  }

  private fun restoreExpandedInnerGroup(oldItem: PTableGroupItem) {
    val newParent = parentItems[oldItem] ?: return
    val index = newParent.children.indexOf(oldItem)
    // Note that the item added may be a different instance than oldItem:
    expandedItems.add(newParent.children[index] as PTableGroupItem)
  }

  private fun restoreExpandedOuterGroup(oldItem: PTableGroupItem) {
    if (!expandedItems.contains(oldItem)) {
      val index = items.indexOf(oldItem)
      if (index >= 0) {
        // Note that the item expanded may be a different instance than oldItem:
        val newItem = items[index] as PTableGroupItem
        expand(newItem, index)
      }
    }
  }

  private fun expand(item: PTableGroupItem, index: Int) {
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
