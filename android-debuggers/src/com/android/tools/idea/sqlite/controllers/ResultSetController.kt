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
package com.android.tools.idea.sqlite.controllers

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.android.tools.idea.sqlite.ui.tableView.TableViewListener
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer

/**
 * Controller specialized in displaying rows and columns from a [SqliteResultSet].
 *
 * The ownership of the [SqliteResultSet] is transferred to the [ResultSetController],
 * i.e. it is closed when [dispose] is called.
 *
 * The [SqliteResultSet] is not necessarily associated with a real table in the database, in those cases the [tableName] will be null.
 */
@UiThread
class ResultSetController(
  parentDisposable: Disposable,
  private var rowBatchSize: Int = 50,
  private val view: TableView,
  private val tableName: String?,
  private val resultSet: SqliteResultSet,
  private val edtExecutor: FutureCallbackExecutor
) : Disposable {
  private val listener = TableViewListenerImpl()

  init {
    Disposer.register(parentDisposable, this)
    Disposer.register(this, resultSet)
  }

  fun setUp() {
    if (Disposer.isDisposed(this)) throw ProcessCanceledException()

    view.addListener(listener)

    view.showRowCount(rowBatchSize)
    view.startTableLoading()

    val futureDisplayRows = edtExecutor.transformAsync(resultSet.columns) { columns ->
      if (Disposer.isDisposed(this)) throw ProcessCanceledException()
      view.showTableColumns(columns!!)

      fetchNextRows()
    }

    val futureCatching = handleFetchRowsError(futureDisplayRows)

    edtExecutor.finallySync(futureCatching) {
      if (Disposer.isDisposed(this)) throw ProcessCanceledException()
      view.stopTableLoading()
    }
  }

  override fun dispose() {
    view.removeListener(listener)
  }

  private fun fetchNextRows() : ListenableFuture<Unit> {
    view.removeRows()

    resultSet.rowBatchSize = rowBatchSize
    val future = fetchRowBatch(0) { resultSet.nextRowBatch() }

    return edtExecutor.transform(future) { hasMoreRows ->
      if (!hasMoreRows) view.setFetchNextRowsButtonState(false)
      return@transform
    }
  }

  /**
   * Fetches rows through the [SqliteResultSet].
   *
   * The future returned by this function resolves to true if there are more rows to be fetched, false otherwise.
   */
  private fun fetchRowBatch(fetchedRowsCount: Int, rowsProvider: () -> ListenableFuture<List<SqliteRow>>) : ListenableFuture<Boolean> {
    if (Disposer.isDisposed(this)) throw ProcessCanceledException()

    return if (fetchedRowsCount >= rowBatchSize) {
      Futures.immediateFuture(true)
    }
    else {
      edtExecutor.transformAsync(rowsProvider()) { rows ->
        if (Disposer.isDisposed(this)) throw ProcessCanceledException()
        if (rows!!.isEmpty()) {
          Futures.immediateFuture(false)
        }
        else {
          view.showTableRowBatch(rows)
          fetchRowBatch(fetchedRowsCount + rows.size, rowsProvider)
        }
      }
    }
  }

  private fun handleFetchRowsError(future: ListenableFuture<Unit>): ListenableFuture<Any> {
    return edtExecutor.catching(future, Throwable::class.java) { error ->
      if (Disposer.isDisposed(this)) throw ProcessCanceledException()

      val message = "Error retrieving rows ${if (tableName != null) "for table \"$tableName\"" else ""}"
      view.reportError(message, error)
    }
  }

  private inner class TableViewListenerImpl : TableViewListener {
    override fun rowCountChanged(rowCount: Int) {
      // TODO(next CL) update UI
      rowBatchSize = rowCount
    }

    override fun loadPreviousRowsInvoked() {
      // TODO(next CL)
    }

    override fun loadNextRowsInvoked() {
      val future = fetchNextRows()
      handleFetchRowsError(future)
    }
  }
}
