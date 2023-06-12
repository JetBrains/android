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

import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.ResultSetSqliteColumn
import com.android.tools.idea.sqlite.model.RowIdName
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteValue
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class FakeSqliteResultSet(
  size: Int = 100,
  columns: List<ResultSetSqliteColumn> =
    listOf(
      ResultSetSqliteColumn("id", SqliteAffinity.INTEGER, true, false),
      ResultSetSqliteColumn(RowIdName.ROWID.stringName, SqliteAffinity.INTEGER, true, false)
    )
) : SqliteResultSet {
  val _columns = columns
  val rows = mutableListOf<SqliteRow>()

  val invocations = mutableListOf<List<SqliteRow>>()

  init {
    for (i in 0 until size) {
      rows.add(SqliteRow(_columns.map { SqliteColumnValue(it.name, SqliteValue.fromAny(i)) }))
    }
  }

  override val columns: ListenableFuture<List<ResultSetSqliteColumn>>
    get() = Futures.immediateFuture(_columns)

  override val totalRowCount: ListenableFuture<Int>
    get() = Futures.immediateFuture(rows.size)

  override fun getRowBatch(rowOffset: Int, rowBatchSize: Int): ListenableFuture<List<SqliteRow>> {
    assert(rowOffset >= 0)
    assert(rowBatchSize > 0)

    val toIndex = if (rowOffset + rowBatchSize > rows.size) rows.size else rowOffset + rowBatchSize

    val rows = rows.subList(rowOffset, toIndex).toList()
    invocations.add(rows)
    return Futures.immediateFuture(rows)
  }

  override fun dispose() {}

  fun insertRowAtIndex(index: Int, value: Int) {
    rows.add(
      index,
      SqliteRow(_columns.map { SqliteColumnValue(it.name, SqliteValue.fromAny(value)) })
    )
  }

  fun deleteRowAtIndex(index: Int) {
    rows.removeAt(index)
  }
}
