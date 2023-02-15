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

import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.SqliteValue
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Takes a [SqliteStatement] and returns a [PreparedStatement] by assigning values to parameters in
 * the statement.
 */
fun Connection.resolvePreparedStatement(sqliteStatement: SqliteStatement): PreparedStatement {
  val preparedStatement = prepareStatement(sqliteStatement.sqliteStatementText)
  sqliteStatement.parametersValues.forEachIndexed { index, value ->
    when (value) {
      is SqliteValue.StringValue -> preparedStatement.setString(index + 1, value.value)
      is SqliteValue.NullValue -> preparedStatement.setNull(index + 1, Types.VARCHAR)
    }
  }
  return preparedStatement
}

fun <T> ResultSet.map(transform: ResultSet.() -> T): Sequence<T> {
  val resultSet = this
  return sequence {
    while (resultSet.next()) {
      yield(resultSet.transform())
    }
  }
}

fun Connection.getColumnNamesInPrimaryKey(tableName: String): List<String> {
  if (tableName.isEmpty()) return emptyList()

  val keySet = metaData.getPrimaryKeys(null, null, tableName)
  return keySet.map { getString("COLUMN_NAME") }.toList()
}

fun selectAllAndRowIdFromTable(table: SqliteTable): String {
  // We need to set an alias for rowid because even if the query is something like "SELECT *,
  // _rowid_ FROM table",
  // in the result set the column corresponding to rowid is always called "rowid".
  // But in [SqliteTable] we save the name of the rowid column as "rowid", "_rowid_" or "oid"
  val columnsToSelect =
    table.rowIdName?.let { rowIdName -> "*, ${rowIdName.stringName} as ${rowIdName.stringName}" }
      ?: "*"
  return "SELECT $columnsToSelect FROM ${AndroidSqlLexer.getValidName(table.name)}"
}

suspend fun openJdbcDatabaseConnection(
  parentDisposable: Disposable,
  virtualFile: VirtualFile,
  workerExecutor: Executor,
  workerDispatcher: CoroutineDispatcher
): JdbcDatabaseConnection {
  return withContext(workerDispatcher) {
    try {
      val url = "jdbc:sqlite:${virtualFile.path}"
      val connection = DriverManager.getConnection(url)
      JdbcDatabaseConnection(parentDisposable, connection, virtualFile, workerExecutor)
    } catch (e: Exception) {
      throw RuntimeException("Error while opening Sqlite database file '${virtualFile.path}'", e)
    }
  }
}
