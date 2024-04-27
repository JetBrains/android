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
package com.android.tools.idea.appinspection.inspectors.network.view.connectionsview

import com.android.tools.idea.appinspection.inspectors.network.model.connections.ConnectionData
import com.android.tools.idea.appinspection.inspectors.network.model.connections.SelectionRangeDataFetcher
import javax.swing.table.AbstractTableModel

internal class ConnectionsTableModel(selectionRangeDataFetcher: SelectionRangeDataFetcher) :
  AbstractTableModel() {
  private lateinit var dataList: List<ConnectionData>

  init {
    selectionRangeDataFetcher.addOnChangedListener { list ->
      dataList = list
      fireTableDataChanged()
    }
  }

  override fun getRowCount() = dataList.size

  override fun getColumnCount() = ConnectionColumn.values().size

  override fun getColumnName(column: Int) = ConnectionColumn.values()[column].displayString

  override fun getValueAt(rowIndex: Int, columnIndex: Int) =
    ConnectionColumn.values()[columnIndex].getValueFrom(dataList[rowIndex])

  override fun getColumnClass(columnIndex: Int) = ConnectionColumn.values()[columnIndex].type

  fun getConnectionData(rowIndex: Int) = dataList[rowIndex]
}
