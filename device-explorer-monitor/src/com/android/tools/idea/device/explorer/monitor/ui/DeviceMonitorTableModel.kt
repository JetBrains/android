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
import com.android.tools.idea.device.explorer.monitor.processes.isPidOnly
import com.android.tools.idea.device.explorer.monitor.processes.safeProcessName
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
    val rowsToDelete = rows.size
    if (rowsToDelete > 0) {
      rows.clear()
      fireTableRowsDeleted(0, rowsToDelete - 1)
    }
  }

  fun updateProcessRows(newRows: List<ProcessInfo>) {
    if (newRows.isEmpty()) {
      clearProcesses()
    } else if (rows.isEmpty()) {
      rows.addAll(newRows.sortedWith(ProcessInfoNameComparator))
      fireTableRowsInserted(0, rows.size - 1)
    } else {
      val newSortedRows = newRows.sortedWith(ProcessInfoNameComparator)
      var currentRowIndex = 0
      var newRowIndex = 0

      while (currentRowIndex < rows.size && newRowIndex < newSortedRows.size) {
        val oldProcessInfo = rows[currentRowIndex]
        val newProcessInfo = newSortedRows[newRowIndex]

        // Update ProcessInfo if process name are the same
        if (ProcessInfoNameComparator.compare(oldProcessInfo, newProcessInfo) == 0) {
          // Compare data to make sure we have the updated version
          if (oldProcessInfo != newProcessInfo) {
            rows[currentRowIndex] = newSortedRows[newRowIndex]
            fireTableRowsUpdated(currentRowIndex, currentRowIndex)
          }
          currentRowIndex++
          newRowIndex++

          // Remove old ProcessInfo since the old one comes before the next new ProcessInfo
        } else if (ProcessInfoNameComparator.compare(oldProcessInfo, newProcessInfo) < 0) {
          rows.removeAt(currentRowIndex)
          fireTableRowsDeleted(currentRowIndex, currentRowIndex)

          // Add new ProcessInfo since it comes before the old ProcessInfo
        } else {
          rows.add(currentRowIndex, newProcessInfo)
          fireTableRowsInserted(currentRowIndex, currentRowIndex)
          currentRowIndex++
          newRowIndex++
        }
      }

      while (currentRowIndex < rows.size) {
        rows.removeAt(currentRowIndex)
        fireTableRowsDeleted(currentRowIndex, currentRowIndex)
      }

      while (newRowIndex < newSortedRows.size) {
        rows.add(newSortedRows[newRowIndex])
        fireTableRowsInserted(newRowIndex, newRowIndex)
        newRowIndex++
      }
    }
  }

  fun removeOldColumnsAndAddColumns(list: List<TableColumn>) {
    columns.clear()
    columns.addAll(list)
    fireTableStructureChanged()
  }

  object ProcessInfoNameComparator : Comparator<ProcessInfo?> by nullsFirst(ProcessInfoNonNullComparator()) {
    private class ProcessInfoNonNullComparator : Comparator<ProcessInfo> {
      override fun compare(o1: ProcessInfo, o2: ProcessInfo): Int {
        return if (o1.isPidOnly && o2.isPidOnly) {
          o1.pid.compareTo(o2.pid)
        }
        else if (o1.isPidOnly) {
          1
        }
        else if (o2.isPidOnly) {
          -1
        }
        else {
          o1.safeProcessName.compareTo(o2.safeProcessName)
        }
      }
    }
  }
}