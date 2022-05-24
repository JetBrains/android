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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.AbstractPaginatedTableModel
import javax.swing.RowSorter
import javax.swing.SortOrder

/**
 * Create a table model backed by this mutable list. Sorting the table will modify this list.
 */
inline fun<reified C: Enum<C>, R> MutableList<R>.asTableModel(crossinline getColumn: (C) -> (R) -> Comparable<*>,
                                                              crossinline getClass: (C) -> Class<*>,
                                                              crossinline getName: (C) -> String,
                                                              name: String = "table",
                                                              pageSize: Int = 25) =
  object : PaginatedTableModel<R>(pageSize) {
    override val rows get() = this@asTableModel
    override fun getDataSize() = rows.size
    override fun getDataValueAt(dataIndex: Int, columnIndex: Int) = getColumn(enumValues<C>()[columnIndex])(rows[dataIndex])
    override fun getColumnCount() = enumValues<C>().size
    override fun getColumnClass(columnIndex: Int) = getClass(enumValues<C>()[columnIndex])
    override fun getColumnName(column: Int) = getName(enumValues<C>()[column])
    override fun toString() = name
    override fun sortData(sortKeys: List<RowSorter.SortKey>) {
      if (sortKeys.isNotEmpty()) {
        rows.sortWith(sortKeys
                        .map { key ->
                          val cmp = compareBy(getColumn(enumValues<C>()[key.column]))
                          if (key.sortOrder == SortOrder.ASCENDING) cmp else cmp.reversed()
                        }
                        .reduce(Comparator<R>::then))
      }
    }
  }

abstract class PaginatedTableModel<R>(initialPageSize: Int): AbstractPaginatedTableModel(initialPageSize) {
  abstract val rows: List<R>
}