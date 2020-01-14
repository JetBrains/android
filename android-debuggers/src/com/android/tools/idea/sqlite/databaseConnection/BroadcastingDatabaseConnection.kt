/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.sqlite.databaseConnection

import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.Executor

/**
 * Decorator for [DatabaseConnection]s, each time a SQLite statement is executed the decorator broadcasts a message on the message bus.
 *
 * The message is sent to the message bus as soon as the future completes.
 * @param databaseConnection [DatabaseConnection] to which each method is delegated.
 */
class BroadcastingDatabaseConnection(
  private val databaseConnection: DatabaseConnection,
  executor: Executor
) : DatabaseConnection by databaseConnection {
  private val taskExecutor = FutureCallbackExecutor.wrap(executor)

  override fun execute(sqliteStatement: SqliteStatement): ListenableFuture<SqliteResultSet?> {
    val settableFuture = SettableFuture.create<SqliteResultSet?>()
    val executeFuture = databaseConnection.execute(sqliteStatement)

    taskExecutor.addCallback(executeFuture, object : FutureCallback<SqliteResultSet?> {
      val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(DatabaseConnection.TOPIC)
      override fun onSuccess(result: SqliteResultSet?) {
        publisher.onSqliteStatementExecutionSuccess(sqliteStatement)
        settableFuture.set(result)
      }

      override fun onFailure(t: Throwable) {
        publisher.onSqliteStatementExecutionFailed(sqliteStatement)
        settableFuture.setException(t)
      }
    })

    return settableFuture
  }
}