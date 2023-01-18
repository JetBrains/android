/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.components.JBScrollPane
import javax.swing.BorderFactory
import javax.swing.SortOrder
import org.junit.Test

class CategoryTableTest {
  val scrollPane =
    JBScrollPane().also {
      it.setBounds(0, 0, 800, 400)
      // This clears the default 1px insets of the ScrollPane to keep layout simpler
      it.border = BorderFactory.createEmptyBorder()
    }

  val demo = CategoryTableDemo()
  val table = demo.table.also { it.addToScrollPane(scrollPane) }

  val fakeUi = FakeUi(scrollPane, createFakeWindow = true)
  val attributes = demo.columns.map { it.attribute }

  @Test
  fun group() {
    val values = demo.devices
    val sorted = groupAndSort(values, listOf(Status.attribute), emptyList())
    assertThat(sorted.map { it.status })
      .containsExactly("Offline", "Offline", "Offline", "Online", "Online", "Online")
  }

  @Test
  fun nestedGroup() {
    val values = demo.devices
    val sorted = groupAndSort(values, listOf(Status.attribute, Type.attribute), emptyList())
    assertThat(sorted.map { it.type })
      .containsExactly("Phone", "Phone", "Tablet", "Phone", "Phone", "Tablet")
  }

  @Test
  fun tableLayout() {
    scrollPane.setBounds(0, 0, 800, 400)
    fakeUi.layout()

    // All columns have fixed width except the first, which should absorb the extra width.
    assertThat(table.header.tableColumns.map { it.width })
      .containsExactly(410, 80, 80, 80, 150)
      .inOrder()

    scrollPane.setBounds(0, 0, 500, 400)
    fakeUi.layout()

    // We are space-constrained, take some space away from each element (except the last, which is
    // fixed-size).
    assertThat(table.header.tableColumns.map { it.width })
      .containsExactly(247, 34, 34, 34, 150)
      .inOrder()

    scrollPane.setBounds(0, 0, 410, 400)
    fakeUi.layout()

    // We have just enough width to give every component its minimum width.
    assertThat(table.header.tableColumns.map { it.width })
      .containsExactly(200, 20, 20, 20, 150)
      .inOrder()

    scrollPane.setBounds(0, 0, 200, 400)
    fakeUi.layout()

    // We don't have enough space for the minimum; the columns keep their width but the rendering is
    // truncated.
    assertThat(table.header.tableColumns.map { it.width })
      .containsExactly(200, 20, 20, 20, 150)
      .inOrder()
  }

  @Test
  fun sorting() {
    fakeUi.clickRelativeTo(scrollPane, 2, 2)
    assertThat(table.columnSorters)
      .containsExactly(ColumnSortOrder(table.columns[0].attribute, SortOrder.ASCENDING))

    fakeUi.clickRelativeTo(scrollPane, table.header.tableColumns[0].width + 2, 2)
    assertThat(table.columnSorters)
      .containsExactly(
        ColumnSortOrder(table.columns[1].attribute, SortOrder.ASCENDING),
        ColumnSortOrder(table.columns[0].attribute, SortOrder.ASCENDING)
      )

    fakeUi.clickRelativeTo(scrollPane, table.header.tableColumns[0].width + 2, 2)
    assertThat(table.columnSorters)
      .containsExactly(
        ColumnSortOrder(table.columns[1].attribute, SortOrder.DESCENDING),
        ColumnSortOrder(table.columns[0].attribute, SortOrder.ASCENDING)
      )
  }
}
