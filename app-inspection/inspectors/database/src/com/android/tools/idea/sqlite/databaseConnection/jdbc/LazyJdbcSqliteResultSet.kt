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
package com.android.tools.idea.sqlite.databaseConnection.jdbc

import com.android.tools.idea.sqlite.databaseConnection.checkOffsetAndSize
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.google.common.util.concurrent.ListenableFuture
import java.sql.Connection
import java.util.concurrent.Executor

class LazyJdbcSqliteResultSet(
  taskExecutor: Executor,
  connection: Connection,
  private val sqliteStatement: SqliteStatement
) : JdbcSqliteResultSet(taskExecutor, connection, sqliteStatement) {
  override val totalRowCount: ListenableFuture<Int>
    get() =
      getRowCount(sqliteStatement) {
        var rowCount = 0
        while (it.next()) {
          rowCount += 1
        }
        rowCount
      }

  override fun getRowBatch(rowOffset: Int, rowBatchSize: Int): ListenableFuture<List<SqliteRow>> {
    checkOffsetAndSize(rowOffset, rowBatchSize)
    return getRowBatch(sqliteStatement) { resultSet, columns ->
      val rows = ArrayList<SqliteRow>()
      while (resultSet.next()) {
        rows.add(createCurrentRow(resultSet, columns))
      }
      rows.subList(rowOffset, minOf(rowOffset + rowBatchSize, rows.size))
    }
  }
}
