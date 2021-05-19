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

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import javax.swing.RowSorter
import javax.swing.SortOrder

class AbstractPaginatedTableModelTest {
  @get:Rule
  val thrown: ExpectedException = ExpectedException.none()

  @Test
  fun pageCount() {
    assertThat(PaginatedListModel(1, mutableListOf(1, 2, 3)).pageCount).isEqualTo(3)
    assertThat(PaginatedListModel(2, mutableListOf(1, 2, 3)).pageCount).isEqualTo(2)
  }

  @Test
  fun rowCount() {
    val divisibleByPage = PaginatedListModel(2, mutableListOf(1, 2, 3, 4))
    assertThat(divisibleByPage.rowCount).isEqualTo(2)
    divisibleByPage.goToLastPage()
    assertThat(divisibleByPage.rowCount).isEqualTo(2)

    val indivisibleByPage = PaginatedListModel(3, mutableListOf(1, 2, 3, 4))
    assertThat(indivisibleByPage.rowCount).isEqualTo(3)
    indivisibleByPage.goToLastPage()
    assertThat(indivisibleByPage.rowCount).isEqualTo(1)
  }

  @Test
  fun pageNavigation() {
    val model = PaginatedListModel(1, mutableListOf(1, 2, 3))
    assertThat(model.isOnFirstPage).isTrue()
    assertThat(model.isOnLastPage).isFalse()

    model.goToNextPage()
    assertThat(model.isOnFirstPage).isFalse()
    assertThat(model.isOnLastPage).isFalse()

    model.goToPrevPage()
    assertThat(model.isOnFirstPage).isTrue()
    assertThat(model.isOnLastPage).isFalse()

    model.goToLastPage()
    assertThat(model.isOnFirstPage).isFalse()
    assertThat(model.isOnLastPage).isTrue()

    model.goToFirstPage()
    assertThat(model.isOnFirstPage).isTrue()
    assertThat(model.isOnLastPage).isFalse()
  }

  @Test
  fun pageSizeMustBePositive() {
    thrown.expect(IllegalArgumentException::class.java)
    thrown.expectMessage("Page size must be positive, was -1")
    PaginatedListModel(-1, mutableListOf())
  }

  @Test
  fun tableWithEmptyDataShouldHaveZeroRows() {
    val model = PaginatedListModel(10, mutableListOf())
    assertThat(model.rowCount).isEqualTo(0)
  }

  @Test
  fun pageSizeCanBeChanged() {
    val model = PaginatedListModel(3, (1..6).toMutableList())

    // Changing page size should change visible rows.
    model.updatePageSize(4)
    assertThat(model.rowCount).isEqualTo(4)

    // Changing page size should reset the current page index.
    model.goToNextPage()
    model.updatePageSize(3)
    assertThat(model.rowCount).isEqualTo(3)
    assertThat(model.pageIndex).isEqualTo(0)

    // Keeping the same page size should not reset the page index.
    model.goToLastPage()
    model.updatePageSize(3)
    assertThat(model.rowCount).isEqualTo(3)
    assertThat(model.pageIndex).isEqualTo(1)
  }
}

class PaginatedListModel(pageSize: Int, val data: MutableList<Int>) : AbstractPaginatedTableModel(pageSize) {
  override fun getDataSize(): Int {
    return data.size
  }

  override fun getDataValueAt(dataIndex: Int, columnIndex: Int): Any {
    return data[dataIndex]
  }

  override fun sortData(sortKeys: List<RowSorter.SortKey>) {
    if (sortKeys[0].sortOrder == SortOrder.ASCENDING) {
      data.sort()
    }
    else {
      data.sortDescending()
    }
  }

  override fun getColumnCount(): Int {
    return 1
  }
}