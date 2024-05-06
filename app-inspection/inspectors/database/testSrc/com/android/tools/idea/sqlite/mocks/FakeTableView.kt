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
package com.android.tools.idea.sqlite.mocks

import com.android.tools.idea.sqlite.ui.tableView.OrderBy
import com.android.tools.idea.sqlite.ui.tableView.RowDiffOperation
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.android.tools.idea.sqlite.ui.tableView.ViewColumn
import javax.swing.JComponent
import org.mockito.Mockito.mock

open class FakeTableView : TableView {

  val errorReported = mutableListOf<Pair<String, Throwable?>>()

  val listeners = mutableListOf<TableView.Listener>()

  override val component = mock(JComponent::class.java)

  override fun resetView() {}

  override fun startTableLoading() {}

  override fun showTableColumns(columns: List<ViewColumn>) {}

  override fun stopTableLoading() {}

  override fun reportError(message: String, t: Throwable?) {
    errorReported.add(Pair(message, t))
  }

  override fun setFetchPreviousRowsButtonState(enable: Boolean) {}

  override fun setFetchNextRowsButtonState(enable: Boolean) {}

  override fun setEditable(isEditable: Boolean) {}

  override fun addListener(listener: TableView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: TableView.Listener) {
    listeners.remove(listener)
  }

  override fun updateRows(rowDiffOperations: List<RowDiffOperation>) {}

  override fun setEmptyText(text: String) {}

  override fun setRowOffset(rowOffset: Int) {}

  override fun revertLastTableCellEdit() {}

  override fun setColumnSortIndicator(orderBy: OrderBy) {}

  override fun setLiveUpdatesButtonState(state: Boolean) {}

  override fun setRefreshButtonState(state: Boolean) {}

  override fun showPageSizeValue(maxRowCount: Int) {}
}
