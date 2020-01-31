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

import androidx.sqlite.inspection.SqliteInspectorProtocol
import androidx.sqlite.inspection.SqliteInspectorProtocol.CellValue
import androidx.sqlite.inspection.SqliteInspectorProtocol.Column
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command
import androidx.sqlite.inspection.SqliteInspectorProtocol.GetSchemaCommand
import androidx.sqlite.inspection.SqliteInspectorProtocol.Row
import androidx.sqlite.inspection.SqliteInspectorProtocol.Table
import com.android.tools.idea.appinspection.api.AppInspectorClient
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.getRowIdName
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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
    val commands = Command.newBuilder()
      .setGetSchema(GetSchemaCommand.newBuilder().setDatabaseId(id))
      .build()
    val responseFuture = messenger.sendRawCommand(commands.toByteArray())

    return taskExecutor.transform(responseFuture) {
      val schemaResponse = SqliteInspectorProtocol.Response.parseFrom(it)
      schemaResponse.getSchema.tablesList.toSqliteSchema()
    }
  }

  override fun execute(sqliteStatement: SqliteStatement): ListenableFuture<SqliteResultSet> {
    // TODO(blocked b/144336989) pass SqliteStatement object instead of String.
    val queryBuilder = SqliteInspectorProtocol.QueryCommand.newBuilder().setQuery(sqliteStatement.assignValuesToParameters()).setDatabaseId(id)
    // TODO(blocked) decide how the on-device inspector is going to notify the client about changes in the tables.
    //hints.forEach { queryBuilder.addAffectedTables(it) }
    val command = Command.newBuilder().setQuery(queryBuilder).build()
    val responseFuture = messenger.sendRawCommand(command.toByteArray())

    return taskExecutor.transform(responseFuture) {
      val queryResponse = SqliteInspectorProtocol.Response.parseFrom(it).query
      getLiveSqliteResultSet(queryResponse.rowsList)
    }
  }

  private fun getLiveSqliteResultSet(rows: List<Row>): ImmediateSqliteResultSet {
    val rows = rows.map {
      val row = it.valuesList.map { cellValue -> cellValue.toSqliteColumn() }
      SqliteRow(row)
    }
    return ImmediateSqliteResultSet(rows)
  }

  private fun List<Table>.toSqliteSchema(): SqliteSchema {
    val tables = map { table ->
      val columns = table.columnsList.map { it.toSqliteColumn() }
      val rowIdName = getRowIdName(columns)
      SqliteTable(table.name, columns, rowIdName, false)
    }
    return SqliteSchema(tables)
  }

  private fun Column.toSqliteColumn(): SqliteColumn {
    // TODO(blocked): add support for primary keys
    // TODO(blocked): add support for NOT NULL
    return SqliteColumn(name, SqliteAffinity.fromTypename(type), false, false)
  }

  private fun CellValue.toSqliteColumn(): SqliteColumnValue {
    // TODO(blocked): add support for primary keys
    // TODO(blocked): add support for NOT NULL
    // TODO(blocked): we need to get affinity info from the on device inspector.
    return when (valueCase) {
      CellValue.ValueCase.STRING_VALUE ->
        SqliteColumnValue(SqliteColumn(columnName, SqliteAffinity.TEXT, false, false), stringValue)
      CellValue.ValueCase.FLOAT_VALUE ->
        SqliteColumnValue(SqliteColumn(columnName, SqliteAffinity.TEXT, false, false), floatValue)
      CellValue.ValueCase.BLOB_VALUE ->
        SqliteColumnValue(SqliteColumn(columnName, SqliteAffinity.TEXT, false, false), blobValue)
      CellValue.ValueCase.INT_VALUE ->
        SqliteColumnValue(SqliteColumn(columnName, SqliteAffinity.TEXT, false, false), intValue)
      else -> SqliteColumnValue(SqliteColumn(columnName, SqliteAffinity.TEXT, false, false), "null")
    }
  }
}