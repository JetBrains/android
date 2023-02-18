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
import com.android.tools.idea.concurrency.cancelOnDispose
import com.android.tools.idea.concurrency.executeAsync
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.ResultSetSqliteColumn
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteValue
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.util.Disposer
import java.sql.Connection
import java.sql.JDBCType
import java.sql.ResultSet
import java.util.concurrent.Executor

abstract class JdbcSqliteResultSet(
  private val taskExecutor: Executor,
  private val connection: Connection,
  private val sqliteStatement: SqliteStatement
) : SqliteResultSet {

  override val columns
    get() =
      taskExecutor
        .executeAsync {
          connection.resolvePreparedStatement(sqliteStatement).use { preparedStatement ->
            preparedStatement.executeQuery().use {
              val metaData = it.metaData
              (1..metaData.columnCount).map { i ->
                val tableName = metaData.getTableName(i)
                val columnName = metaData.getColumnName(i)

                val keyColumnsNames = connection.getColumnNamesInPrimaryKey(tableName)
                ResultSetSqliteColumn(
                  metaData.getColumnName(i),
                  SqliteAffinity.fromJDBCType(JDBCType.valueOf(metaData.getColumnType(i))),
                  metaData.isNullable(i) == 1,
                  keyColumnsNames.contains(columnName)
                )
              }
            }
          }
        }
        .cancelOnDispose(this)

  abstract override val totalRowCount: ListenableFuture<Int>
  abstract override fun getRowBatch(
    rowOffset: Int,
    rowBatchSize: Int
  ): ListenableFuture<List<SqliteRow>>

  protected fun getRowCount(
    sqliteStatement: SqliteStatement,
    handleResponse: (ResultSet) -> Int
  ): ListenableFuture<Int> {
    return taskExecutor
      .executeAsync {
        check(!Disposer.isDisposed(this)) { "ResultSet has already been closed." }
        check(!connection.isClosed) { "The connection has been closed." }

        connection.resolvePreparedStatement(sqliteStatement).use { preparedStatement ->
          preparedStatement.executeQuery().use { handleResponse(it) }
        }
      }
      .cancelOnDispose(this)
  }

  protected fun getRowBatch(
    sqliteStatement: SqliteStatement,
    handleResponse: (ResultSet, List<ResultSetSqliteColumn>) -> List<SqliteRow>
  ): ListenableFuture<List<SqliteRow>> {
    return columns
      .transform(taskExecutor) { columns ->
        check(!Disposer.isDisposed(this)) { "ResultSet has already been closed." }
        check(!connection.isClosed) { "The connection has been closed." }

        connection.resolvePreparedStatement(sqliteStatement).use { preparedStatement ->
          preparedStatement.executeQuery().use { handleResponse(it, columns) }
        }
      }
      .cancelOnDispose(this)
  }

  @WorkerThread
  protected fun createCurrentRow(
    resultSet: ResultSet,
    columns: List<ResultSetSqliteColumn>
  ): SqliteRow {
    return SqliteRow(
      columns.mapIndexed { i, column ->
        SqliteColumnValue(column.name, SqliteValue.fromAny(resultSet.getObject(i + 1)))
      }
    )
  }

  override fun dispose() {}
}
