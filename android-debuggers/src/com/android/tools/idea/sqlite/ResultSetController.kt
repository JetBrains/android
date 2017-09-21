/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.sqlite

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrent.EdtExecutor
import com.android.tools.idea.concurrent.FutureCallbackExecutor
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.SqliteView
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
 */

@UiThread
class ResultSetController(
  sqliteController: SqliteController,
  private val view: SqliteView,
  private val table: SqliteTable,
  private val resultSet: SqliteResultSet,
  executor: EdtExecutor
) : Disposable {
  private val edtExecutor: FutureCallbackExecutor = FutureCallbackExecutor.wrap(executor)

  /** The number of rows to retrieve per service invocation (to prevent too many round trip per row) */
  private val rowBatchSize = 50     //TODO(b/131589065)
  /** The maximum number of rows to retrieve (to prevent unbounded memory/cpu usage) */
  private val maxRowCount = 1_000   //TODO(b/131589065)

  init {
    Disposer.register(sqliteController, this)
    Disposer.register(this, resultSet)
  }

  fun start() {
    view.startTableLoading(table)

    val futureDisplayRows = edtExecutor.transformAsync(resultSet.columns()) { columns ->
      guardDisposed {
        view.showTableColumns(columns!!)

        // Start fetching rows
        fetchRows()
      }
    }

    val futureCatching = edtExecutor.catching(futureDisplayRows, Throwable::class.java) { error ->
      guardDisposed {
        view.reportErrorRelatedToTable(table, "Error retrieving contents of table", error)
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
