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
import com.android.tools.idea.sqlite.model.SqliteRow
import javax.swing.JComponent

/**
 * Interface used to abstract views that display the content of SQL tables.
 */
interface TableView {
  /**
   * The JComponent containing the view's UI.
   */
  val component: JComponent

  /**
   * Removes data for both columns and rows and updates the view.
   */
  fun resetView()
  /**
   * Removes data for rows and updates the view.
   */
  fun removeRows()

  /**
   * Updates the UI to show the number of rows loaded per page.
   */
  fun showPageSizeValue(maxRowCount: Int)

  fun startTableLoading()
  fun showTableColumns(columns: List<SqliteColumn>)
  fun showTableRowBatch(rows: List<SqliteRow>)
  fun stopTableLoading()
  fun reportError(message: String, t: Throwable?)

  /**
   * Enables or disables the button to fetch the previous page of rows.
   */
  fun setFetchPreviousRowsButtonState(enable: Boolean)

  /**
   * Enables or disables the button to fetch the next page of rows.
   */
  fun setFetchNextRowsButtonState(enable: Boolean)

  /**
   * Enable or disable editing of table cells.
   */
  fun setEditable(isEditable: Boolean)

  fun addListener(listener: Listener)
  fun removeListener(listener: Listener)

  interface Listener {
    fun loadPreviousRowsInvoked()
    fun loadNextRowsInvoked()
    fun loadFirstRowsInvoked()
    fun loadLastRowsInvoked()
    fun refreshDataInvoked()
    fun updateCellInvoked(targetRow: SqliteRow, targetColumn: SqliteColumn, newValue: Any?)

    /**
     * Invoked when the user changes the number of rows to display per page.
     */
    fun rowCountChanged(rowCount: Int)

    /**
     * Invoked when the user wants to order the data by a specific column
     */
    fun toggleOrderByColumnInvoked(sqliteColumn: SqliteColumn)
  }
}