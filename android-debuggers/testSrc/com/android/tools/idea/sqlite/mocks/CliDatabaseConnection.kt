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
package com.android.tools.idea.sqlite.mocks

import com.android.tools.idea.sqlite.cli.SqliteCliArgs
import com.android.tools.idea.sqlite.cli.SqliteCliClient
import com.android.tools.idea.sqlite.cli.SqliteCliResponse
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.ResultSetSqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteValue
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.guava.asListenableFuture
import java.nio.file.Path
import java.util.concurrent.Executor

/**
 * Processes requests using sqlite3 CLI - targeted as a testing replacement a live device connection.
 * @param columnSeparator - column separator used for dealing with CLI output; use any value that is not present in the data.
 **/
class CliDatabaseConnection(private val databasePath: Path,
                            private val client: SqliteCliClient,
                            private val columnSeparator: Char,
                            executor: Executor) : DatabaseConnection {
  private val coroutineDispatcher = executor.asCoroutineDispatcher()

  override fun execute(sqliteStatement: SqliteStatement) = CoroutineScope(coroutineDispatcher).async<Unit> {
    client.runSqliteCliCommand(
      SqliteCliArgs.builder()
        .database(databasePath)
        .raw("${sqliteStatement.checkNoSeparatorClash().sqliteStatementWithInlineParameters};")
        .build())
      .checkSuccess()
  }.asListenableFuture()

  override fun query(sqliteStatement: SqliteStatement): ListenableFuture<SqliteResultSet> = CoroutineScope(coroutineDispatcher).async {
    client.runSqliteCliCommand(
      SqliteCliArgs.builder()
        .database(databasePath)
        .headersOn()
        .separator(columnSeparator)
        .raw("${sqliteStatement.checkNoSeparatorClash().sqliteStatementWithInlineParameters};")
        .build())
      .checkSuccess()
      .toSqliteResultSet()
  }.asListenableFuture()

  override fun close(): ListenableFuture<Unit> = Futures.immediateFuture(null)

  override fun readSchema() = throw IllegalStateException("Not implemented")

  private fun SqliteCliResponse.toSqliteResultSet(): SqliteResultSet = this.checkSuccess().let {
    val lines: List<String> = stdOutput.split(System.lineSeparator())
    val columnNames: List<String> = lines.take(1).first().split(columnSeparator)
    val dataLines: List<String> = lines.drop(1)

    object : SqliteResultSet {
      override val columns: ListenableFuture<List<ResultSetSqliteColumn>>
        get() = Futures.immediateFuture(let { columnNames.map { ResultSetSqliteColumn(it) } })

      override val totalRowCount: ListenableFuture<Int> get() = Futures.immediateFuture(dataLines.size)

      override fun getRowBatch(rowOffset: Int, rowBatchSize: Int): ListenableFuture<List<SqliteRow>> = Futures.immediateFuture(let {
        dataLines.drop(rowOffset).take(rowBatchSize).map { row ->
          val rawCells: List<String> = row.split(columnSeparator)
          val cells = rawCells.mapIndexed { ix, cell -> SqliteColumnValue(columnNames[ix], SqliteValue.fromAny(cell)) }
          SqliteRow(cells)
        }
      })

      override fun dispose() {}
    }
  }

  private fun SqliteStatement.checkNoSeparatorClash() = apply {
    if (this.sqliteStatementWithInlineParameters.contains(columnSeparator))
      throw IllegalArgumentException("Potential clash between the data and a column separator." +
                                     " Consider using a different column separator or changing your test data.")
  }

  private fun SqliteCliResponse.checkSuccess() = apply {
    if (exitCode != 0) throw IllegalStateException("Issue while executing query. Error: $errOutput")
  }
}