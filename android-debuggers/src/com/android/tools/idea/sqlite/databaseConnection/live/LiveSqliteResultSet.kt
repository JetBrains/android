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
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.databaseConnection.checkOffsetAndSize
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.util.Disposer
import java.util.concurrent.Executor

/**
 * [SqliteResultSet] for live connections.
 *
 * @param _columns The list of columns for this result set.
 * @param sqliteStatement The original [SqliteStatement] this result set is for.
 * @param messenger Used to send messages to an on-device inspector.
 * @param executor Used to execute IO operation on a background thread.
 */
class LiveSqliteResultSet(
  private val sqliteStatement: SqliteStatement,
  private val messenger: AppInspectorClient.CommandMessenger,
  private val connectionId: Int,
  executor: Executor
) : SqliteResultSet {
  private val taskExecutor = FutureCallbackExecutor.wrap(executor)

  override val columns: ListenableFuture<List<SqliteColumn>> get() {
    val queryCommand = buildQueryCommand(sqliteStatement, connectionId)
    val responseFuture = messenger.sendRawCommand(queryCommand.toByteArray())

    return taskExecutor.transform(responseFuture) {
      val response = SqliteInspectorProtocol.Response.parseFrom(it)

      if (response.hasErrorOccurred()) {
        handleError(response.errorOccurred.content)
      }

      return@transform response.query.columnNamesList.map { columnName ->
        // TODO(b/150937705): add support for primary keys
        // TODO(b/150937705): add support for NOT NULL
        // TODO(b/150937705): we need to get affinity info from the on device inspector.
        SqliteColumn(columnName, SqliteAffinity.TEXT, false, false)
      }
    }
  }

  override val totalRowCount: ListenableFuture<Int> get() {
    val queryCommand = buildQueryCommand(sqliteStatement.toRowCountStatement(), connectionId)
    val responseFuture = messenger.sendRawCommand(queryCommand.toByteArray())

    return taskExecutor.transform(responseFuture) {
      check(!Disposer.isDisposed(this)) { "ResultSet has already been disposed." }

      val response = SqliteInspectorProtocol.Response.parseFrom(it)

      if (response.hasErrorOccurred()) {
        handleError(response.errorOccurred.content)
      }

      response.query.rowsList.firstOrNull()?.valuesList?.firstOrNull()?.intValue ?: 0
    }
  }

  override fun getRowBatch(rowOffset: Int, rowBatchSize: Int): ListenableFuture<List<SqliteRow>> {
    checkOffsetAndSize(rowOffset, rowBatchSize)

    val queryCommand = buildQueryCommand(sqliteStatement.toSelectLimitOffset(rowOffset, rowBatchSize), connectionId)
    val responseFuture = messenger.sendRawCommand(queryCommand.toByteArray())

    return taskExecutor.transform(responseFuture) { byteArray ->
      check(!Disposer.isDisposed(this)) { "ResultSet has already been disposed." }

      val response = SqliteInspectorProtocol.Response.parseFrom(byteArray)

      if (response.hasErrorOccurred()) {
        handleError(response.errorOccurred.content)
      }

      val columnNames = response.query.columnNamesList
      val rows = response.query.rowsList.map {
        val sqliteColumnValues = it.valuesList.mapIndexed { index, cellValue -> cellValue.toSqliteColumnValue(columnNames[index]) }
        SqliteRow(sqliteColumnValues)
      }

      rows
    }
  }

  override fun dispose() {
  }
}