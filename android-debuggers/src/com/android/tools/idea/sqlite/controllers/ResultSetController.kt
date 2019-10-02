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
import com.android.tools.idea.sqlite.ui.tableView.TableView
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
  private val view: TableView,
  private val tableName: String?,
  private val resultSet: SqliteResultSet,
  private val edtExecutor: FutureCallbackExecutor
) : Disposable {
  /** The number of rows to retrieve per service invocation (to prevent too many round trip per row) */
  private val rowBatchSize = 50     //TODO(b/131589065)
  /** The maximum number of rows to retrieve (to prevent unbounded memory/cpu usage) */
  private val maxRowCount = 1_000   //TODO(b/131589065)

  init {
    Disposer.register(parentDisposable, this)
    Disposer.register(this, resultSet)
  }

  fun setUp() {
    view.startTableLoading()

    val futureDisplayRows = edtExecutor.transformAsync(resultSet.columns) { columns ->
      guardDisposed {
        view.showTableColumns(columns!!)

        // Start fetching rows
        fetchRows()
      }
    }

    val futureCatching = edtExecutor.catching(futureDisplayRows, Throwable::class.java) { error ->
      guardDisposed {
        val message = "Error retrieving rows ${if(tableName != null) "for table \"$tableName\"" else ""}"
        view.reportError(message, error)
      }
    }

    edtExecutor.finallySync(futureCatching) {
      guardDisposed {
        view.stopTableLoading()
      }
    }
  }

  override fun dispose() { }

  private fun fetchRows() : ListenableFuture<Unit> {
    resultSet.rowBatchSize = rowBatchSize
    return fetchRowBatch(0)
  }

  private fun fetchRowBatch(currentRowCount: Int) : ListenableFuture<Unit> {
    return guardDisposedFuture {
      if (currentRowCount >= maxRowCount) {
        Futures.immediateFuture(Unit)
      }
      else {
        edtExecutor.transformAsync(resultSet.nextRowBatch()) { rows ->
          guardDisposed {
            if (rows!!.isEmpty()) {
              Futures.immediateFuture(Unit)
            }
            else {
              view.showTableRowBatch(rows)
              fetchRowBatch(currentRowCount + rows.size)
            }
          }
        }
      }
    }
  }

  private fun <V> guardDisposed(block: () -> V): V {
    return if (Disposer.isDisposed(this)) throw ProcessCanceledException() else block.invoke()
  }

  private fun <V> guardDisposedFuture(block: () -> ListenableFuture<V>): ListenableFuture<V> {
    return if (Disposer.isDisposed(this)) throw ProcessCanceledException() else block.invoke()
  }
}
