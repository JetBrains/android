/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.adtui.categorytable

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Implementation of selection of a single row for [CategoryTable].
 *
 * TODO: consider supporting multiple selection.
 */
class CategoryTableSingleSelection<T : Any>(val table: CategoryTable<T>) {
  private var selectedKey = MutableStateFlow<RowKey<T>?>(null)

  fun asFlow(): Flow<Set<RowKey<T>>> = selectedKey.map { setOfNotNull(it) }

  fun clear() {
    selectedKey.value = null
  }

  fun selectRow(key: RowKey<T>) {
    selectedKey.value = key
  }

  fun selectNextRow() {
    selectedKey.update { key ->
      val index = key?.let { table.indexOf(it) } ?: -1
      if (index < table.rowComponents.size - 1) {
        selectFirstVisibleRowIn(index + 1 until table.rowComponents.size) ?: key
      } else key
    }
  }

  fun selectPreviousRow() {
    selectedKey.update { key ->
      val index = key?.let { table.indexOf(it) } ?: 0
      if (index > 0) {
        selectFirstVisibleRowIn(index - 1 downTo 0) ?: key
      } else key
    }
  }

  private fun selectFirstVisibleRowIn(indices: IntProgression): RowKey<T>? {
    for (i in indices) {
      val categoryTableRow = table.rowComponents[i]
      if (categoryTableRow.isVisible) {
        categoryTableRow.requestFocusInWindow()
        return categoryTableRow.rowKey
      }
    }
    return null
  }

  private fun ifCategoryRowSelected(block: (CategoryRowComponent<T>) -> Unit) {
    val key = selectedKey.value ?: return
    val categoryRow =
      table.rowComponents.first { it.rowKey == key } as? CategoryRowComponent<T> ?: return
    block(categoryRow)
  }

  fun toggleSelectedRowCollapsed() {
    ifCategoryRowSelected { table.toggleCollapsed(it.path) }
  }

  fun expandSelectedRow() {
    ifCategoryRowSelected { table.setCollapsed(it.path, false) }
  }

  fun collapseSelectedRow() {
    ifCategoryRowSelected { table.setCollapsed(it.path, true) }
  }
}
