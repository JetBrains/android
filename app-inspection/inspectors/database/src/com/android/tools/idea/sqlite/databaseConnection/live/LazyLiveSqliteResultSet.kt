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
package com.android.tools.idea.sqlite.databaseConnection.live

import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.sqlite.DatabaseInspectorMessenger
import com.android.tools.idea.sqlite.databaseConnection.checkOffsetAndSize
import com.android.tools.idea.sqlite.model.ResultSetSqliteColumn
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

class LazyLiveSqliteResultSet(
  private val sqliteStatement: SqliteStatement,
  messenger: DatabaseInspectorMessenger,
  connectionId: Int,
  private val taskExecutor: Executor
) : LiveSqliteResultSet(sqliteStatement, messenger, connectionId, taskExecutor) {

  override val columns: ListenableFuture<List<ResultSetSqliteColumn>>
    get() = sendQueryCommand(sqliteStatement).mapToColumns(taskExecutor)

  override val totalRowCount: ListenableFuture<Int>
    get() =
      sendQueryCommand(sqliteStatement).transform(taskExecutor) { response ->
        response.query.rowsList.size
      }

  override fun getRowBatch(
    rowOffset: Int,
    rowBatchSize: Int,
    responseSizeByteLimitHint: Long?
  ): ListenableFuture<List<SqliteRow>> {
    checkOffsetAndSize(rowOffset, rowBatchSize)
    return sendQueryCommand(sqliteStatement, responseSizeByteLimitHint).transform(taskExecutor) {
      response ->
      val columnNames = response.query.columnNamesList
      val rows = response.query.rowsList
      rows.subList(rowOffset, minOf(rowOffset + rowBatchSize, rows.size)).map {
        val sqliteColumnValues =
          it.valuesList.mapIndexed { index, cellValue ->
            cellValue.toSqliteColumnValue(columnNames[index])
          }
        SqliteRow(sqliteColumnValues)
      }
    }
  }
}
