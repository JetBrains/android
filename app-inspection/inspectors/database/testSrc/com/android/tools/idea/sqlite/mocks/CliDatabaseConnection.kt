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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.sqlite.cli.SqliteCliArgs
import com.android.tools.idea.sqlite.cli.SqliteCliClient
import com.android.tools.idea.sqlite.cli.SqliteCliResponse
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.databaseConnection.live.LiveSqliteResultSet
import com.android.tools.idea.sqlite.model.ResultSetSqliteColumn
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.SqliteValue
import com.android.tools.idea.sqlite.model.getRowIdName
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.nio.file.Path
import java.util.concurrent.Executor
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.guava.asListenableFuture

/**
 * From:
 * [https://cs.android.com/androidx/platform/frameworks/support/+/androidx-master-dev:sqlite/sqlite-inspection/src/main/java/androidx/sqlite/inspection/SqliteInspector.java;l=135;drc=e06399865fcdca1975f6dcc667cc5f9477e67998]
 *
 * Sample output:
 * ```
 * type|tableName|columnName|columnType|notnull|pk|unique
 * table|t1|c1|int|0|0|0
 * table|t1|c2|int|0|0|0
 * table|t2|c11|int|0|0|0
 * table|t2|c22|int|0|0|0
 * ```
 */
const val schemaQuery =
  """
    select
      m.type as type,
      m.name as tableName,
      ti.name as columnName,
      ti.type as columnType,
      [notnull],
      pk,
      ifNull([unique], 0) as [unique]
    from sqlite_master AS m, pragma_table_info(m.name) as ti
    left outer join
      (
        select tableName, name as columnName, ti.[unique]
        from
          (
            select m.name as tableName, il.name as indexName, il.[unique]
            from
              sqlite_master AS m,
              pragma_index_list(m.name) AS il,
              pragma_index_info(il.name) as ii
            where il.[unique] = 1
            group by il.name
            having count(*) = 1  -- countOfColumnsInIndex=1
          )
            as ti,  -- tableName|indexName|unique : unique=1 and countOfColumnsInIndex=1
          pragma_index_info(ti.indexName)
      )
        as tci  -- tableName|columnName|unique : unique=1 and countOfColumnsInIndex=1
      on tci.tableName = m.name and tci.columnName = ti.name
    where m.type in ('table', 'view')
    order by type, tableName, ti.cid  -- cid = columnId
    """

/**
 * Processes requests using sqlite3 CLI - targeted as a testing replacement a live device
 * connection.
 *
 * @param columnSeparator
 * - column separator used for dealing with CLI output; use any value that is not present in the
 *   data.
 */
class CliDatabaseConnection(
  private val databasePath: Path,
  private val client: SqliteCliClient,
  private val columnSeparator: Char,
  executor: Executor
) : DatabaseConnection {
  private val coroutineDispatcher = executor.asCoroutineDispatcher()

  override fun execute(sqliteStatement: SqliteStatement) =
    CoroutineScope(coroutineDispatcher)
      .async<Unit> {
        client
          .runSqliteCliCommand(
            SqliteCliArgs.builder()
              .database(databasePath)
              .raw(
                "${sqliteStatement.checkNoSeparatorClash().sqliteStatementWithInlineParameters};"
              )
              .build()
          )
          .checkSuccess()
      }
      .asListenableFuture()

  override fun query(sqliteStatement: SqliteStatement): ListenableFuture<SqliteResultSet> =
    CoroutineScope(coroutineDispatcher)
      .async {
        client
          .runSqliteCliCommand(
            SqliteCliArgs.builder()
              .database(databasePath)
              .headersOn()
              .separator(columnSeparator)
              .raw(
                "${sqliteStatement.checkNoSeparatorClash().sqliteStatementWithInlineParameters};"
              )
              .build()
          )
          .checkSuccess()
          .toSqliteResultSet()
      }
      .asListenableFuture()

  override fun close(): ListenableFuture<Unit> = Futures.immediateFuture(null)

  override fun readSchema(): ListenableFuture<SqliteSchema> =
    CoroutineScope(coroutineDispatcher)
      .async {
        client
          .runSqliteCliCommand(
            SqliteCliArgs.builder()
              .database(databasePath)
              .headersOn()
              .separator(columnSeparator)
              .raw("$schemaQuery;")
              .build()
          )
          .checkSuccess()
          .toSqliteSchema()
      }
      .asListenableFuture()

  private fun SqliteCliResponse.toSqliteResultSet(): SqliteResultSet =
    toRawCells().let { rawCells ->
      object : LiveSqliteResultSet(mock(), mock(), -1, mock()) {
        override val columns: ListenableFuture<List<ResultSetSqliteColumn>>
          get() = Futures.immediateFuture(let { rawCells.header.map { ResultSetSqliteColumn(it) } })

        override val totalRowCount: ListenableFuture<Int>
          get() = Futures.immediateFuture(rawCells.dataRows.size)

        override fun getRowBatch(
          rowOffset: Int,
          rowBatchSize: Int,
          responseSizeByteLimitHint: Long?
        ): ListenableFuture<List<SqliteRow>> =
          Futures.immediateFuture(
            let {
              // simulate responseSizeByteLimitHint by hard-coding `2` rows per response - good
              // enough for testing purposes
              val batchSize = if (responseSizeByteLimitHint != null) 2 else rowBatchSize
              // min(batchSize, rowBatchSize) ensures that both size [bytes] and row count limits
              // are enforced
              rawCells.dataRows.drop(rowOffset).take(min(batchSize, rowBatchSize)).map { row ->
                val cells =
                  row.mapIndexed { ix, cell ->
                    SqliteColumnValue(rawCells.header[ix], SqliteValue.fromAny(cell))
                  }
                SqliteRow(cells)
              }
            }
          )

        override fun dispose() {}
      }
    }

  private fun SqliteCliResponse.toSqliteSchema(): SqliteSchema =
    toRawCells().let { rawCells ->
      /** @see [schemaQuery] documentation for column list */
      val columnMap = rawCells.header.mapIndexed { ix, name -> name to ix }.toMap()
      fun List<String>.getCell(columnName: String): String =
        this[columnMap.getValue(columnName)] // this is a bit slow, but OK for tests

      val tables =
        rawCells.dataRows
          .groupBy { it.getCell("tableName") }
          .map { (tableName, tableLines) ->
            val columns =
              tableLines.map { row ->
                val name = row.getCell("columnName")
                val type = row.getCell("columnType")
                val affinity = SqliteAffinity.fromTypename(type)
                val isNullable = row.getCell("notnull") == "0"
                val isPrimaryKey = row.getCell("pk") != "0"
                SqliteColumn(name, affinity, isNullable, isPrimaryKey)
              }
            val rowIdName = getRowIdName(columns)
            val isView =
              tableLines.first().getCell("type").let {
                if (listOf("table", "view").contains(it)) it == "view"
                else throw IllegalArgumentException("Unexpected type: $it")
              }
            SqliteTable(tableName, columns, rowIdName, isView)
          }

      SqliteSchema(tables)
    }

  private data class RawCells(val header: List<String>, val dataRows: List<List<String>>)

  private fun SqliteCliResponse.toRawCells(): RawCells = let {
    val rowsRaw: List<String> = checkSuccess().stdOutput.split(System.lineSeparator())
    val rowsCells: Sequence<List<String>> = rowsRaw.asSequence().map { it.split(columnSeparator) }
    RawCells(header = rowsCells.first(), dataRows = rowsCells.drop(1).toList())
  }

  private fun SqliteStatement.checkNoSeparatorClash() = apply {
    if (this.sqliteStatementWithInlineParameters.contains(columnSeparator))
      throw IllegalArgumentException(
        "Potential clash between the data and a column separator." +
          " Consider using a different column separator or changing your test data."
      )
  }

  private fun SqliteCliResponse.checkSuccess() = apply {
    if (exitCode != 0) throw IllegalStateException("Issue while executing query. Error: $errOutput")
  }
}
