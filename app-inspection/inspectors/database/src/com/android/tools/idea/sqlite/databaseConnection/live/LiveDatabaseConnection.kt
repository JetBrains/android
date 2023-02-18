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

import androidx.sqlite.inspection.SqliteInspectorProtocol.Command
import androidx.sqlite.inspection.SqliteInspectorProtocol.GetSchemaCommand
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.sqlite.DatabaseInspectorMessenger
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.Executor

/** Implementation of [DatabaseConnection] based on the AppInspection pipeline. */
class LiveDatabaseConnection(
  parentDisposable: Disposable,
  private val messenger: DatabaseInspectorMessenger,
  private val id: Int,
  private val taskExecutor: Executor
) : DatabaseConnection {

  init {
    Disposer.register(parentDisposable, this)
  }

  override fun close(): ListenableFuture<Unit> {
    // TODO(blocked) not yet implemented in on-device inspector
    return Futures.immediateFuture(Unit)
  }

  override fun readSchema(): ListenableFuture<SqliteSchema> {
    val commands =
      Command.newBuilder().setGetSchema(GetSchemaCommand.newBuilder().setDatabaseId(id)).build()
    val responseFuture = messenger.sendCommandAsync(commands)

    return responseFuture.transform(taskExecutor) { response ->
      response.getSchema.tablesList.toSqliteSchema()
    }
  }

  override fun query(sqliteStatement: SqliteStatement): ListenableFuture<SqliteResultSet> {
    val resultSet =
      when (sqliteStatement.statementType) {
        SqliteStatementType.SELECT ->
          PagedLiveSqliteResultSet(sqliteStatement, messenger, id, taskExecutor)
        SqliteStatementType.EXPLAIN, SqliteStatementType.PRAGMA_QUERY ->
          LazyLiveSqliteResultSet(sqliteStatement, messenger, id, taskExecutor)
        else ->
          throw IllegalArgumentException(
            "SqliteStatement must be of type SELECT, EXPLAIN or PRAGMA, but is ${sqliteStatement.statementType}"
          )
      }
    Disposer.register(this, resultSet)
    return Futures.immediateFuture(resultSet)
  }

  override fun execute(sqliteStatement: SqliteStatement): ListenableFuture<Unit> {
    val queryCommand = buildQueryCommand(sqliteStatement, id)
    val responseFuture = messenger.sendCommandAsync(queryCommand)
    return responseFuture.transform(taskExecutor) { Unit }
  }
}
