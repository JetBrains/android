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
package com.android.tools.idea.sqlite.jdbc

import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteRow
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.SequentialTaskExecutor
import java.sql.JDBCType
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Implementation of [SqliteResultSet] for a local Sqlite file using the JDBC driver.
 */
class SqliteJdbcResultSet(
  private val service: SqliteJdbcService,
  private val statement: PreparedStatement,
  private val resultSet: ResultSet
) : SqliteResultSet {

  /**
   * It is safe to use [LazyThreadSafetyMode.NONE] because
   * the property is accessed from a [SequentialTaskExecutor] with a single thread.
   */
  private val columns: ArrayList<SqliteColumn> by lazy(LazyThreadSafetyMode.NONE) {
    val newColumns = ArrayList<SqliteColumn>()
    val metaData = resultSet.metaData

    for (i in 1..metaData.columnCount) {
      val column = SqliteColumn(metaData.getColumnName(i), JDBCType.valueOf(metaData.getColumnType(i)))
      newColumns.add(column)
    }

    newColumns
  }

  override var rowBatchSize: Int = 10
    set(value) {
      if (value <= 0) {
        throw IllegalArgumentException("Row batch size must be >= 1")
      }
      field = value
    }

  override fun columns(): ListenableFuture<List<SqliteColumn>> = service.sequentialTaskExecutor.executeAsync {
    columns
  }

  override fun nextRowBatch(): ListenableFuture<List<SqliteRow>> {
    return service.sequentialTaskExecutor.executeAsync {
      if (Disposer.isDisposed(this)) {
        throw IllegalStateException("ResultSet has been closed")
      }
      val rows = ArrayList<SqliteRow>()
      repeat(rowBatchSize) {
        if (!resultSet.next())
          return@repeat
        rows.add(currentRow())
      }
      rows
    }
  }

  private fun currentRow(): SqliteRow = SqliteRow(columns.mapIndexed { i, column ->
    SqliteColumnValue(column, resultSet.getObject(i+1))
  })

  override fun dispose() {
    service.sequentialTaskExecutor.executeAsync {
      resultSet.close()
      statement.close()
    }.get()
  }
}
