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
package com.android.tools.idea.sqlite.databaseConnection.jdbc

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.SequentialTaskExecutor
import java.sql.Connection
import java.sql.JDBCType
import java.sql.ResultSet

class JdbcSqliteResultSet(
  private val service: JdbcDatabaseConnection,
  private val connection: Connection,
  private val sqliteStatement: SqliteStatement
) : SqliteResultSet {

  /**
   * It's safe to use [LazyThreadSafetyMode.NONE] because the property is accessed from a [SequentialTaskExecutor] with a single thread.
   */
  private val _columns: List<SqliteColumn> by lazy(LazyThreadSafetyMode.NONE) {
    val preparedStatement = connection.resolvePreparedStatement(sqliteStatement)
    val resultSet = preparedStatement.executeQuery()

    val metaData = resultSet.metaData
    (1..metaData.columnCount).map { i ->
      val tableName = metaData.getTableName(i)
      val columnName = metaData.getColumnName(i)

      val keyColumnsNames = connection.getColumnNamesInPrimaryKey(tableName)
      SqliteColumn(
        metaData.getColumnName(i),
        SqliteAffinity.fromJDBCType(JDBCType.valueOf(metaData.getColumnType(i))),
        metaData.isNullable(i) == 1,
        keyColumnsNames.contains(columnName)
      )
    }
  }

  private val _rowCount: Int by lazy(LazyThreadSafetyMode.NONE) {
    check(!Disposer.isDisposed(this)) { "ResultSet has already been closed." }
    check(!connection.isClosed) { "The connection has been closed." }

    val countQuery = "SELECT COUNT(*) FROM (${sqliteStatement.sqliteStatementText})"
    val preparedStatement = connection.resolvePreparedStatement(SqliteStatement(countQuery, sqliteStatement.parametersValues))
    val resultSet = preparedStatement.executeQuery()

    resultSet.next()
    val count = resultSet.getInt(1)

    resultSet.close()
    preparedStatement.close()

    count
  }

  override val columns get() = service.sequentialTaskExecutor.executeAsync { _columns }

  override val rowCount get() = service.sequentialTaskExecutor.executeAsync { _rowCount }

  override fun getRowBatch(rowOffset: Int, rowBatchSize: Int): ListenableFuture<List<SqliteRow>> {
    require(rowOffset >= 0) { "Offset must be >= 0." }
    require(rowBatchSize > 0) { "Row batch size must be > 0." }

    return service.sequentialTaskExecutor.executeAsync {
      check(!Disposer.isDisposed(this)) { "ResultSet has already been closed." }
      check(!connection.isClosed) { "The connection has been closed." }

      val limitOffsetQuery = "SELECT * FROM (${sqliteStatement.sqliteStatementText}) LIMIT $rowOffset, $rowBatchSize"
      val preparedStatement = connection.resolvePreparedStatement(SqliteStatement(limitOffsetQuery, sqliteStatement.parametersValues))
      val resultSet = preparedStatement.executeQuery()

      val rows = ArrayList<SqliteRow>()
      while (resultSet.next()) {
        rows.add(createCurrentRow(resultSet))
      }

      resultSet.close()
      preparedStatement.close()

      rows
    }
  }

  @WorkerThread
  private fun createCurrentRow(resultSet: ResultSet): SqliteRow {
    return SqliteRow(_columns.mapIndexed { i, column -> SqliteColumnValue(column, resultSet.getObject(i + 1)) })
  }

  override fun dispose() {
  }
}