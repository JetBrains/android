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
package com.android.tools.idea.sqlite.controllers

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.sqlite.cli.SqliteQueries
import com.android.tools.idea.sqlite.model.Delimiter
import com.android.tools.idea.sqlite.model.ExportFormat.CSV
import com.android.tools.idea.sqlite.model.ExportRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportDatabaseRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportQueryResultsRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportTableRequest
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteValue
import com.android.tools.idea.sqlite.model.createSqliteStatement
import com.android.tools.idea.sqlite.model.isQueryStatement
import com.android.tools.idea.sqlite.repository.DatabaseRepository
import com.android.tools.idea.sqlite.ui.exportToFile.ExportToFileDialogView
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.Executor

@UiThread
class ExportToFileController(
  private val project: Project,
  private val view: ExportToFileDialogView,
  private val databaseRepository: DatabaseRepository,
  taskExecutor: Executor,
  edtExecutor: Executor,
  private val notifyExportComplete: (ExportRequest) -> Unit,
  private val notifyExportError: (ExportRequest, Throwable?) -> Unit
) : Disposable {
  private val edtDispatcher = edtExecutor.asCoroutineDispatcher()
  private val taskDispatcher = taskExecutor.asCoroutineDispatcher()
  private val listener = object : ExportToFileDialogView.Listener {
    override suspend fun exportRequestSubmitted(params: ExportRequest) = export(params)
  }

  fun setUp() {
    view.addListener(listener)
  }

  override fun dispose() {
    view.removeListener(listener)
  }

  private suspend fun export(params: ExportRequest) = withContext(edtDispatcher) {
    try {
      doExport(params)
      notifyExportComplete(params)
    }
    catch (t: Throwable) {
      // TODO(161081452): refine what we catch
      // TODO(161081452): add logging
      notifyExportError(params, t)
    }
  }

  private suspend fun doExport(params: ExportRequest): Unit = withContext(taskDispatcher) {
    when (params) {
      is ExportDatabaseRequest -> throw IllegalStateException("Not implemented") // // TODO(161081452)
      is ExportTableRequest -> {
        when (params.format) {
          is CSV -> {
            doExport(
              ExportQueryResultsRequest(
                params.srcDatabase,
                createSqliteStatement(SqliteQueries.selectTableContents(params.srcTable)),
                params.format,
                params.dstPath)
            )
          }
          else -> throwNotSupportedParams(params)
        }
      }
      is ExportQueryResultsRequest -> {
        if (!params.srcQuery.isQueryStatement) throwNotSupportedParams(params)

        when (params.format) {
          is CSV -> {
            // TODO(161081452): lock and download in chunks
            val rows = executeQuery(params.srcDatabase, params.srcQuery)
            writeRowsToCsvFile(
              rows,
              (params.format as CSV).delimiter,
              params.dstPath)
          }
          else -> throwNotSupportedParams(params)
        }
      }
    }
  }

  private suspend fun createSqliteStatement(statementText: String): SqliteStatement = withContext(edtDispatcher) {
    createSqliteStatement(project, statementText)
  }

  private suspend fun executeQuery(srcDatabase: SqliteDatabaseId, srcQuery: SqliteStatement): List<SqliteRow> =
    withContext(taskDispatcher) {
      val resultSet = databaseRepository.runQuery(srcDatabase, srcQuery).await()
      // TODO(161081452): convert to a streaming implementation not to crash / hang on huge tables
      resultSet.getRowBatch(0, Integer.MAX_VALUE).await()
    }

  // TODO(161081452): change to writing a chunk at a time
  // TODO(161081452): move out to an IO class
  @Suppress("BlockingMethodInNonBlockingContext") // the warning tries to make us use Dispatchers.IO
  private suspend fun writeRowsToCsvFile(rows: List<SqliteRow>, delimiter: Delimiter, dstPath: Path) = withContext(taskDispatcher) {
    val delimiterString = delimiter.delimiter.toString()

    dstPath.toFile().bufferedWriter().use { writer ->
      // header
      rows.firstOrNull()?.values?.map { it.columnName }?.let {
        writer.append(it.joinToString(delimiterString))
        writer.newLine()
      }
      // data
      rows.forEach { row ->
        writer.append(row.values.joinToString(delimiterString) { it.value.asString })
        writer.newLine()
      }
    }
  }

  private val SqliteValue.asString
    get() : String =
      when (this) {
        is SqliteValue.StringValue -> value
        is SqliteValue.NullValue -> ""
      }

  private fun throwNotSupportedParams(params: ExportRequest) {
    throw IllegalArgumentException("Unsupported export format: $params")
  }
}