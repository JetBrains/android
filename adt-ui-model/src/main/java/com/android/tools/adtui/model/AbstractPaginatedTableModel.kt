/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.adtui.model

import javax.swing.RowSorter
import javax.swing.table.AbstractTableModel
import kotlin.math.ceil
import kotlin.math.max

/**
 * A table model the supports pagination.
 *
 * The model extends [AbstractTableModel] to expose only [pageSize] rows from the data set are displayed in a table.
 *
 * @param pageSize page size of the model. Must be a positive number.
 * @property pageCount number of total pages based on the given [pageSize]. At least 1 even if the data set is empty.
 */
abstract class AbstractPaginatedTableModel(val pageSize: Int) : AbstractTableModel() {
  init {
    require(pageSize > 0) { "Page size must be positive, was $pageSize" }
  }

  var pageIndex = 0
    private set

  val pageCount get() = max(1, ceil(getDataSize().toDouble() / pageSize).toInt())
  val isOnFirstPage get() = pageIndex == 0
  val isOnLastPage get() = pageIndex == pageCount - 1

  /**
   * Returns the size of the entire data set.
   *
   * This is used for page calculation.
   */
  abstract fun getDataSize(): Int

  /**
   * Returns the value for the cell at [dataIndex] and [columnIndex].
   *
   * This is used by [getValueAt] for mapping a row in the current page to a data entry.
   *
   * @param dataIndex index of the data entry in the entire data set.
   */
  protected abstract fun getDataValueAt(dataIndex: Int, columnIndex: Int): Any

  /**
   * Sorts the entire data set.
   *
   * Traditional JTables only sort the rows in view. When data are paginated we want to sort the entire data set instead of just the current
   * page. This is called on sort order change to pre-sort data before pagination logic kicks in.
   *
   * @param sortKeys current sort keys of the table's [RowSorter]
   */
  abstract fun sortData(sortKeys: List<RowSorter.SortKey>)

  override fun getRowCount(): Int {
    return if (isOnLastPage) getDataSize().rem(pageSize) else pageSize
  }

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
    val dataIndex = rowIndex + pageIndex * pageSize
    return getDataValueAt(dataIndex, columnIndex)
  }

  fun goToFirstPage() {
    if (!isOnFirstPage) {
      pageIndex = 0
      fireTableDataChanged()
    }
  }

  fun goToLastPage() {
    if (!isOnLastPage) {
      pageIndex = pageCount - 1
      fireTableDataChanged()
    }
  }

  fun goToPrevPage() {
    if (!isOnFirstPage) {
      --pageIndex
      fireTableDataChanged()
    }
  }

  fun goToNextPage() {
    if (!isOnLastPage) {
      ++pageIndex
      fireTableDataChanged()
    }
  }
}