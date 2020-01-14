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
package com.android.tools.idea.sqlite.databaseConnection

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable
import com.intellij.util.messages.Topic

/**
 * Abstraction over operations allowed on a single underlying sqlite database.
 *
 * All operations are asynchronous, where completion is communicated through [ListenableFuture] return values.
 */
interface DatabaseConnection : Disposable {
  companion object {
    @JvmField
    val TOPIC = Topic<DatabaseConnectionListener>("DatabaseConnectionListenerTopic", DatabaseConnectionListener::class.java)
  }

  fun close(): ListenableFuture<Unit>
  fun readSchema(): ListenableFuture<SqliteSchema>

  /**
   * Executes a SQLite statement on the database.
   *
   * @see java.sql.PreparedStatement.execute
   */
  fun execute(sqliteStatement: SqliteStatement): ListenableFuture<SqliteResultSet?>

  override fun dispose() {
    close().get()
  }
}

@WorkerThread
interface DatabaseConnectionListener {
  /** Called when the execution of a [SqliteStatement] has been successful **/
  fun onSqliteStatementExecutionSuccess(sqliteStatement: SqliteStatement)

  /** Called when the execution of a [SqliteStatement] has failed **/
  fun onSqliteStatementExecutionFailed(sqliteStatement: SqliteStatement)
}
