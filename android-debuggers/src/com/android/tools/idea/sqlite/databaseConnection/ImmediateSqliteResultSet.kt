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
package com.android.tools.idea.sqlite.databaseConnection

import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteRow
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Implementation of [SqliteResultSet] that takes all the data from its constructor.
 */
class ImmediateSqliteResultSet(private val rows: List<SqliteRow>) : SqliteResultSet {
  override val totalRowCount: ListenableFuture<Int> = Futures.immediateFuture(rows.size)

  override val columns: ListenableFuture<List<SqliteColumn>>
    get() {
      return if (rows.isEmpty()) return Futures.immediateFuture(emptyList())
      else Futures.immediateFuture(rows[0].values.map { it.column })
    }

  override fun getRowBatch(rowOffset: Int, rowBatchSize: Int): ListenableFuture<List<SqliteRow>> {
    return Futures.immediateFuture(rows.subList(rowOffset, minOf(rowOffset + rowBatchSize, rows.size)))
  }

  override fun dispose() { }
}