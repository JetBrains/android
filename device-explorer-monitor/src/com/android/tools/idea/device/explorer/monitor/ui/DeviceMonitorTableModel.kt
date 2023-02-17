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
package com.android.tools.idea.device.explorer.monitor.ui

import com.android.tools.idea.device.explorer.monitor.processes.ProcessInfo
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableColumn

class DeviceMonitorTableModel : AbstractTableModel() {
  private val rows = mutableListOf<ProcessInfo>()
  private val columns = mutableListOf<TableColumn>()
  override fun getRowCount(): Int = rows.size

  override fun getColumnCount(): Int = columns.size

  override fun getColumnName(columnIndex: Int): String = columns[columnIndex].headerValue as String

  override fun getColumnClass(columnIndex: Int): Class<*> = ProcessInfo::class.java

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = rows[rowIndex]

  fun getValueForRow(rowIndex: Int): ProcessInfo = rows[rowIndex]

  fun clearProcesses() {
    if (rows.size > 0) {
      rows.clear()
      fireTableDataChanged()
    }
  }

  fun updateProcessRows(processRows: List<ProcessInfo>) {
    rows.clear()
    rows.addAll(processRows)
    fireTableDataChanged()
  }

  fun updateColumns(list: List<TableColumn>) {
    columns.clear()
    columns.addAll(list)
    fireTableStructureChanged()
  }
}