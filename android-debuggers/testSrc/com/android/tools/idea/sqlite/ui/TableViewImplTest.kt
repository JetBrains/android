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
package com.android.tools.idea.sqlite.ui

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import javax.swing.JPanel
import javax.swing.JTable

private const val COLUMN_DEFAULT_WIDTH = 75

class TableViewImplTest : LightPlatformTestCase() {
  private lateinit var view: TableViewImpl
  private lateinit var fakeUi: FakeUi

  override fun setUp() {
    super.setUp()
    view = TableViewImpl()
    val component: JPanel = view.component as JPanel
    component.size = Dimension(600, 200)

    fakeUi = FakeUi(component)
  }

  fun testColumnsFitParentIfSpaceIsAvailable() {
    // Prepare
    val treeWalker = TreeWalker(view.component)
    val column = SqliteColumn("name", SqliteAffinity.NUMERIC, false, false)
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()
    val jbScrollPane = TreeWalker(table).ancestors().filterIsInstance<JBScrollPane>().first()

    // Act
    view.showTableColumns(listOf(column))
    fakeUi.layout()

    // Assert
    assertEquals(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS, table.autoResizeMode)
    
    assertEquals(598, table.size.width)
    assertEquals(598, table.columnModel.getColumn(0).width)

    assertEquals(0, jbScrollPane.horizontalScrollBar.model.minimum)
    assertEquals(598, jbScrollPane.horizontalScrollBar.model.maximum)
  }

  fun testTableIsScrollableIfTooManyColumns() {
    // Prepare
    val treeWalker = TreeWalker(view.component)

    val column = SqliteColumn("name", SqliteAffinity.NUMERIC, false, false)
    val columns = mutableListOf<SqliteColumn>()
    repeat(600 / COLUMN_DEFAULT_WIDTH) {
      columns.add(column)
    }

    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()
    val jbScrollPane = TreeWalker(table).ancestors().filterIsInstance<JBScrollPane>().first()

    // Act
    view.showTableColumns(columns)
    fakeUi.layout()

    // Assert
    assertEquals(JTable.AUTO_RESIZE_OFF, table.autoResizeMode)

    assertTrue(table.size.width > 598)
    assertEquals(COLUMN_DEFAULT_WIDTH, table.columnModel.getColumn(0).width)
    
    assertEquals(0, jbScrollPane.horizontalScrollBar.model.minimum)
    assertTrue(jbScrollPane.horizontalScrollBar.model.maximum > 598)
  }

  fun testSetEditableHidesReadOnlyLabelAndEnablesCellEditing() {
    // Prepare
    val treeWalker = TreeWalker(view.component)

    val readOnlyLabel = treeWalker.descendants().first { it.name == "read-only-label" }
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    view.showTableColumns(emptyList())

    // Act
    view.setEditable(true)

    // Assert
    assertFalse(readOnlyLabel.isVisible)
    assertTrue(table.model.isCellEditable(0, 0))
  }

  fun testSetNotEditableShowsReadOnlyLabelAndDisableCellEditing() {
    // Prepare
    val treeWalker = TreeWalker(view.component)

    val readOnlyLabel = treeWalker.descendants().first { it.name == "read-only-label" }
    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    view.showTableColumns(emptyList())

    // Act
    view.setEditable(false)


    // Assert
    assertTrue(readOnlyLabel.isVisible)
    assertFalse(table.model.isCellEditable(0, 0))
  }

  fun testClickOnColumnHeaderSortsTable() {
    // Prepare
    val treeWalker = TreeWalker(view.component)

    val table = treeWalker.descendants().filterIsInstance<JBTable>().first()

    val mockListener = mock(TableView.Listener::class.java)
    view.addListener(mockListener)

    val col = SqliteColumn("col", SqliteAffinity.INTEGER, false, false)
    val cols = listOf(col)
    val rows = listOf(SqliteRow(listOf(SqliteColumnValue(col, "val"))))

    view.startTableLoading()
    view.showTableColumns(cols)
    view.showTableRowBatch(rows)
    view.stopTableLoading()

    table.size = Dimension(600, 200)
    table.tableHeader.size = Dimension(600, 100)

    table.preferredSize = table.size
    TreeWalker(table).descendants().forEach { it.doLayout() }

    fakeUi = FakeUi(table.tableHeader)

    // Act
    fakeUi.mouse.click(597, 0)

    // Assert
    assertEquals(0, table.columnAtPoint(Point(597, 0)))
    verify(mockListener).toggleOrderByColumnInvoked(col)
  }
}