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
package com.android.tools.adtui

import com.android.tools.adtui.model.PaginatedListModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PaginatedTableViewTest {
  @Test
  fun navigatesPages() {
    val tableView = PaginatedTableView(PaginatedListModel(2, mutableListOf(1, 2, 3)))
    assertThat(tableView.table.rowCount).isEqualTo(2)
    assertThat(tableView.firstPageButton.isEnabled).isFalse()
    assertThat(tableView.prevPageButton.isEnabled).isFalse()
    assertThat(tableView.nextPageButton.isEnabled).isTrue()
    assertThat(tableView.lastPageButton.isEnabled).isTrue()
    assertThat(tableView.pageInfoLabel.text).isEqualTo("1 - 2 of 3")

    // Go to next page
    tableView.nextPageButton.doClick()
    assertThat(tableView.table.rowCount).isEqualTo(1)
    assertThat(tableView.firstPageButton.isEnabled).isTrue()
    assertThat(tableView.prevPageButton.isEnabled).isTrue()
    assertThat(tableView.nextPageButton.isEnabled).isFalse()
    assertThat(tableView.lastPageButton.isEnabled).isFalse()
    assertThat(tableView.pageInfoLabel.text).isEqualTo("3 - 3 of 3")

    // Go to previous page
    tableView.prevPageButton.doClick()
    assertThat(tableView.table.rowCount).isEqualTo(2)
    assertThat(tableView.firstPageButton.isEnabled).isFalse()
    assertThat(tableView.prevPageButton.isEnabled).isFalse()
    assertThat(tableView.nextPageButton.isEnabled).isTrue()
    assertThat(tableView.lastPageButton.isEnabled).isTrue()
    assertThat(tableView.pageInfoLabel.text).isEqualTo("1 - 2 of 3")

    // Go to last page
    tableView.nextPageButton.doClick()
    assertThat(tableView.table.rowCount).isEqualTo(1)
    assertThat(tableView.firstPageButton.isEnabled).isTrue()
    assertThat(tableView.prevPageButton.isEnabled).isTrue()
    assertThat(tableView.nextPageButton.isEnabled).isFalse()
    assertThat(tableView.lastPageButton.isEnabled).isFalse()
    assertThat(tableView.pageInfoLabel.text).isEqualTo("3 - 3 of 3")

    // Go to first page
    tableView.prevPageButton.doClick()
    assertThat(tableView.table.rowCount).isEqualTo(2)
    assertThat(tableView.firstPageButton.isEnabled).isFalse()
    assertThat(tableView.prevPageButton.isEnabled).isFalse()
    assertThat(tableView.nextPageButton.isEnabled).isTrue()
    assertThat(tableView.lastPageButton.isEnabled).isTrue()
    assertThat(tableView.pageInfoLabel.text).isEqualTo("1 - 2 of 3")
  }

  @Test
  fun sortsAllPages() {
    val tableView = PaginatedTableView(PaginatedListModel(2, mutableListOf(1, 2, 3)))
    tableView.table.rowSorter.toggleSortOrder(0)
    assertThat(tableView.table.getValueAt(0, 0)).isEqualTo(1)
    assertThat(tableView.table.getValueAt(1, 0)).isEqualTo(2)
    tableView.table.rowSorter.toggleSortOrder(0)
    assertThat(tableView.table.getValueAt(0, 0)).isEqualTo(3)
    assertThat(tableView.table.getValueAt(1, 0)).isEqualTo(2)
  }

  @Test
  fun emptyTable() {
    val tableView = PaginatedTableView(PaginatedListModel(10, mutableListOf()))
    assertThat(tableView.firstPageButton.isEnabled).isFalse()
    assertThat(tableView.prevPageButton.isEnabled).isFalse()
    assertThat(tableView.nextPageButton.isEnabled).isFalse()
    assertThat(tableView.lastPageButton.isEnabled).isFalse()
  }

  @Test
  fun updatePageSize() {
    val tableView = PaginatedTableView(PaginatedListModel(10, (1..50).toMutableList()), arrayOf(10, 25, 100))
    assertThat(tableView.pageSizeComboBox.isVisible).isTrue()

    // Combo box is pre-selected.
    assertThat(tableView.pageSizeComboBox.selectedIndex).isEqualTo(0)

    // Change page size by selecting a different item.
    tableView.pageSizeComboBox.selectedIndex = 1
    assertThat(tableView.tableModel.pageSize).isEqualTo(25)

    // Change page size by setting a value directly.
    tableView.pageSizeComboBox.item = 100
    assertThat(tableView.tableModel.pageSize).isEqualTo(100)
  }

  @Test
  fun pageSizeDropdownCanBeHidden() {
    val tableView = PaginatedTableView(PaginatedListModel(10, mutableListOf(1, 2, 3)), emptyArray())
    assertThat(tableView.pageSizeComboBox.isVisible).isFalse()
  }
}