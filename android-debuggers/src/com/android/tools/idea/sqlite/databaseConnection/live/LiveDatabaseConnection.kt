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
package com.android.tools.idea.sqlite.databaseConnection.live

import com.android.tools.idea.appinspection.api.AppInspectorClient
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.sql.protocol.SqliteInspection
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.sql.JDBCType
import java.util.concurrent.Executor

/**
 * Implementation of [DatabaseConnection] based on the AppInspection pipeline.
 */
class LiveDatabaseConnection(
  private val messenger: AppInspectorClient.CommandMessenger,
  private val id: Int,
  executor: Executor
) : DatabaseConnection {
  private val taskExecutor = FutureCallbackExecutor.wrap(executor)

  override fun close(): ListenableFuture<Unit> {
    // TODO(blocked) not yet implemented in on-device inspector
    return Futures.immediateFuture(Unit)
  }

  override fun readSchema(): ListenableFuture<SqliteSchema> {
    val commands = SqliteInspection.Commands.newBuilder()
      .setGetSchema(SqliteInspection.GetSchemaCommand.newBuilder().setId(id))
      .build()
    val responseFuture = messenger.sendRawCommand(commands.toByteArray())

    return taskExecutor.transform(responseFuture) {
      val schemaResponse = SqliteInspection.SchemaResponse.parseFrom(it)
      schemaResponse.schema.toSqliteSchema()
    }
  }

  override fun execute(sqliteStatement: SqliteStatement): ListenableFuture<SqliteResultSet?> {
    // TODO(b/144336989) pass SqliteStatement object instead of String.
    val queryBuilder = SqliteInspection.QueryCommand.newBuilder().setQuery(sqliteStatement.assignValuesToParameters()).setDatabaseId(id)
    // TODO: next CL. Figure out how to do this
    //hints.forEach { queryBuilder.addAffectedTables(it) }
    val command = SqliteInspection.Commands.newBuilder().setQuery(queryBuilder).build()
    val responseFuture = messenger.sendRawCommand(command.toByteArray())

    return taskExecutor.transform(responseFuture) {
      val cursor = SqliteInspection.Cursor.parseFrom(it)
      if (cursor.rowsList.size == 0) {
        null
      } else {
        getLiveSqliteResultSet(cursor)
      }
    }
  }

  private fun getLiveSqliteResultSet(cursor: SqliteInspection.Cursor): ImmediateSqliteResultSet {
    val rows = cursor.rowsList.map {
      val row = it.valuesList.map { cellValue -> cellValue.toSqliteColumn() }
      SqliteRow(row)
    }
    return ImmediateSqliteResultSet(rows)
  }

  private fun SqliteInspection.Schema.toSqliteSchema(): SqliteSchema {
    val tables = tablesList.map { table ->
      val sqliteColumns = table.columnsList.map { it.toSqliteColumn() }
      // TODO(blocked): add support for rowIdName
      SqliteTable(table.name, sqliteColumns, null, false)
    }
    return SqliteSchema(tables)
  }

  // TODO(blocked): properly handle supported data types. See https://www.sqlite.org/datatype3.html
  private fun SqliteInspection.Column.toSqliteColumn(): SqliteColumn {
    val type = if (type == "TEXT") {
      JDBCType.VARCHAR
    }
    else {
      try {
        JDBCType.valueOf(type)
      }
      catch (e: Exception) {
        JDBCType.OTHER
      }
    }
    return SqliteColumn(name, type, false)
  }

  private fun SqliteInspection.CellValue.toSqliteColumn(): SqliteColumnValue {
    // TODO(blocked): add support for primary keys
    return when (unionCase) {
      SqliteInspection.CellValue.UnionCase.STRING_VALUE ->
        SqliteColumnValue(SqliteColumn(columnName, JDBCType.VARCHAR, false), stringValue)
      SqliteInspection.CellValue.UnionCase.FLOAT_VALUE ->
        SqliteColumnValue(SqliteColumn(columnName, JDBCType.FLOAT, false), floatValue)
      SqliteInspection.CellValue.UnionCase.BLOB_VALUE ->
        SqliteColumnValue(SqliteColumn(columnName, JDBCType.BLOB, false), blobValue)
      SqliteInspection.CellValue.UnionCase.INT_VALUE ->
        SqliteColumnValue(SqliteColumn(columnName, JDBCType.INTEGER, false), intValue)
      else -> SqliteColumnValue(SqliteColumn(columnName, JDBCType.NULL, false), "null")
    }
  }
}