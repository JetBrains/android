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
package com.android.tools.idea.sqlite.databaseConnection.live

import androidx.sqlite.inspection.SqliteInspectorProtocol
import com.android.tools.idea.concurrency.cancelOnDispose
import com.android.tools.idea.sqlite.DatabaseInspectorMessenger
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

/**
 * [SqliteResultSet] for live connections.
 *
 * @param sqliteStatement The original [SqliteStatement] this result set is for.
 * @param messenger Used to send messages to an on-device inspector.
 * @param taskExecutor Used to execute IO operation on a background thread.
 */
abstract class LiveSqliteResultSet(
  private val sqliteStatement: SqliteStatement,
  private val messenger: DatabaseInspectorMessenger,
  private val connectionId: Int,
  private val taskExecutor: Executor
) : SqliteResultSet {

  /**
   * @param responseSizeByteLimitHint
   * - best effort limit of a single response expressed in bytes
   */
  protected fun sendQueryCommand(
    sqliteStatement: SqliteStatement,
    responseSizeByteLimitHint: Long? = null
  ): ListenableFuture<SqliteInspectorProtocol.Response> {
    val queryCommand = buildQueryCommand(sqliteStatement, connectionId, responseSizeByteLimitHint)
    return messenger.sendCommandAsync(queryCommand).cancelOnDispose(this)
  }

  /**
   * @param rowBatchSize
   * - limit of a batch size expressed as the number of rows cap
   */
  final override fun getRowBatch(
    rowOffset: Int,
    rowBatchSize: Int
  ): ListenableFuture<List<SqliteRow>> = getRowBatch(rowOffset, rowBatchSize, null)

  /**
   * @param rowBatchSize
   * - limit of a batch size expressed as the number of rows cap
   * @param responseSizeByteLimitHint
   * - best effort limit of a batch size expressed in bytes - only introduced in
   * [LiveSqliteResultSet] (as opposed to [SqliteResultSet]) as other implementations use the local
   * file system, where we don't need to be so careful with the response size. Memory on the device
   * (subclasses of [LiveSqliteResultSet]) is much more restricted.
   */
  abstract fun getRowBatch(
    rowOffset: Int,
    rowBatchSize: Int,
    responseSizeByteLimitHint: Long? = null
  ): ListenableFuture<List<SqliteRow>>

  override fun dispose() {}
}
