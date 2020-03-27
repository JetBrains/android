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
import com.android.tools.idea.concurrency.executeAsync
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.databaseConnection.checkOffsetAndSize
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteValue
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.util.Disposer
import java.sql.Connection
import java.sql.JDBCType
import java.sql.ResultSet

class JdbcSqliteResultSet(
  private val service: JdbcDatabaseConnection,
  private val connection: Connection,
  private val sqliteStatement: SqliteStatement
) : SqliteResultSet {

  override val columns get() = service.sequentialTaskExecutor.executeAsync {
    connection.resolvePreparedStatement(sqliteStatement).use { preparedStatement ->
      preparedStatement.executeQuery().use {
        val metaData = it.metaData
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
    }
  }

  override val totalRowCount get() = service.sequentialTaskExecutor.executeAsync {
    check(!Disposer.isDisposed(this)) { "ResultSet has already been closed." }
    check(!connection.isClosed) { "The connection has been closed." }

    val newStatement = sqliteStatement.toRowCountStatement()
    connection.resolvePreparedStatement(newStatement).use { preparedStatement ->
      preparedStatement.executeQuery().use {
        it.next()
        val count = it.getInt(1)

        it.close()
        preparedStatement.close()

        count
      }
    }
  }

  override fun getRowBatch(rowOffset: Int, rowBatchSize: Int): ListenableFuture<List<SqliteRow>> {
    checkOffsetAndSize(rowOffset, rowBatchSize)

    return columns.transform(service.sequentialTaskExecutor) { columns ->
      check(!Disposer.isDisposed(this)) { "ResultSet has already been closed." }
      check(!connection.isClosed) { "The connection has been closed." }

      val newStatement = sqliteStatement.toSelectLimitOffset(rowOffset, rowBatchSize)
      connection.resolvePreparedStatement(newStatement).use { preparedStatement ->
        preparedStatement.executeQuery().use {
          val rows = ArrayList<SqliteRow>()
          while (it.next()) {
            rows.add(createCurrentRow(it, columns))
          }

          preparedStatement.close()

          rows
        }
      }
    }
  }

  @WorkerThread
  private fun createCurrentRow(resultSet: ResultSet, columns: List<SqliteColumn>): SqliteRow {
    return SqliteRow(columns.mapIndexed { i, column -> SqliteColumnValue(column.name, SqliteValue.fromAny(resultSet.getObject(i + 1))) })
  }

  override fun dispose() {
  }
}