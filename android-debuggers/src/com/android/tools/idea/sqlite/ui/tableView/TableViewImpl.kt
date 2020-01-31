/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.sqlite.ui.tableView

import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.ui.notifyError
import com.android.tools.idea.sqlite.ui.setResultSetTableColumns
import com.android.tools.idea.sqlite.ui.setupResultSetTable
import java.util.Vector
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel

/**
 * Abstraction on the UI component used to display tables.
 */
class TableViewImpl : TableView {
  private val columnClass = SqliteColumnValue::class.java
  private val tableModel: DefaultTableModel by lazy {
    object : DefaultTableModel() {
      override fun getColumnClass(columnIndex: Int): Class<*> = columnClass
    }
  }

  private val panel = TablePanel()

  override val component: JComponent = panel.root

  override fun resetView() {
    tableModel.dataVector.clear()
    tableModel.rowCount = 0
    tableModel.columnCount = 0
  }

  override fun startTableLoading() {
    panel.table.setupResultSetTable(tableModel, columnClass)
    tableModel.rowCount = 0
    tableModel.columnCount = 0
    panel.table.setPaintBusy(true)
  }

  override fun showTableColumns(columns: List<SqliteColumn>) {
    columns.forEach { tableModel.addColumn(it.name) }
    panel.table.setResultSetTableColumns()
  }

  override fun showTableRowBatch(rows: List<SqliteRow>) {
    rows.forEach { row ->
      val values = Vector<SqliteColumnValue>()
      row.values.forEach { values.addElement(it) }
      tableModel.addRow(values)
    }
  }

  override fun stopTableLoading() {
    panel.table.setPaintBusy(false)
  }

  override fun reportError(message: String, t: Throwable) {
    notifyError(message, t)
  }
}