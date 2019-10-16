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
import kotlin.properties.Delegates.vetoable

/**
 * Implementation of [SqliteResultSet] for a local Sqlite file using the JDBC driver.
 */
class SqliteJdbcResultSet(
  private val service: SqliteJdbcService,
  private val statement: PreparedStatement,
  private val resultSet: ResultSet
) : SqliteResultSet {

  /**
   * It's safe to use [LazyThreadSafetyMode.NONE] because the property is accessed from a [SequentialTaskExecutor] with a single thread.
   */
  private val _columns: List<SqliteColumn> by lazy(LazyThreadSafetyMode.NONE) {
    val metaData = resultSet.metaData
    (1..metaData.columnCount).map { i -> SqliteColumn(metaData.getColumnName(i), JDBCType.valueOf(metaData.getColumnType(i))) }
  }

  override val columns get() = service.sequentialTaskExecutor.executeAsync { _columns }

  override var rowBatchSize: Int by vetoable(10) { _, _, newValue ->
    require(newValue > 0) { "Row batch size must be > 0." }
    true
  }

  override fun nextRowBatch(): ListenableFuture<List<SqliteRow>> {
    return service.sequentialTaskExecutor.executeAsync {
      check(!Disposer.isDisposed(this)) { "ResultSet has already been closed." }

      val rows = ArrayList<SqliteRow>()
      repeat(rowBatchSize) {
        if (!resultSet.next()) return@repeat
        rows.add(createCurrentRow())
      }
      rows
    }
  }

  override fun dispose() {
    service.sequentialTaskExecutor.executeAndAwait {
      resultSet.close()
      statement.close()
    }
  }

  private fun createCurrentRow() = SqliteRow(_columns.mapIndexed { i, column ->
    SqliteColumnValue(column, resultSet.getObject(i + 1))
  })
}
