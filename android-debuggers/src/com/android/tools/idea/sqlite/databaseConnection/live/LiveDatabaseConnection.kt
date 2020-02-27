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
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command
import androidx.sqlite.inspection.SqliteInspectorProtocol.GetSchemaCommand
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.EmptySqliteResultSet
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.util.Disposer
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
    val queryCommand = buildQueryCommand(sqliteStatement, id)
    val responseFuture = messenger.sendRawCommand(queryCommand.toByteArray())

    return taskExecutor.transform(responseFuture) {
      val queryResponse = SqliteInspectorProtocol.Response.parseFrom(it).query

      val sqliteColumns = queryResponse.columnNamesList.map { columnName ->
        // TODO(blocked): add support for primary keys
        // TODO(blocked): add support for NOT NULL
        // TODO(blocked): we need to get affinity info from the on device inspector.
        SqliteColumn(columnName, SqliteAffinity.TEXT, false, false)
      }

      val resultSet = if (sqliteColumns.isNotEmpty()) {
        LiveSqliteResultSet(sqliteColumns, sqliteStatement, messenger, id, taskExecutor)
      }
      else {
        EmptySqliteResultSet()
      }
      Disposer.register(this, resultSet)

      return@transform resultSet
    }
  }
}