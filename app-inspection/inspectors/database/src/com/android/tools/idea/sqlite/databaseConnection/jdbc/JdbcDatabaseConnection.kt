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
package com.android.tools.idea.sqlite.databaseConnection.jdbc

import com.android.tools.idea.concurrency.executeAsync
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.getRowIdName
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.SequentialTaskExecutor
import java.sql.Connection
import java.util.concurrent.Executor

/**
 * Implementation of [DatabaseConnection] for a local Sqlite file using the JDBC driver.
 *
 * This class has a [SequentialTaskExecutor] with one thread, that should be used to make sure that
 * operations are executed sequentially, to avoid concurrency issues with the JDBC objects.
 */
class JdbcDatabaseConnection(
  parentDisposable: Disposable,
  private val connection: Connection,
  private val sqliteFile: VirtualFile,
  pooledExecutor: Executor
) : DatabaseConnection {
  companion object {
    private val logger: Logger = Logger.getInstance(JdbcDatabaseConnection::class.java)
  }

  init {
    Disposer.register(parentDisposable, this)
  }

  private val sequentialTaskExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Sqlite JDBC service", pooledExecutor)

  override fun close(): ListenableFuture<Unit> = sequentialTaskExecutor.executeAsync {
    connection.close()
    logger.info("Successfully closed database: ${sqliteFile.path}")
  }

  override fun readSchema(): ListenableFuture<SqliteSchema> = sequentialTaskExecutor.executeAsync {
    val tables = connection.metaData.getTables(null, null, null, null)
    val sqliteTables = mutableListOf<SqliteTable>()
    while (tables.next()) {
      val columns = readColumnDefinitions(connection, tables.getString("TABLE_NAME"))
      val rowIdName = getRowIdName(columns)
      sqliteTables.add(
        SqliteTable(
          tables.getString("TABLE_NAME"),
          columns,
          rowIdName,
          isView = tables.getString("TABLE_TYPE") == "VIEW"
        )
      )
    }

    SqliteSchema(sqliteTables).apply { logger.info("Successfully read database schema: ${sqliteFile.path}") }
  }

  override fun query(sqliteStatement: SqliteStatement): ListenableFuture<SqliteResultSet> {
    val resultSet = when (sqliteStatement.statementType) {
      SqliteStatementType.SELECT -> PagedJdbcSqliteResultSet(this.sequentialTaskExecutor, connection, sqliteStatement)
      SqliteStatementType.EXPLAIN,
      SqliteStatementType.PRAGMA_QUERY -> LazyJdbcSqliteResultSet(this.sequentialTaskExecutor, connection, sqliteStatement)
      else -> throw IllegalArgumentException(
        "SqliteStatement must be of type SELECT, EXPLAIN or PRAGMA, but is ${sqliteStatement.statementType}"
      )
    }
    Disposer.register(this, resultSet)
    return Futures.immediateFuture(resultSet)
  }

  override fun execute(sqliteStatement: SqliteStatement): ListenableFuture<Unit> {
    return sequentialTaskExecutor.executeAsync {
      connection.resolvePreparedStatement(sqliteStatement).use { preparedStatement ->
        preparedStatement.executeUpdate().also {
          logger.info("SQL statement \"${sqliteStatement.sqliteStatementText}\" executed with success.")
        }
      }
      Unit
    }
  }

  private fun readColumnDefinitions(connection: Connection, tableName: String): List<SqliteColumn> {
    connection.createStatement().use { statement ->
      statement.executeQuery("PRAGMA table_info(${AndroidSqlLexer.getValidName(tableName)})").use {
        return it.map {
          val columnName = getString(2)
          val columnType = getString(3)
          val colNotNull = getString(4)
          val colPk = getString(6)

          SqliteColumn(
            columnName,
            SqliteAffinity.fromTypename(columnType),
            colNotNull == "0",
            // The number in table_info for primary key is an integer that corresponds
            // to the position of the column in the primary key constraint.
            // Or 0 if the column is not in the primary key.
            colPk.toInt() > 0
          )
        }.toList()
      }
    }
  }
}
